package signalk.org.cloud_data_synch.server;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static signalk.org.cloud_data_synch.utils.ConfigConstants.ENABLE_SERIAL;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.GENERATE_NMEA0183;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.OUTPUT_NMEA;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.OUTPUT_TCP;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.REST_PORT;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.TCP_NMEA_PORT;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.TCP_PORT;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.UDP_NMEA_PORT;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.UDP_PORT;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.UUID;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.VERSION;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.WEBSOCKET_PORT;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.SIGNALK_DISCOVERY;
import static signalk.org.cloud_data_synch.utils.SignalKConstants._SIGNALK_HTTP_TCP_LOCAL;
import static signalk.org.cloud_data_synch.utils.SignalKConstants._SIGNALK_WS_TCP_LOCAL;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceInfo;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.PropertyConfigurator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.nettosphere.Nettosphere;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import minify.Minify;
import mjson.Json;
import nz.co.fortytwo.signalk.artemis.serial.SerialPortManager;
import signalk.org.cloud_data_synch.service.ChartService;
import signalk.org.cloud_data_synch.service.CloudSynchService;
import signalk.org.cloud_data_synch.service.InfluxDbService;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigUtils;
import signalk.org.cloud_data_synch.utils.ConfigUtils.SynchConfigObject;
import signalk.org.cloud_data_synch.utils.SecurityUtils;
import signalk.org.cloud_data_synch.utils.Util;




/**
 * ActiveMQ Artemis embedded with JMS
 */
public final class CloudSynchServer {

	private static Logger logger;
	public static EmbeddedActiveMQ embedded;
	private static Nettosphere server;
	private JmmDNS jmdns;

	private nz.co.fortytwo.signalk.artemis.serial.SerialPortManager serialPortManager;
	private signalk.org.cloud_data_synch.server.NettyServer skServer;
	private signalk.org.cloud_data_synch.server.NettyServer nmeaServer;
	private ClientSession session;
	private ClientConsumer consumer;
	
	private CloudSynchService synchServce;

	public CloudSynchServer() throws Exception {
		init(null);
	}
	
	public CloudSynchServer(String configFileName,  String dbName) throws Exception {
		InfluxDbService.setDbName(dbName);
		init(configFileName);
	}
	
	@SuppressWarnings("static-access")
	private void init(String configFileName) throws Exception{
		
		long startTm = (new Date()).getTime();
				
		Properties props = System.getProperties();
		props.setProperty("java.net.preferIPv4Stack", "true");
		props.setProperty("log4j.configurationFile", "./conf/log4j2.json");
		props.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level","TRACE");
		System.setProperties(props);
		logger = LogManager.getLogger(CloudSynchServer.class);
		
//		ensureSecurityConf();
		
		getServerConfig(configFileName);
		
		synchServce = new CloudSynchService();
		synchServce.init();
		
//		Config config = Config.getInstance();
//		config.getTDBService(Config.dbName, Config.dbType);
//		config.loadConfig((NavigableMap<String, Json>)config.getMap());

/**		

		embedded = new EmbeddedActiveMQ();
		embedded.start();
		
		//start incoming message consumer
		startIncomingConsumer();
		
		// start serial?
		if(Config.getConfigPropertyBoolean(ENABLE_SERIAL)){
			// start a serial port manager
			if (serialPortManager == null) {
				serialPortManager = new SerialPortManager();
				new Thread(serialPortManager).start();
			}
			
		}
		
		addShutdownHook(this);
		
		//swagger
		buildSwagger();
				
	
		server = new Nettosphere.Builder().config(
				new org.atmosphere.nettosphere.Config.Builder()
						.supportChunking(true)
						.maxChunkContentLength(1024*1024)
						.socketKeepAlive(true)
						.enablePong(false)
						.initParam(ApplicationConfig.PROPERTY_SESSION_SUPPORT, "true")
						.initParam(ApplicationConfig.ANALYTICS, "false")
						.initParam("jersey.config.server.provider.packages","nz.co.fortytwo.signalk.artemis,io.swagger.jaxrs.listing")
						.initParam("jersey.config.server.provider.packages","nz.co.fortytwo.signalk.artemis")
						.initParam("jersey.config.server.provider.classnames","org.glassfish.jersey.media.multipart.MultiPartFeature")
						.initParam("org.atmosphere.cpr.broadcaster.shareableThreadPool","true")
						.initParam("org.atmosphere.cpr.broadcaster.maxProcessingThreads", "10")
						.initParam("org.atmosphere.cpr.broadcaster.maxAsyncWriteThreads", "10")
						.initParam("org.atmosphere.websocket.maxIdleTime", "10000")
						.initParam("org.atmosphere.cpr.Broadcaster.writeTimeout", "30000")
						.initParam("org.atmosphere.cpr.broadcasterLifeCyclePolicy","EMPTY_DESTROY")
						.initParam("org.atmosphere.websocket.WebSocketProcessor","nz.co.fortytwo.signalk.artemis.server.SignalkWebSocketProcessor")
						//.resource("/signalk/*",AtmosphereServlet.class)
						//.resource("/*",FileSystemResourceServlet.class)
						//swagger
						//.initParam("openApi.configuration.resourcePackages", "nz.co.fortytwo.signalk.artemis.service")
						//.resource("/swagger/*",OpenApiServlet.class)
						//.resource(AuthenticationFilter.class)
						.port(8080)
						.host("0.0.0.0")
					.build()
				).build();
		
		server.start();
	
		skServer = new NettyServer(null, OUTPUT_TCP);
		skServer.setTcpPort(Config.getConfigPropertyInt(TCP_PORT));
		skServer.setUdpPort(Config.getConfigPropertyInt(UDP_PORT));
		skServer.run();

		if(Config.getConfigPropertyBoolean(GENERATE_NMEA0183)){
			nmeaServer = new NettyServer(null, OUTPUT_NMEA);
			nmeaServer.setTcpPort(Config.getConfigPropertyInt(TCP_NMEA_PORT));
			nmeaServer.setUdpPort(Config.getConfigPropertyInt(UDP_NMEA_PORT));
			nmeaServer.run();
		}

		startMdns();
		ChartService.reloadCharts();
*/
		
		startMe();
		long endTm = (new Date()).getTime();
		
		System.out.println("SignalK Cloud Synch server started in "+String.valueOf(endTm-startTm)+"ms.");

	}

	private void getServerConfig() throws Exception 
	{
		getServerConfig(null);
	}
	
	private void getServerConfig(String cFileName) throws Exception 
	{
		String configFileName = (cFileName != null) ? cFileName :  ConfigUtils.getConfigFileName();

		File synchConf = new File(configFileName);
		if(synchConf.exists()) {
			try(InputStream in = new FileInputStream(synchConf))
			{
				String defaultConfiguration = Minify.minify((CharSequence) IOUtils.toString(in));
				ConfigUtils.load(defaultConfiguration);
			}
			catch (Exception e) {
				logger.error(e,e);
				throw (e);
			}
		}
		else {
			throw new Exception("File "+"does not exists. The server will not start");
		}
	}


	private void ensureSecurityConf() {
		File secureConf = new File("./conf/security-conf.json");
		if(!secureConf.exists()) {
			try(InputStream in = getClass().getClassLoader().getResource("security-conf.json.default").openStream()){
				String defaultSecurity = IOUtils.toString(in);
				SecurityUtils.save(defaultSecurity);
			}catch (Exception e) {
				logger.error(e,e);
			}
			
		}else {
			//make sure we encrypt all passwords
			try {
				Json conf = SecurityUtils.getSecurityConfAsJson();
				SecurityUtils.save(conf.toString());
			} catch (IOException e) {
				logger.error(e,e);
			}
		}
	}

	private void startIncomingConsumers() throws Exception {
		
			session = Util.getVmSession(Config.getConfigProperty(Config.ADMIN_USER),Config.getConfigProperty(Config.ADMIN_PWD));
			consumer = session.createConsumer(Config.INCOMING_RAW);
			consumer.setMessageHandler(new MessageHandler() {
				
				@Override
				public void onMessage(ClientMessage message) {
					try {
						message.acknowledge();
					} catch (ActiveMQException e) {
						logger.error(e, e);
					}
					logger.debug("Acknowledge {}", message);
					
				}
			});
			session.start();
			
	}

	private static void addShutdownHook(final CloudSynchServer server) {
		Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
			@Override
			public void run() {
				server.stop();
			}
		});

	}

	public void stop() {
		if(consumer!=null) {
			try {
				consumer.close();
			} catch (ActiveMQException e) {
				logger.error(e, e);
			}
		}
		if(session!=null) {
			try {
				session.close();
			} catch (ActiveMQException e) {
				logger.error(e,e);
			}
		}
		if (skServer != null) {
			skServer.shutdownServer();
			skServer = null;
		}

		if (nmeaServer != null) {
			nmeaServer.shutdownServer();
			nmeaServer = null;
		}
		try {
			if (serialPortManager != null)
				serialPortManager.stopSerial();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		try {
			stopMdns();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		try {
			server.stop();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		try {
			embedded.stop();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

	public static ActiveMQServer getActiveMQServer() {
		return embedded.getActiveMQServer();
	}

	
	/**
	 * Stop the DNS-SD server.
	 * 
	 * @throws IOException
	 */
	public void stopMdns() throws IOException {
		if (jmdns != null) {
			jmdns.unregisterAllServices();
			jmdns.close();
			jmdns = null;
		}
	}
	
	public NavigableMap<String, Json> extractData(NavigableMap<String, Json> dataMap) throws Exception
	{
		if (dataMap == null)
			dataMap = new ConcurrentSkipListMap<>();
		
		try {
			synchServce.extractData(dataMap);
		}
		catch (Exception e) {
			logger.error("Data extract Error : {}", e.getLocalizedMessage());
			throw e;
		}
		return dataMap;
	}

	private void startMe() {
		// DNS-SD
		// NetworkTopologyDiscovery netTop =
		// NetworkTopologyDiscovery.Factory.getInstance();

		Runnable r = new Runnable() {
			private volatile boolean stop = false;

			int wifi = Integer.parseInt(ConfigUtils.getWifi());
			int mifi = Integer.parseInt(ConfigUtils.getMifi());
			int satellite = Integer.parseInt(ConfigUtils.getSattelite());

			boolean useWiFi = false;
			boolean useMiFi = false;
			boolean useSattelite = false;

			Map<Integer, SynchConfigObject> _map = ConfigUtils.getTransports();

			NavigableMap<String, Json> dataMap = new ConcurrentSkipListMap<>();

			@Override
			public void run() {
				while (!stop) {
					// ** Map<Integer, SynchConfigObject> _map = new TreeMap<Integer, SynchConfigObject>(ConfigUtils.getTransports());
					/**
					 * jmdns = JmmDNS.Factory.getInstance();
					 * 
					 * jmdns.registerServiceType(_SIGNALK_WS_TCP_LOCAL);
					 * jmdns.registerServiceType(_SIGNALK_HTTP_TCP_LOCAL);
					 * ServiceInfo wsInfo =
					 * ServiceInfo.create(_SIGNALK_WS_TCP_LOCAL, "signalk-ws",
					 * Config.getConfigPropertyInt(WEBSOCKET_PORT), 0, 0,
					 * getMdnsTxt());
					 */
					/// *** dataMap.clear();
					/**
					 * Get database extracts here
					 */
					try {
						extractData(dataMap);
					}
					catch (Exception e) {
						logger.error("Data extract Error : {}", e.getLocalizedMessage());
						stop = true;
					}
					if (!stop) {
						/**
						 * Find suitable transport here
						 */

						for (int priority = 0; priority < _map.size(); priority++) {
							SynchConfigObject _synchTransport = _map.get(priority);
							String useTransport = _synchTransport.getTransport();

							int pingms = synchServce.ping(_synchTransport);
							/**
							 * Find suitable transport here
							 */
							if (pingms <= wifi)
								useWiFi = true;
							else if (pingms <= mifi)
								useMiFi = true;
							else
								useSattelite = true;

							switch (useTransport) {
							case ConfigUtils.WIFI:
								if (useWiFi) {
									logger.info("will use WiFi for {} transport", _synchTransport.getName());
									synchServce.sendData(_synchTransport);
								}
								break;
							case ConfigUtils.MIFI:
								if (useMiFi) {
									logger.info("will use MiFi for {} transport", _synchTransport.getName());
									synchServce.sendData(_synchTransport);
								}
								break;
							case ConfigUtils.SATTELITE:
								logger.info("will use Sattelite for {} transport", _synchTransport.getName());
								synchServce.sendData(_synchTransport);
								break;
							default:
								synchServce.sendData(_synchTransport);
								break;
							}

						}

						dataMap.clear();

						try {
							// jmdns.registerService(wsInfo);
							ServiceInfo httpInfo = ServiceInfo.create(_SIGNALK_HTTP_TCP_LOCAL, "signalk-http",
							        Config.getConfigPropertyInt(REST_PORT), 0, 0, getMdnsTxt());
							jmdns.registerService(httpInfo);
						}
						catch (IOException e) {
							e.printStackTrace();
						}
						useWiFi = false;
						useMiFi = false;
						useSattelite = false;
					}
				}
			}
		};
		Thread t = new Thread(r);
		t.setDaemon(true);
		t.start();

	}

	private void startMdns() {
		// DNS-SD
		// NetworkTopologyDiscovery netTop =
		// NetworkTopologyDiscovery.Factory.getInstance();
		Runnable r = new Runnable() {

			@Override
			public void run() {
				jmdns = JmmDNS.Factory.getInstance();

				jmdns.registerServiceType(_SIGNALK_WS_TCP_LOCAL);
				jmdns.registerServiceType(_SIGNALK_HTTP_TCP_LOCAL);
				ServiceInfo wsInfo = ServiceInfo.create(_SIGNALK_WS_TCP_LOCAL, "signalk-ws",
						Config.getConfigPropertyInt(WEBSOCKET_PORT), 0, 0, getMdnsTxt());
				try {
					jmdns.registerService(wsInfo);
					ServiceInfo httpInfo = ServiceInfo.create(_SIGNALK_HTTP_TCP_LOCAL, "signalk-http",
							Config.getConfigPropertyInt(REST_PORT), 0, 0, getMdnsTxt());
					jmdns.registerService(httpInfo);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		Thread t = new Thread(r);
		t.setDaemon(true);
		t.start();

	}

	private Map<String, String> getMdnsTxt() {
		Map<String, String> txtSet = new HashMap<String, String>();
		txtSet.put("path", SIGNALK_DISCOVERY);
		txtSet.put("server", "signalk-server");
		txtSet.put("version", Config.getConfigProperty(VERSION));
		txtSet.put("vessel_name", Config.getConfigProperty(UUID));
		txtSet.put("vessel_mmsi", Config.getConfigProperty(UUID));
		txtSet.put("vessel_uuid", Config.getConfigProperty(UUID));
		return txtSet;
	}
	
	private static void buildSwagger()
    {
        // This configures Swagger
//        BeanConfig beanConfig = new BeanConfig();
//        beanConfig.setVersion( "1.0.0" );
//        beanConfig.setResourcePackage("nz.co.fortytwo.signalk.artemis.service");
//        
//        beanConfig.setScan( true );
//        beanConfig.setBasePath( "/" );
//        beanConfig.setDescription( "Manages webapp lifecycle in a signalk web server" );
//        beanConfig.setTitle( "Signalk Webapp Management API" );
        
		
    }
	
	static {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("log4j.configurationFile", "./conf/log4j2.json");
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level","TRACE");
		
    }
	
	public static void main(String[] args) throws Exception {
//		Properties props = System.getProperties();
//		props.setProperty("java.net.preferIPv4Stack", "true");
//		//props.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
//		props.setProperty("log4j.configurationFile", "./conf/log4j2.json");
//		props.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level","TRACE");
//		System.setProperties(props);
		
		PropertyConfigurator.configure("./conf/log4j2.json");
		InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
		
		File file = new File("./conf/log4j2.json");
		if(!file.exists()){
			FileUtils.copyFile(new File("./conf/log4j2.json.sample"),file);
		}
		// this will force a reconfiguration
		context.setConfigLocation(file.toURI());		

		if (args.length > 1)
			new CloudSynchServer(args[1], null);
		else 
			new CloudSynchServer(null, null);

	}
}
