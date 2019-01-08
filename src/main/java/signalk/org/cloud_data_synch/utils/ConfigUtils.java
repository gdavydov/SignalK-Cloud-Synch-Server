package signalk.org.cloud_data_synch.utils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import signalk.org.cloud_data_synch.service.SignalKCloudSynchService;
import signalk.org.cloud_data_synch.service.SignalKCloudSynchServiceFactory;
import signalk.org.cloud_data_synch.service.TDBService;
import signalk.org.cloud_data_synch.utils.ConfigUtils.SynchConfigObject;
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
	public static final String COMPRESSED_EXT  = ".zip";
	
	public static final String USE_ATTACHMENTS = "use_attachments";
	public static final String CLASS_NAME = "class_name";
	public static final String EXEC_CLASS = "exec_class";
	public static final String DATA_SOURCE = "data_source";
	public static final String DATABASE_NAME = "dbname";
	public static final String KEY_FILE = "keyfile";
	
	public static final String FREQUENCY = "frequency";
	public static final String PRIORITY  = "priority";
	
	
	public static final String PRODUCERS  = "producers";
	public static final String CONSUMERS  = "consumers";
	public static final String PING_SPEED   = "ping_speed";
	public static final String WIFI               = "WIFI";
	public static final String MIFI                = "MIFI";
	public static final String SATTELITE          = "SATTELITE";
	public static final String EXTRACT_DATA       = "extract_data";
	public static final String TIME_RESOLUTION    = "time_resolution";
	public static final String AGGREGATE_FUNCTION = "aggregate_function";
	public static final String DATA_READ_TIMEOUT = "data_read_timeout";
	
	public static final String DATA_FOLDERS = "data_folders";
	public static final String CONSUMER_DATA_FOLDER = "consumer_data_folder";
	public static final String PRODUCER_DATA_FOLDER = "producer_data_folder";

	public static final String SERVER_TYPE = "server_type";
	public static final String CONSUMER = "consumer";
	public static final String PRODUCER = "producer";
	public static final String DEFAULT_DATA_TIME_RESOLUTION  = "5s";
	public static final String DEFAULT_AGGREGATE_FUNCTION = "max";
	public static final int DEFAULT_WIFI_RESPONCE_TIME = 150;
	public static final int DEFAULT_MIFI_RESPONCE_TIME = 200;
	public static final int DEFAULT_SAT_RESPONCE_TIME = 10000;	
	public static final long DEFAULT_READ_FREQUNCY = 120000;
	public static final long DEFAULT_PING_INTERVAL = 10000;
	public static final long MAX_PING_INTERVAL = 180000;
	public static final long DEFAULT_DATA_EXTRACT_INTERVAL = 20000;
	public static final long DEFAULT_FILE_WATCHING_PERIOD = 10000;
	public static final String DEFAULT_DATA_LOCATION = "./data_dumps";
	public static final String DEFAULT_DATA_FILE_EXTENTION = "dd";

	public static final long GLOBAL_ERROR = -99;

	
    private static ConfigUtils instance = new ConfigUtils();
    
    private Map<String, Json> producers;
    private Map<String, Json> consumers;
	
	private static final String HASH = "hash";
	private static SecretKey key = MacProvider.generateKey();

	private static Json conf;

	private String pingHost;
	private String pingTimeout;
	private int wifi = DEFAULT_WIFI_RESPONCE_TIME;
	private int mifi = DEFAULT_MIFI_RESPONCE_TIME;
	private int sattelite = DEFAULT_SAT_RESPONCE_TIME;
	private String time_resolution = DEFAULT_DATA_TIME_RESOLUTION;
	private String aggregate_function = DEFAULT_AGGREGATE_FUNCTION; 
	private String dataReadTimeout = DATA_READ_TIMEOUT;
	private String dataSource;
	private String serverType;
	private String consumer_data_folder;
	private String producer_data_folder;

	private  Map<Integer, SynchConfigObject> transports = new TreeMap<Integer, SynchConfigObject>();

	public class SynchConfigObject 
	{
		private String name;     
		private String frequency;
		private String priority;
		private String useTransport;
		private int transportSpeed;
		private Map<String, Object> configPropertiesProducer;
		private Map<String, Object> configPropertiesConsumer;
		private Class<SignalKCloudSynchService> consumerClass;
		private Class<SignalKCloudSynchService> producerClass;
		private Object producerInstance;
		private Object consumerInstance;
		private Method produceMethod;
		private Method consumeMethod;
		
		long lastCommunication;
		
		public SynchConfigObject (String name, Json conf) throws Exception
		{
			this.name=name;
			frequency=conf.at(TRANSPORT).asJsonMap().get(name).asJsonMap().get(FREQUENCY).asString();
			priority =conf.at(TRANSPORT).asJsonMap().get(name).asJsonMap().get(PRIORITY).asString();
			useTransport=conf.at(TRANSPORT).asJsonMap().get(name).asJsonMap().get(USE_TRANSPORT).asString().toUpperCase();
			
			if (useTransport.equals(ConfigUtils.WIFI) ) {
				transportSpeed = DEFAULT_WIFI_RESPONCE_TIME;
			}
			else if (useTransport.equals(ConfigUtils.MIFI) ) {
				transportSpeed = DEFAULT_MIFI_RESPONCE_TIME;
			}
			else
				transportSpeed = DEFAULT_SAT_RESPONCE_TIME;
				
			if (instance.serverType.equals(PRODUCER)) {
				configPropertiesProducer= conf.asJsonMap().get(PRODUCERS).asJsonMap().get(name) != null ? conf.asJsonMap().get(PRODUCERS).asJsonMap().get(name).asMap() : null;
				try {
					producerClass = SignalKCloudSynchServiceFactory.getProducer(getProducerValue(CLASS_NAME));
					produceMethod = producerClass.getMethod("produce", Object.class);
					producerInstance = producerClass.newInstance();					
				}
				catch (Throwable t) {
					logger.error("SignalKCloudSynchServiceFactory - Error : {}. Producer.Class={}", 
							t.getLocalizedMessage(), 
							getProducerValue(CLASS_NAME)); 
					t.printStackTrace();
				}
			}
			else if (instance.serverType.equals(CONSUMER)) {
				configPropertiesProducer= conf.asJsonMap().get(CONSUMERS).asJsonMap().get(name) != null ? conf.asJsonMap().get(PRODUCERS).asJsonMap().get(name).asMap() : null;
				try {
					consumerClass = SignalKCloudSynchServiceFactory.getConsumer(getConsumerValue(CLASS_NAME));
					consumeMethod = consumerClass.getMethod("consume", Object.class);
					consumerInstance = producerClass.newInstance();
				}
				catch (Throwable t) {
					logger.error("SignalKCloudSynchServiceFactory - Error : {}. Consumer.Class=() ", 
							t.getLocalizedMessage(), 
							getConsumerValue(CLASS_NAME));
					t.printStackTrace();
				}
			}
			else {
				throw new Exception ("Missing or unknown server type: "+instance.serverType);
			}

			

			
//			configPropertiesConsumer= conf.at(name).asJsonMap().get(CONSUMER) != null ? conf.at(name).asJsonMap().get(CONSUMER).asMap() : null;
			configPropertiesProducer= conf.asJsonMap().get(PRODUCERS).asJsonMap().get(name) != null ? conf.asJsonMap().get(PRODUCERS).asJsonMap().get(name).asMap() : null;
						
			try {
				producerClass = SignalKCloudSynchServiceFactory.getProducer(getProducerValue(CLASS_NAME));
				produceMethod = producerClass.getMethod("produce", Object.class);
				producerInstance = producerClass.newInstance();
			}
			catch (Throwable t) {
				logger.error("SignalKCloudSynchServiceFactory - Error : {}. Producer.Class={}, Consumer.Class=() ", 
						t.getLocalizedMessage(), 
						getProducerValue(CLASS_NAME), 
						getConsumerValue(CLASS_NAME));
				t.printStackTrace();
			}

			try {
				if (producerInstance != null) {
					Method producerSetUp = producerClass.getMethod("setUpProducer", Map.class);
					producerSetUp.invoke(producerInstance, configPropertiesProducer);
				}
				
				if (consumerInstance != null) {
					Method consumerSetUp = consumerClass.getMethod("setUpConsumer", Map.class);
					consumerSetUp.invoke(consumerInstance, configPropertiesProducer);
				}
			}
			catch (Throwable t) {
				logger.error("SignalKCloudSynchServiceFactory - Error : {}", t.getLocalizedMessage());
				t.printStackTrace();
			}
		}
		
		public String getConsumerValue(String param)
		{
			return (configPropertiesConsumer != null) ? String.valueOf(configPropertiesConsumer.get(param)) : null;
		}
		
		public String getProducerValue(String param)
		{
			return (configPropertiesProducer != null) ? String.valueOf(configPropertiesProducer.get(param)) : null;
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
		public int getTransportSpeed() {
			return transportSpeed;
		}
		
		public Object consumerInstance() {
			return consumerInstance;
		}

		public Object producerInstance() {
			return producerInstance;
		}

		public Method produceMethod() {
			return produceMethod;
		}

		public Method consumeMethod() {
			return consumeMethod;
		}

		public Long invokeProduceMethod(Object obj) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			
			return (Long)produceMethod.invoke(producerInstance(), obj);
		}

		public void invokeConsumeMethod() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			
			consumeMethod.invoke(consumerInstance());
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

		instance.pingHost           = conf.asJsonMap().get(PING_SPEED).asJsonMap().get(HOST).asString();
		instance.pingTimeout           = conf.asJsonMap().get(PING_SPEED).asJsonMap().get(TIMEOUT).asString();
		instance.wifi               = Integer.parseInt(conf.asJsonMap().get(PING_SPEED).asJsonMap().get(WIFI).asString());
		instance.mifi               = Integer.parseInt(conf.asJsonMap().get(PING_SPEED).asJsonMap().get(MIFI).asString());
		instance.sattelite          = Integer.parseInt(conf.asJsonMap().get(PING_SPEED).asJsonMap().get(SATTELITE).asString());
		instance.time_resolution    = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(TIME_RESOLUTION).asString();
		instance.aggregate_function = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(AGGREGATE_FUNCTION).asString();
		instance.dataSource         = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(DATA_SOURCE).asString();
		instance.dataReadTimeout    = conf.asJsonMap().get(EXTRACT_DATA).asJsonMap().get(DATA_READ_TIMEOUT).asString();
		instance.consumer_data_folder = conf.asJsonMap().get(DATA_FOLDERS).asJsonMap().get(CONSUMER_DATA_FOLDER).asString();
		instance.producer_data_folder = conf.asJsonMap().get(DATA_FOLDERS).asJsonMap().get(PRODUCER_DATA_FOLDER).asString();
		
		instance.serverType         = conf.asJsonMap().get(SERVER_TYPE).asString();
		if ( instance.serverType == null || ( !instance.serverType.equals(PRODUCER) && !instance.serverType.equals(CONSUMER))) {
			throw new Exception ("Missing or unknown server type: "+instance.serverType);
		}
		
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

	public static String getPingHost()
	{
		return instance.pingHost;
	}

	public static String getPingTimeout()
	{
		return instance.pingTimeout;
	}

	public static int getWifi() {
		return instance.wifi;
	}

	public static int getMifi() {
		return instance.mifi;
	}

	public static int getSattelite() {
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
	
	public static String getServerType()
	{
		return instance.serverType;
	}

	public static String getConsumerDataFolder()
	{
		return instance.consumer_data_folder;
	}

	public static String getProducerDataFolder()
	{
		return instance.producer_data_folder;
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
