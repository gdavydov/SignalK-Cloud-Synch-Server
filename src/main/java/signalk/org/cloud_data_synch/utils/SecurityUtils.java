package signalk.org.cloud_data_synch.utils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

import javax.crypto.SecretKey;
import javax.servlet.http.Cookie;
import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResource;
import org.joda.time.DateTime;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import mjson.Json;
import signalk.org.cloud_data_synch.utils.PasswordStorage.CannotPerformOperationException;

public final class SecurityUtils {
	
	private static Logger logger = LogManager.getLogger(SecurityUtils.class);

	private static java.nio.file.Path target = Paths.get("./conf/security-conf.json");
	private static final String PASSWORD = "password";
	private static final String LAST_CHANGED = "lastPasswordChange";
	public static final String USERS = "users";
	public static final String ROLES = "roles";
	public static final String REALM = "signalk";
	public static final String AUTHENTICATION_SCHEME = "Bearer";
	public static final String AUTH_COOKIE_NAME = "SK_TOKEN";
	
	private static final String HASH = "hash";
	private static SecretKey key = MacProvider.generateKey();

	private static Json securityConf;
	
	
	
	public static void setForbidden(AtmosphereResource r) {
		r.getResponse().setHeader(HttpHeaders.AUTHORIZATION, "Basic realm=\""+REALM+"\"");
		r.getResponse().setStatus(HttpStatus.SC_FORBIDDEN, "Forbidden");
	}

	public static void setUnauthorised(AtmosphereResource r) {
		r.getResponse().setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\""+REALM+"\"");
		r.getResponse().setStatus(HttpStatus.SC_UNAUTHORIZED);
	}

	public static boolean isTokenBasedAuthentication(String authorizationHeader) {

		// Check if the Authorization header is valid
		// It must not be null and must be prefixed with "Bearer" plus a whitespace
		// The authentication scheme comparison must be case-insensitive
		return authorizationHeader != null
				&& authorizationHeader.toLowerCase().startsWith(AUTHENTICATION_SCHEME.toLowerCase() + " ");
	}

	public static String validateToken(String token) throws Exception {
		// Check if the token was issued by the server and if it's not expired
		// Throw an Exception if the token is invalid
		Claims body = Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
		
		//renew if near expiry
		if((System.currentTimeMillis()-body.getExpiration().getTime())< (body.getExpiration().getTime()*0.1)) {
			return issueToken(body.getSubject(), Json.read(body.get(ROLES,String.class)));
		}
		return token;
	}
	
	public static String getSubject(String token) throws Exception {
		// Check if the token was issued by the server and if it's not expired
		return Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody().getSubject();
		
	}
	
	public static Json getRoles(String token) throws Exception {
		return Json.read(Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody().get(ROLES, String.class));
	}
	

	public static String authenticateUser(String username, String password) throws Exception {
		//load user json
		Json conf = getSecurityConfAsJson();
		Json users=conf.at(USERS);
		logger.debug("Users: {}",users);
		for(Json user : users.asJsonList()) {
			logger.debug("Checking: {}",user);
			if(username.equals(user.at("name").asString()) 
					&& PasswordStorage.verifyPassword(password, user.at(HASH).asString())){
				Json roles = user.at(ROLES);
				return issueToken(username, roles);
			}
		}
		throw new SecurityException("Username or password invalid");
	}

	public static String issueToken(String username, Json roles) {
		// Issue a token (can be a random String persisted to a database or a JWT token)
		// The issued token must be associated to a user
		Claims claims = Jwts.claims();
		claims.put(ROLES, roles.toString());
		
		String compactJws = Jwts.builder()
				.setSubject(username)
				.setClaims(claims)
				.setIssuedAt(DateTime.now().toDate())
				.setExpiration(DateTime.now().plusHours(1).toDate())
				.signWith(SignatureAlgorithm.HS512, key)
				.compact();
		// Return the issued token
		logger.debug("Issue token: {}",compactJws);
		return compactJws;
	}

	public static void save(String body) throws IOException {
		Json conf = Json.read(body);
		for( Json user : conf.at(USERS).asJsonList()) {
			//hash any new passwords
			String pass = user.at(PASSWORD).asString();
			if(StringUtils.isNotBlank(pass)) {
				try {
					user.set(HASH,PasswordStorage.createHash(pass));
					user.set(PASSWORD, "");
					user.set(LAST_CHANGED, Util.getIsoTimeString());
				} catch (CannotPerformOperationException e) {
					logger.error(e,e);
				}
			}
		}
		FileUtils.writeStringToFile(target.toFile(), conf.toString());
		securityConf=null;
	}
	public static byte[] getSecurityConfAsBytes() throws IOException {
		return FileUtils.readFileToByteArray(target.toFile());
	}
	
	public static Json getSecurityConfAsJson() throws IOException {
		if(securityConf==null) {
			securityConf=Json.read(FileUtils.readFileToString(target.toFile()));
		}
		return securityConf;
	}



	public static Cookie updateCookie(Cookie c, String token) {
		if(c==null)
			c = new Cookie(AUTH_COOKIE_NAME, token);
		else
			c.setValue(token);
		c.setMaxAge(3600);
		c.setHttpOnly(false);
		c.setPath("/");
		return c;
	}
	
	

	public static ArrayList<String> getDeniedReadPaths(String jwtToken) throws Exception {
		Json roles = StringUtils.isBlank(jwtToken)?Json.read("[\"public\"]"):getRoles(jwtToken);
		ArrayList<String> denied = new ArrayList<>();
		for(Json r : roles.asJsonList()) {
			for(Json d : getSecurityConfAsJson().at(ROLES).at(r.asString()).at("denied")) {
				if(d.at("read").asBoolean()) {
					denied.add(d.at("name").asString());
				}
			}
		}
		
		return denied;
		
	}
	
	public static ArrayList<String> getAllowedReadPaths(String jwtToken) throws Exception {
		
		Json roles = StringUtils.isBlank(jwtToken)?Json.read("[\"public\"]"):getRoles(jwtToken);
		ArrayList<String> allowed = new ArrayList<>();
		for(Json r : roles.asJsonList()) {
			for(Json d : getSecurityConfAsJson().at(ROLES).at(r.asString()).at("allowed")) {
				if(d.at("read").asBoolean()) {
					allowed.add(d.at("name").asString());
				}
			}
		}
		
		return allowed;
		
	}

}
