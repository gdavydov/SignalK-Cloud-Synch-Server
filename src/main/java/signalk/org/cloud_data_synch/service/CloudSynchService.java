package signalk.org.cloud_data_synch.service;

import static signalk.org.cloud_data_synch.utils.ConfigConstants.MAP_DIR;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.STATIC_DIR;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.AGGREGATE_FUNCTION;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_DATA_LOCATION;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_FILE_WATCHING_PERIOD;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_DATA_EXTRACT_INTERVAL;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_PING_INTERVAL;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_READ_FREQUNCY;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.FROMTIME;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.RESOLUTION;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.TOTIME;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.ws.rs.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigUtils;
import signalk.org.cloud_data_synch.utils.ConfigUtils.SynchConfigObject;
import signalk.org.cloud_data_synch.utils.HttpUtils;
import signalk.org.cloud_data_synch.utils.ZipUtils;
import signalk.org.cloud_data_synch.utils.CircularQueue;

@Path("/signalk/v1/Synch")
// @Api( value="Signalk Chart Management API")
public class CloudSynchService
{

	public static int wifi = ConfigUtils.getWifi();
	public static int mifi = ConfigUtils.getMifi();
	public static int sattelite = ConfigUtils.getSattelite();

	private static Logger logger = LogManager.getLogger(CloudSynchService.class);
	// private static boolean reloaded = false;
	protected static TDBService tdbService = null;
	private Long lastExtractDt = null;
	private boolean inProcess = false;

	/**
	 * Serialization stuff
	 */
	private static String filename = "./fromtime.ser";
	private FileOutputStream fos = null;
	private ObjectOutputStream out = null;
	private FileInputStream fis = null;
	private ObjectInputStream in = null;
	private long readFrequency = DEFAULT_READ_FREQUNCY;
	private long pingms = 10; 
	
	private static CircularQueue<String> dataFilesQueue = new CircularQueue<String>(1000);
	
	private Map<String, Long>producerCronTable = new HashMap<String, Long>();
	
	public CloudSynchService() throws Exception {
	}

	public void init()
	{
		try {
			tdbService = (tdbService == null)
			        ? TDBServiceFactory.getService(Config.dbName, Config.dbType, Long.valueOf(ConfigUtils.getDataReadTimeout())) : tdbService;
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		long currTime=(new Date()).getTime();
		producerCronTable.put("nextPing", currTime);
		producerCronTable.put("nextDataExtract", currTime);
		producerCronTable.put("nextProductionTime", currTime);

	}

	public NavigableMap<String, Json> extractData(NavigableMap<String, Json> map) throws Exception
	{
		lastExtractDt = (lastExtractDt == null) ? deSerializeFromTime() : lastExtractDt;

		org.joda.time.DateTime fromDt = (new org.joda.time.DateTime(lastExtractDt, org.joda.time.DateTimeZone.UTC));
		long now = new Date().getTime();
		org.joda.time.DateTime toDt = (new org.joda.time.DateTime(now, org.joda.time.DateTimeZone.UTC));

		String dataSource = ConfigUtils.getDataSource();

		if (dataSource == null)
			throw new Exception("Data source ios not configured");

		Map<String, String> parameters = new HashMap<String, String>(3);

		parameters.put(FROMTIME, fromDt.toString());
		parameters.put(TOTIME, toDt.toString());
		/**
		 * Just for debug only TODO remove
		 */
		parameters.put(FROMTIME, "2018-08-20T03:20:03.0000Z");
		parameters.put(TOTIME, "2018-08-20T04:10:03.0000Z");
		/**
		 * End debug section
		 */
		parameters.put(RESOLUTION, ConfigUtils.getTime_resolution());
		parameters.put(AGGREGATE_FUNCTION, ConfigUtils.getAggregate_function());

		String fileName = tdbService.dumpData(map, dataSource, parameters);
/*
		Json json = null;
		// qUuid = StringUtils.substringAfter(ctx,dot);
		json = SignalkMapConvertor.mapToUpdatesCSV(map, parameters);
		// json.set(CONTEXT, ctx);
*/
		if (logger.isDebugEnabled())
			logger.debug("Will enqueue file {} for producer", fileName);
		dataFilesQueue.enqueue(fileName);
		serializeFromTime(now);
		return map;
	}

	public void loadData()
	{
		init();
	}


	/**
	 * Send data to the cloud server
	 * @param _synchTransport - chosen synchronization transport
	 * @param fileName - data file name
	 * @return - number of written bytes. if return -99 : error
	 */
	public long sendData(SynchConfigObject _synchTransport, String fileName)
	{
		try {
			
			if (_synchTransport.getProducerValue(ConfigUtils.COMPRESS) != null && ! _synchTransport.getProducerValue(ConfigUtils.COMPRESS).equalsIgnoreCase("no")) {
				File sourceFile = new File(fileName);
				File zipFile = new File(sourceFile.getCanonicalPath()+ConfigUtils.COMPRESSED_EXT);
				
//				ZipUtils.zipSingleFile(sourceFile, zipFile);
				ZipUtils.zinSingleFile(sourceFile, zipFile);
				
				Long bytesWritten = (Long)_synchTransport.invokeProduceMethod(zipFile.getCanonicalPath());
				return bytesWritten;
			}
			else {
				Long bytesWritten = (Long)_synchTransport.invokeProduceMethod(fileName);
				return bytesWritten;
			}
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ConfigUtils.GLOBAL_ERROR;

	}

	/**
	 * Processing data on a cloud server
	 * @param _synchTransport - chosen synchronization transport
	 * @return - number of written bytes. if return -99 : error
	 */
	public long getData(SynchConfigObject _synchTransport)
	{
		try {
			_synchTransport.invokeConsumeMethod();
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return -99;

	}

	/**
	 * https://howtodoinjava.com/java8/java-8-watchservice-api-tutorial/
	 * 
	 * @param dirName
	 * @return
	 */
/*	
	public static String directoryWatcher(String dirName)
	{

		try {
			WatchService watchService = FileSystems.getDefault().newWatchService();
			java.nio.file.Path folder = Paths.get(dirName);

			WatchKey key = folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
			        StandardWatchEventKinds.ENTRY_MODIFY);

			while ((key = watchService.take()) != null) {
				for (WatchEvent<?> event : key.pollEvents()) {
					@SuppressWarnings("rawtypes")
					WatchEvent.Kind kind = event.kind();

					@SuppressWarnings("unchecked")
					java.nio.file.Path name = (java.nio.file.Path) ((WatchEvent<Path>) event).context();
					folder.resolve(name);
					// Path child = folder.resolve(name);
					// System.out.format("%s: %s\n", event.kind().name(),
					// child);
					if (logger.isDebugEnabled())
						logger.debug("Event kind: {}. . File affected: {}", event.kind(), event.context());

					return event.context().toString();
				}
				key.reset();
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}
*/
	public void startPinger()
	{
		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
		{
			boolean stop = false;

			@Override
			public void run()
			{
/*
				// TODO REME 4 lines
				long currTime= (new Date()).getTime();
				long nextPing=getNextPingTime();
				
				logger.debug("CurrTime {}, nextPing {}, diff {}", currTime, nextPing, nextPing-currTime );
*/				
				if ((new Date()).getTime() >= getNextPingTime()) {
					if (!stop) {
						try {
							if (logger.isDebugEnabled())
								logger.debug("Ping at will be at {}. ", (new Date()).getTime());

							pingms = HttpUtils.ping(ConfigUtils.getPingHost(), ConfigUtils.getPingTimeout());
						}
						catch (Exception e) {
							logger.error("Pinger Error : {}", e.getLocalizedMessage());
							stop = true;
						}
						if (readFrequency > ConfigUtils.MAX_PING_INTERVAL)
							setNextPingTime((new Date()).getTime()+ConfigUtils.MAX_PING_INTERVAL);
						else
							setNextPingTime((new Date()).getTime()+readFrequency);

						if (logger.isDebugEnabled())
							logger.debug("Next ping will be at {} in {}ms. ", producerCronTable.get("nextPing"), readFrequency /*producerCronTable.get("nextPing"), (new Date()).getTime()*/);
					}
				}
			}
		}, 0, DEFAULT_PING_INTERVAL, TimeUnit.MILLISECONDS);
	}

	public void startDataExtract()
	{

		Thread t = new Thread(new Runnable()
		{
			private volatile boolean stop = false;

			NavigableMap<String, Json> dataMap = new ConcurrentSkipListMap<>();

			@Override
			public void run()
			{
				if (!stop) {
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
					try {
						if (logger.isDebugEnabled())
							logger.debug("Data Extract will sleep for {}ms" , producerCronTable.get("nextDataExtract")-(new Date()).getTime());

						TimeUnit.MILLISECONDS.sleep(producerCronTable.get("nextDataExtract")-(new Date()).getTime());
					}
					catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}, "extractData");

		t.setDaemon(true);
		t.start();
	}
	
	public void startSchDataExtract()
	{
		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
		{
			boolean stop = false;

			@Override
			public void run()
			{
				NavigableMap<String, Json> dataMap = new ConcurrentSkipListMap<>();

				if ((new Date()).getTime() >= getNextExtractTime()) {
					if (!stop) {
						/**
						 * Get database extracts here
						 */
						try {
///							extractData(dataMap);
							setNextExtractTime((new Date()).getTime()+readFrequency);
							if (logger.isDebugEnabled())
								logger.debug("Next Data Extract will be in {}ms. ", readFrequency /*producerCronTable.get("nextPing"), (new Date()).getTime()*/);

						}
						catch (Exception e) {
							logger.error("Data extract Error : {}", e.getLocalizedMessage());
							stop = true;
						}
					}
				}
			}
		}, 0, DEFAULT_DATA_EXTRACT_INTERVAL, TimeUnit.MILLISECONDS);
	}
/*	
	public void startFileWatcher(String watchDir)
	{
		try {
			WatchService watcher = FileSystems.getDefault().newWatchService();
			java.nio.file.Path folder = Paths.get(watchDir);

			WatchKey _key = folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
			        StandardWatchEventKinds.ENTRY_MODIFY);

			Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
			{
				boolean stop = false;

				@Override
				public void run()
				{
					while (!stop) {
						try {
							WatchKey key = watcher.poll();
							if (null == key)
								// if ((key = watcher.poll()) == null)
								return;

							for (WatchEvent<?> event : key.pollEvents()) {
								WatchEvent.Kind<?> kind = event.kind();

								if (logger.isDebugEnabled())
									logger.debug("File Watcher Event {}",kind);

								if (kind == StandardWatchEventKinds.OVERFLOW)
									continue;

								@SuppressWarnings("unchecked")
								WatchEvent<java.nio.file.Path> ev = (WatchEvent<java.nio.file.Path>) event;
								java.nio.file.Path dataFile = ev.context();
								if (logger.isInfoEnabled())
									logger.info("Found file {}. Will process now", dataFile);

								dataFilesQueue.enqueue(DEFAULT_DATA_LOCATION + "/" + dataFile);
								
								if (ConfigUtils.getServerType().equals(ConfigUtils.PRODUCER))
//									produce();
									System.out.println("Will produce file here");
								else if (ConfigUtils.getServerType().equals(ConfigUtils.CONSUMER))
									System.out.println("Will consume file here");
//									consume();

								boolean valid = key.reset();
								if (!valid)
									logger.error("Invalid key => {}. Chugging continue...", key);
							}
						}
						catch (Exception e) {
							logger.error("File Watcher Error : {}", e.getLocalizedMessage());
							stop = true;
							// break;
						}
					}
				}
			}, 0, DEFAULT_FILE_WATCHING_PERIOD, TimeUnit.MILLISECONDS);
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Thread t = new Thread(r, "dataFileWatcher");
		// t.setDaemon(true);
		// t.start();

	}
*/	
	@SuppressWarnings("unchecked")
	public void startProducer()
	{
		Map<Integer, SynchConfigObject> _map = ConfigUtils.getTransports();

		if (logger.isDebugEnabled())
			logger.debug("Will start producer now. Scheduled interval {} {}", DEFAULT_FILE_WATCHING_PERIOD, TimeUnit.MILLISECONDS );
		
		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
		{
			boolean stop = false;

			@Override
			public void run()
			{
/*
				// TODO REME 4 lines
				long currTime= (new Date()).getTime();
				long nextPrd=getNextProductionTime();
				
				logger.debug("CurrTime {}, nextPrd {}, diff {}", currTime, nextPrd, currTime-nextPrd );
*/				
//				if (currTime >= nextPrd ) {
				if ((new Date()).getTime() >= getNextProductionTime() ) {
					if (!stop) {
						try {						
							produce();

							if (logger.isDebugEnabled())
								logger.debug("Next production will be in {}ms. ", readFrequency /*producerCronTable.get("nextPing"), (new Date()).getTime()*/);
							
						}
						catch(Exception e) {
							logger.error("Producer error {}", e.getLocalizedMessage());
							stop = true;
						}
					}
				}	
			}
		}, 0, DEFAULT_FILE_WATCHING_PERIOD, TimeUnit.MILLISECONDS);
	}

	private void produce()
	{
		if (!dataFilesQueue.isEmpty())
			inProcess = true;
//			pingms = HttpUtils.ping(ConfigUtils.getPingHost(), ConfigUtils.getPingTimeout());
			long dataSent = 0;
			try {
				logger.info("Try to run producer Time:{}", (new Date()).getTime() );
				
				for (int priority = 1; priority <= ConfigUtils.getTransports().size(); priority++) {
					SynchConfigObject _synchTransport = ConfigUtils.getTransports().get(priority);
					String useTransport = _synchTransport.getTransport();

					String frequency = _synchTransport.getFrequency();

					if (frequency.contains("m")) { // minutes
						frequency = frequency.replace("m", "");
						readFrequency = Long.parseLong(frequency) * 60 * 1000;
					}
					else if (frequency.contains("s")) { // seconds
						frequency = frequency.replace("s", "");
						readFrequency = Long.parseLong(frequency) * 1000;
					}
					else { // value in millisecs
						readFrequency = Long.parseLong(frequency);
					}

					logger.info("Will ping host: {} for transport: {}. Transport priority: {}",
					        _synchTransport.getProducerValue(ConfigUtils.HOST), _synchTransport.getName(), priority);

					/**
					 * Check suitable transport here
					 */
					if (pingms <= _synchTransport.getTransportSpeed()) {
						logger.info("Will use: {} for transport: {}. Transport priority: {}", _synchTransport.getTransport(),
						        _synchTransport.getName(), priority);

						if (readFrequency > ConfigUtils.MAX_PING_INTERVAL)
							setNextPingTime((new Date()).getTime()+ConfigUtils.MAX_PING_INTERVAL);
						else
							setNextPingTime((new Date()).getTime()+readFrequency);

						String fileName = dataFilesQueue.dequeue();
						dataSent = sendData(_synchTransport, fileName);
							logger.info("Was sent {} bytes. File name {}. Transport{}.", dataSent, fileName, _synchTransport.getTransport());

						setNextProductionTime((new Date()).getTime()+readFrequency);

						break;
					}
					else
						continue;

					// dataMap.clear();
				}
			}
			catch (CircularQueue.QueueEmptyException e) {
				logger.info("The data files queue is empty");
				setNextProductionTime((new Date()).getTime()+readFrequency);
				inProcess = false;
				return;
			}
	}

	public void setNextPingTime(long _tm)
	{
		producerCronTable.put("nextPing", _tm);
	}
	public void setNextExtractTime(long _tm)
	{
		producerCronTable.put("nextDataExtract", _tm);
	}
	public void setNextProductionTime(long _tm)
	{
		producerCronTable.put("nextProductionTime", _tm);
	}

	public long getNextPingTime()
	{
		return producerCronTable.get("nextPing");
	}
	public long getNextExtractTime()
	{
		return producerCronTable.get("nextDataExtract");
	}
	public long getNextProductionTime()
	{
		return producerCronTable.get("nextProductionTime");
	}

	
	public void consume()
	{

	}

	public static CircularQueue<String> getDataFilesQueue()
	{
		return dataFilesQueue;
	}

	private void install(File zipFile) throws Exception
	{
		if (!zipFile.getName().endsWith(".zip"))
			return;
		// unzip here
		if (logger.isDebugEnabled())
			logger.debug("Unzipping file:" + zipFile);
		try {
			// File zipFile = destination.toFile();
			String f = zipFile.getName();
			f = f.substring(0, f.indexOf("."));
			File destDir = new File(Config.getConfigProperty(STATIC_DIR) + Config.getConfigProperty(MAP_DIR) + f);
			if (!destDir.exists()) {
				destDir.mkdirs();
			}
			ZipUtils.unzip(destDir, zipFile);
			if (logger.isDebugEnabled())
				logger.debug("Unzipped file:" + destDir);
			// now add a reference in resources
			// sendChartMessage(loadChart(hasChart(getCharts(), f),
			// f).toString());
		}
		catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	private void serializeFromTime(Long t) throws Exception
	{
		// save the object to file
		try {
			fos = new FileOutputStream(filename);
			out = new ObjectOutputStream(fos);
			out.writeObject(t);
			out.flush();
			out.close();
		}
		catch (Exception e) {
			if (e instanceof FileNotFoundException) { // never run before
				try {
					Long tm = (new Date()).getTime();
					fos = new FileOutputStream(filename);
					out = new ObjectOutputStream(fos);
					out.writeObject(tm);
					out.flush();
					out.close();
				}
				catch (Exception ex) {
					throw new Exception("Can not create time serialization file");
				}
			}
			e.printStackTrace();
			logger.error("Can not serialize current time stamp. Reason : {}", e.getLocalizedMessage());
			throw new Exception("Can not serialize current time stamp" + e.getLocalizedMessage());
		}
	}

	private Long deSerializeFromTime()
	{

		// read the object from file
		try {
			fis = new FileInputStream(filename);
			in = new ObjectInputStream(fis);
			Long t = (Long) in.readObject();
			in.close();
			System.out.println(t);
			return t;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return -99L;
		}
	}

	public static void main(String[] args) throws Exception
	{
		String s = "08-20-2018T03:00:00Z";

		if (args.length != 0)
			s = args[0];

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd-yyyy'T'HH:mm:ssZ");
		try {
			Date date = simpleDateFormat.parse(s);
			(new CloudSynchService()).serializeFromTime(date.getTime());

		}
		catch (ParseException ex) {
			System.out.println("Exception " + ex);
		}

		// Properties props = System.getProperties();
		// props.setProperty("java.net.preferIPv4Stack", "true");
		// //props.setProperty("java.util.logging.manager",
		// "org.jboss.logmanager.LogManager");
		// props.setProperty("log4j.configurationFile", "./conf/log4j2.json");
		// props.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level","TRACE");
		// System.setProperties(props);
		/**
		 * PropertyConfigurator.configure("./conf/log4j2.json");
		 * InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
		 * LoggerContext context = (org.apache.logging.log4j.core.LoggerContext)
		 * LogManager.getContext(false);
		 */
	}
}
