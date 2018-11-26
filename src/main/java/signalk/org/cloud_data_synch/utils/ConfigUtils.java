package signalk.org.cloud_data_synch.utils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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
import signalk.org.cloud_data_synch.service.TDBService;
import signalk.org.cloud_data_synch.utils.PasswordStorage.CannotPerformOperationException;

public final class ConfigUtils {
	
	private static Logger logger = LogManager.getLogger(ConfigUtils.class);
	private static String configFileName = "./conf/cloudsynch-conf.json";

	private static java.nio.file.Path target = Paths.get("./conf/security-conf.json");
//	private static final String PASSWORD = "password";
	private static final String LAST_CHANGED = "lastPasswordChange";
	public static final String USERS = "users";
	public static final String ROLES = "roles";
	public static final String REALM = "signalk";
	public static final String AUTHENTICATION_SCHEME = "Bearer";
	public static final String AUTH_COOKIE_NAME = "SK_TOKEN";
	
	public static final String USE_TRANSPORT = "use_transport";
	public static final String TRANSPORT = "transport";
	public static final String MQTT      = "mqtt";
	public static final String SCP       = "scp";
	public static final String SFTP      = "sftp";
	public static final String FTP       = "ftp";
	public static final String SMPT      = "smpt";
	public static final String HOST      = "host";
	public static final String PORT      = "port";
	public static final String USER      = "user";
	public static final String PASSWORD  = "password";
	public static final String TIMEOUT   = "timeout";
	public static final String COMPRESS  = "compress_data";
	public static final String USE_ATTACHMENTS = "use_attachments";
	public static final String CLASS_NAME = "class_name";
	public static final String EXEC_CLASS = "exec_class";
	public static final String DATA_SOURCE = "data_source";
	
	public static final String FREQUENCY = "frequency";
	public static final String PRIORITY  = "priority";
	
	public static final String CONNECTION_SPEED   = "connection_speed";
	public static final String WIFI               = "WIFI";
	public static final String MIFI                = "MIFI";
	public static final String SATTELITE          = "SATTELITE";
	public static final String EXTRACT_DATA       = "extract_data";
	public static final String TIME_RESOLUTION    = "time_resolution";
	public static final String AGGREGATE_FUNCTION = "aggregate_function";
	public static final String DATA_READ_TIMEOUT = "data_read_timeout";
	
	
	public static final String SERVER_TYPE = "server_type";
	public static final String CONSUMER = "consumer";
	public static final String PRODUCER = "producer";
	public static final String DEFAULT_DATA_TIME_RESOLUTION  = "5s";
	public static final String DEFAULT_AGGREGATE_FUNCTION = "max";
	public static final String DEFAULT_WIFI_RESPONCE_TIME = "150";
	public static final String DEFAULT_MIFI_RESPONCE_TIME = "200";
	public static final String DEFAULT_SAT_RESPONCE_TIME = "10000";
	
    private static ConfigUtils instance = new ConfigUtils();
	
	private static final String HASH = "hash";
	private static SecretKey key = MacProvider.generateKey();

	private static Json conf;

	private String wifi = DEFAULT_WIFI_RESPONCE_TIME;
	private String mifi = DEFAULT_MIFI_RESPONCE_TIME;
	private String sattelite = DEFAULT_SAT_RESPONCE_TIME;
	private String time_resolution = DEFAULT_DATA_TIME_RESOLUTION;
	private String aggregate_function = DEFAULT_AGGREGATE_FUNCTION; 
	private String dataReadTimeout = DATA_READ_TIMEOUT;
	private String dataSource;
	private String serverType;

	private  Map<Integer, SynchConfigObject> transports = new TreeMap<Integer, SynchConfigObject>();

	public class SynchConfigObject 
	{
		String name;     
		String frequency;
		String priority;
		String useTransport;
		Map<String, Object> configPropertiesProducer;
		Map<String, Object> configPropertiesConsumer;
		Class<?> consumerExecInstance;
		Class<?> producerExecInstance;

		long lastCommunication;
		
		public SynchConfigObject (String name, Json conf) throws Exception
		{
			this.name=name;
			frequency=conf.at(TRANSPORT).asJsonMap().get(name).asJsonMap().get(FREQUENCY).asString();
			priority =conf.at(TRANSPORT).asJsonMap().get(name).asJsonMap().get(PRIORITY).asString();
			useTransport=conf.at(TRANSPORT).asJsonMap().get(name).asJsonMap().get(USE_TRANSPORT).asString().toUpperCase();
			
			configPropertiesConsumer= conf.at(name).asJsonMap().get(CONSUMER).asMap();
			configPropertiesProducer= conf.at(name).asJsonMap().get(PRODUCER).asMap();
			
			String producerClassName=getProducerValue(CLASS_NAME);
			if (producerClassName != null) {
				try {
					if (logger.isDebugEnabled())
						logger.debug("Will instanciate producer {}", producerClassName );
					Class<?> execInstance = Class.forName(producerClassName);
	/*
					Method setUpMethod = instance.getMethod("setUpTDb", String.class);
					service = (TDBService) setUpMethod.invoke(null, dbName);
					dbService.put(dbType + "-" + dbName, service);
					return service;
	*/
					producerExecInstance=execInstance;
				}
				catch (Throwable t) {
					throw new Exception(t);
				}
			}

			String consumerrClassName=getProducerValue(CLASS_NAME);
			if (producerClassName != null) {
				try {
					if (logger.isDebugEnabled())
						logger.debug("Will instanciate consumer {}", producerClassName );
					Class<?> execInstance = Class.forName(producerClassName);
	/*
					Method setUpMethod = instance.getMethod("setUpTDb", String.class);
					service = (TDBService) setUpMethod.invoke(null, dbName);
					dbService.put(dbType + "-" + dbName, service);
					return service;
	*/
					consumerExecInstance=execInstance;
				}
				catch (Throwable t) {
					throw new Exception(t);
				}
			}
		}
		
		public String getConsumerValue(String param)
		{
			return String.valueOf(configPropertiesConsumer.get(param));
		}
		
		public String getProducerValue(String param)
		{
			return String.valueOf(configPropertiesProducer.get(param));
		}
		
		public String getName() {
			return name;
		}

		public String getFrequency() {
			return frequency;
		}

		public String getTransport() {
			return useTransport;
		}

		public long getLastCommunication() {
			return lastCommunication;
		}

		public void setLastCommunication(long lastCommunication) {
			this.lastCommunication = lastCommunication;
		}
	}

	public static String getConfigFileName() {
		return configFileName;
	}

	public static void setConfigFileName(String configFileName) {
		ConfigUtils.configFileName = configFileName;
	}

	public static void load(String body) throws Exception
	{
		Json conf = Json.read(body);

		instance.wifi               = conf.asJsonMap().get(CONNECTION_SPEED).asJsonMap().get(WIFI).asString();
		instance.mifi               = conf.asJsonMap().get(CONNECTION_SPEED).asJsonMap().get(MIFI).asString();
		instance.sattelite          = conf.asJsonMap().get(CONNECTION_SPEED).asJsonMap().get(SATTELITE).asString();
		instance.time_resolution    = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(TIME_RESOLUTION).asString();
		instance.aggregate_function = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(AGGREGATE_FUNCTION).asString();
		instance.dataSource         = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(DATA_SOURCE).asString();
		instance.dataReadTimeout    = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(DATA_READ_TIMEOUT).asString();
		
		instance.serverType         = conf.asJsonMap().get(SERVER_TYPE).asString();
		
		conf.at(TRANSPORT).asJsonMap().forEach((k,v) -> {
			Json transport = v;
			String transportName = k;
			String priority =transport.asJsonMap().get(PRIORITY).asString();
			try {
				instance.transports.put(Integer.valueOf(priority), instance.new SynchConfigObject(transportName, conf));
			}
			catch (Exception e) {
				try {
					throw e;
				}
				catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
	}
	
	public static String getStringValue(Json _val)
	{
		return _val != null ? _val.asString() : null;
	}


	public static Map<Integer, SynchConfigObject> getTransports() {
		return instance.transports;
	}

	public static SynchConfigObject createNewConfigobject(String name, Json _c) throws Exception
	{
		return instance.new SynchConfigObject(name, _c);
	}
	
	public static Logger getLogger() {
		return logger;
	}

	public static java.nio.file.Path getTarget() {
		return target;
	}

	public static SecretKey getKey() {
		return key;
	}

	public static Json getConf() {
		return conf;
	}

	public static String getWifi() {
		return instance.wifi;
	}

	public static String getMifi() {
		return instance.mifi;
	}

	public static String getSattelite() {
		return instance.sattelite;
	}

	public static String getTime_resolution() {
		return instance.time_resolution;
	}

	public static String getDataSource() {
		return instance.dataSource;
	}

	public static String getAggregate_function() {
		return instance.aggregate_function;
	}
	
	public static String getDataReadTimeout() {
		return instance.dataReadTimeout;
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

	public static byte[] getSecurityConfAsBytes() throws IOException {
		return FileUtils.readFileToByteArray(target.toFile());
	}
	
	public static Json getSecurityConfAsJson() throws IOException {
		if(conf==null) {
			conf=Json.read(FileUtils.readFileToString(target.toFile()));
		}
		return conf;
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
