package signalk.org.cloud_data_synch.service;

import static signalk.org.cloud_data_synch.utils.ConfigConstants.MAP_DIR;
import static signalk.org.cloud_data_synch.utils.ConfigConstants.STATIC_DIR;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONTEXT;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PATH;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PUT;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.attr;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.name;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.resources;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.value;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.vessels;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.DEFAULT_RESOLUTION;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.DEFAULT_TIMEPERIOD;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.FROMTIME;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PATH;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.RESOLUTION;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.SORT_ORDER;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.TIME;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.TIMEPERIOD;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.TOTIME;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.AGGREGATE_FUNCTION;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.log4j.PropertyConfigurator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import mjson.Json;
import signalk.org.cloud_data_synch.server.CloudSynchServer;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigUtils;
import signalk.org.cloud_data_synch.utils.ConfigUtils.SynchConfigObject;
import signalk.org.cloud_data_synch.utils.Util;
import signalk.org.cloud_data_synch.utils.ZipUtils;

@Path("/signalk/v1/Synch")
//@Api( value="Signalk Chart Management API")
public class CloudSynchService  
{

	public static int wifi = Integer.parseInt(ConfigUtils.getWifi());
	public static int mifi = Integer.parseInt(ConfigUtils.getMifi());
	public static int sattelite = Integer.parseInt(ConfigUtils.getSattelite());
	public static int PING_ERROR = -99;
	
	private static Logger logger = LogManager.getLogger(CloudSynchService.class);
	//private static boolean reloaded = false;
	protected static TDBService tdbService = null;
	private Long lastExtractDt=null;

	/**
	 *  Serialization stuff
	 */
    private static String filename = "./fromtime.ser";
    private FileOutputStream fos = null;
    private ObjectOutputStream out = null;
    private FileInputStream fis=null;
    private ObjectInputStream in = null;

	public CloudSynchService() throws Exception 
	{
	}
	
	public void init()
	{
		try {
			tdbService= (tdbService==null) ? TDBServiceFactory.getService(Config.dbName, Config.dbType, Long.valueOf(ConfigUtils.getDataReadTimeout())) : tdbService;
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public NavigableMap<String, Json> extractData(NavigableMap<String, Json> map) throws Exception
	{
		lastExtractDt = (lastExtractDt == null) ? deSerializeFromTime() : lastExtractDt;

		org.joda.time.DateTime fromDt=(new org.joda.time.DateTime( lastExtractDt, org.joda.time.DateTimeZone.UTC));
		long now = new Date().getTime();
		org.joda.time.DateTime toDt=(new org.joda.time.DateTime(now, org.joda.time.DateTimeZone.UTC));
		
		String dataSource = ConfigUtils.getDataSource();
		
		if (dataSource == null)
			throw new Exception ("Data source ios not configured");
		
		Map<String, String> parameters = new HashMap<String, String>(3);
		
		parameters.put(FROMTIME, fromDt.toString());
		parameters.put(TOTIME, toDt.toString());
/**
 * Just for debug only
 * TODO remove
 */
		parameters.put(FROMTIME, "2018-08-20T03:20:03.0000Z");
		parameters.put(TOTIME, "2018-08-20T04:10:03.0000Z");
/**
 * End debug section
 */
		parameters.put(RESOLUTION, ConfigUtils.getTime_resolution());
		parameters.put(AGGREGATE_FUNCTION, ConfigUtils.getAggregate_function());
		
		map=tdbService.dumpData(map, dataSource, parameters);
		
		Json json = null;
//			qUuid = StringUtils.substringAfter(ctx,dot);
			json = SignalkMapConvertor.mapToUpdatesCSV(map, parameters);
//			json.set(CONTEXT, ctx);

		
		serializeFromTime(now);
		return map;
	}
	
	public void loadData()
	{
		init();
	}
	
	public int sendData(SynchConfigObject _synchTransport)
	{
		return -99;
		
	}

	public void recieve()
	{
		
	}
	
	private void fileWatcher()
	{
		
	}
	
	public int ping(SynchConfigObject synchTransport)
	{
		int ret=0;
		try {
			int _ret=ping1(synchTransport.getProducerValue(ConfigUtils.HOST), Integer.parseInt(synchTransport.getProducerValue(ConfigUtils.TIMEOUT)));
			ret = _ret > ret ? _ret : ret;
		}
		catch (IOException e) {
			logger.error("Exception in ping to {} Error: {}", synchTransport.getProducerValue(ConfigUtils.HOST), e.getLocalizedMessage());
			return PING_ERROR;
		}
		return ret;
	}

	private int ping1(String ipAddress, int timeOut) throws IOException 
	{
		int _timeOut=500;
		
	    InetAddress inet = InetAddress.getByName(ipAddress);
//	    InetAddress inet = InetAddress.getByAddress(ipAddress);
	    
	    long startTm = (new Date()).getTime();

	    if (logger.isDebugEnabled())
	    	logger.debug("Sending Ping Request to {}. Time:{}",ipAddress, String.valueOf(startTm));
	   
	    boolean isReachable = inet.isReachable(_timeOut);

	    long reachedIn = (new Date()).getTime() - startTm;
	    
	    if (!isReachable) {	    
		    if (logger.isInfoEnabled())
		    	logger.debug("Host is NOT reachable - {}", ipAddress);
	    }
	    else {
		    if (logger.isInfoEnabled())
		    	logger.debug("Host is reachable - {} time - {}ms", ipAddress, reachedIn);
		    return 0;
	    }	
	    
	    if (reachedIn < Integer.parseInt(ConfigUtils.getWifi()))
	    	return wifi;	
	    else if (reachedIn < Integer.parseInt(ConfigUtils.getMifi()))
	    	return mifi;
	    else 
	    	return sattelite;
	}
	
	
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	//@Produces("application/json")
    //	@ApiOperation(value = "Upload a  TMS chart", notes = "Accepts a zipfile created by https://github.com/rob42/freeboard-installer ", response = String.class)

	public Response post(FormDataMultiPart form) throws Exception {
		if(logger.isDebugEnabled())logger.debug("Uploading file..");
		List<String> contentRange = form.getHeaders().get("Content-Range");
		FormDataBodyPart filePart = form.getField("file[]");
		ContentDisposition header =filePart.getContentDisposition();
		InputStream fileInputStream = filePart.getValueAs(InputStream.class);
		String fileName = header.getFileName();
		if(!fileName.endsWith(".zip")) {
			return Response.status(HttpStatus.SC_BAD_REQUEST).entity(fileName +": Only zip files allowed!").build();
		}
		File dest = new File(Config.getConfigProperty(STATIC_DIR) + Config.getConfigProperty(MAP_DIR) + fileName);

		if(contentRange!=null&& contentRange.get(0)!=null){
			String range = contentRange.get(0);
			range=StringUtils.remove(range,"bytes ");
			long total = Long.valueOf(range.split("/")[1]);
			range=StringUtils.substringBefore(range,"/");
			long start = Long.valueOf(range.split("-")[0]);
			long end = Long.valueOf(range.split("-")[1]);

			java.nio.file.Path destFile=Paths.get(dest.toURI());
			if(start==0){
				if(logger.isDebugEnabled())logger.debug("Uploading new file: {}, size:{}", dest.getAbsoluteFile(),total);
				FileUtils.deleteQuietly(destFile.toFile());
				FileUtils.touch(destFile.toFile());
			}else {
				if(logger.isDebugEnabled())logger.debug("Uploading continuation: {} : size:{}, this:{}-{}", dest.getAbsoluteFile(),total, start, end);
			}
			Files.write(destFile,IOUtils.toByteArray(fileInputStream), StandardOpenOption.APPEND);
			if(total == end+1) {
				install(dest);
			}
		}else{
			FileUtils.copyInputStreamToFile(fileInputStream, dest);
			if(logger.isDebugEnabled())logger.debug("Uploading to file: {}", dest.getAbsoluteFile());
			install(dest);
		}

		Json f = Json.object();
		f.set("name", fileName);
		f.set("size", dest.length());


		return Response.status(200).entity(f.toString()).build();

	}

	public static void reloadCharts() throws Exception {

		logger.info("Reload charts at startup");
		String staticDir = Config.getConfigProperty(STATIC_DIR);
		if (!staticDir.endsWith("/")) {
			staticDir = staticDir + "/";
		}
		NavigableMap<String, Json> map = getCharts();
		Map<String, String> query = new HashMap<>();
		query.put("skey", "charts");
		// get current charts

		tdbService= (tdbService==null) ? TDBServiceFactory.getService(Config.dbName, Config.dbType) : tdbService;

		tdbService.loadResources(map, query);
		logger.info("Existing charts: Quan:{}", map.size() / 2);
		logger.debug("Existing charts: Quan:{} : {}", map.size() / 2, map);
		File mapDir = new File(staticDir + Config.getConfigProperty(MAP_DIR));
		logger.info("Reloading charts from: " + mapDir.getAbsolutePath());
		if (mapDir == null || !mapDir.exists() || mapDir.listFiles() == null)
			return;
		// TreeMap<String, Object> treeMap = new TreeMap<String,
		// Object>(signalkModel.getSubMap("resources.charts"));
		// get a list of current charts

		for (File chart : mapDir.listFiles()) {
			if (chart.isDirectory()) {
				if (hasChart(map, chart.getName())==null) {
					Json chartJson = CloudSynchService.loadChart(hasChart(map, chart.getName()),chart.getName());
					logger.info("Loading new chart: {}= {}", chart.getName(), chartJson);
					try {
						Util.sendRawMessage(Config.getConfigProperty(Config.ADMIN_USER),
								Config.getConfigProperty(Config.ADMIN_PWD), chartJson.toString());
					} catch (Exception e) {
						logger.warn(e.getMessage());
					}
				}
			}
		}
		logger.info("Chart resources updated");

	}

	public static String hasChart(NavigableMap<String, Json> map, String name) {
		for (Entry<String, Json> e : map.entrySet()) {
			if (e.getKey().endsWith(attr))
				continue;
			if (logger.isDebugEnabled())
				logger.debug("Checking chart: {} : {} : {}", name, e.getKey(), e.getValue());
			if (name.equals(e.getValue().at("identifier").asString())) {
				logger.info("Existing chart: {} = {}", name, e.getKey());
				return e.getKey();
			}
		}
		return null;
	}

	public static NavigableMap<String, Json> getCharts() throws Exception
	{
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<>();
		Map<String, String> query = new HashMap<>();
		query.put("skey", "charts");
		// get current charts
		
		tdbService= (tdbService==null) ? TDBServiceFactory.getService(Config.dbName, Config.dbType) : tdbService;

		tdbService.loadResources(map, query);
		return map;
	}

	private void install(File zipFile) throws Exception {
		if (!zipFile.getName().endsWith(".zip"))
			return;
		// unzip here
		if (logger.isDebugEnabled())logger.debug("Unzipping file:" + zipFile);
		try {
			// File zipFile = destination.toFile();
			String f = zipFile.getName();
			f = f.substring(0, f.indexOf("."));
			File destDir = new File(Config.getConfigProperty(STATIC_DIR) + Config.getConfigProperty(MAP_DIR) + f);
			if (!destDir.exists()) {
				destDir.mkdirs();
			}
			ZipUtils.unzip(destDir, zipFile);
			if (logger.isDebugEnabled())logger.debug("Unzipped file:" + destDir);
			// now add a reference in resources
			sendChartMessage(loadChart(hasChart(getCharts(), f),f).toString());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	public static Json loadChart(String key, String chartName) throws Exception {
		try {
			File destDir = new File(
					Config.getConfigProperty(STATIC_DIR) + Config.getConfigProperty(MAP_DIR) + chartName);
			SAXReader reader = new SAXReader();
			Document document = reader.read(new File(destDir, "tilemapresource.xml"));

			String title = document.getRootElement().element("Title").getText();
			String scale = "250000";
			double[] bounds = {0.0,0.0,0.0,0.0};
			if (document.getRootElement().element("Metadata") != null) {
				scale = document.getRootElement().element("Metadata").attribute("scale").getText();
			}
			if (document.getRootElement().element("BoundingBox") != null) {
				//<BoundingBox minx="170.63201130808108" miny="-43.799956482598866" maxx="179.99879176919154" maxy="-32.49905360842772"/>
				Element box = document.getRootElement().element("BoundingBox");
				
				bounds[0]=Double.valueOf(box.attribute("minx").getText());
				bounds[1]=Double.valueOf(box.attribute("miny").getText());
				bounds[2]=Double.valueOf(box.attribute("maxx").getText());
				bounds[3]=Double.valueOf(box.attribute("maxy").getText());
			}
			
			double maxRes = 0.0;
			double minRes = Double.MAX_VALUE;
			int maxZoom = 0;
			int minZoom = 99;
			Element tileSets = document.getRootElement().element("TileSets");
			for (Object o : tileSets.elements("TileSet")) {
				Element e = (Element) o;
				int href = Integer.parseInt(e.attribute("href").getValue());
				maxZoom = Math.max(href, maxZoom);
				minZoom = Math.min(href, minZoom);
				double units = Double.parseDouble(e.attribute("units-per-pixel").getValue());
				maxRes = Math.max(units, maxRes);
				minRes = Math.min(units, minRes);
			}
			// now make an entry in resources
			
			Json resource = createChartMsg(key, chartName, title, scale, minZoom, maxZoom, bounds);
			return resource;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	private static Json createChartMsg(String key, String f, String title, String scale, int minZoom, int maxZoom, double[] bounds) {
		Json val = Json.object();
		
		if(key!=null) {
			//existing chart
			val.set(PATH, key);
		}else {
			//new chart
			val.set(PATH, "charts." + "urn:mrn:signalk:uuid:" + java.util.UUID.randomUUID().toString());
		}
		Json currentChart = Json.object();
		val.set(value, currentChart);
		String time = Util.getIsoTimeString();
		time = time.substring(0, time.indexOf("."));
		currentChart.set("identifier", f);
		currentChart.set(name, title);
		currentChart.set("description", title);
		currentChart.set("minzoom", minZoom);
		currentChart.set("maxzoom", maxZoom);
		currentChart.set("bounds", bounds);
		currentChart.set("format", "png");
		currentChart.set("type","tilelayer");
		currentChart.set("tilemapUrl", "/" + Config.getConfigProperty(MAP_DIR) + f+"/{z}/{x}/{-y}.png");
		try {
			int scaleInt = Integer.valueOf(scale);
			currentChart.set("scale", scaleInt);
		} catch (Exception e) {
			currentChart.set("scale", 0);
		}

		Json updates = Json.array();
		updates.add(val);
		Json msg = Json.object();
		msg.set(CONTEXT, resources);
		msg.set(PUT, updates);

		if (logger.isDebugEnabled())
			logger.debug("Created new chart msg:{}", msg);
		return msg;
	}
	
	private void sendChartMessage(String body) throws Exception {
		Util.sendRawMessage(Config.getConfigProperty(Config.ADMIN_USER),
				Config.getConfigProperty(Config.ADMIN_PWD), body);

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
		            	Long tm= (new Date()).getTime();
			            fos = new FileOutputStream(filename);
			            out = new ObjectOutputStream(fos);
			            out.writeObject(tm);
			            out.flush();
			            out.close();
	            	}
	            	catch(Exception ex) {
	            		throw new Exception ("Can not create time serialization file");
	            	}
	        	}
	            e.printStackTrace();
	            logger.error("Can not serialize current time stamp. Reason : {}",e.getLocalizedMessage());
	            throw new Exception("Can not serialize current time stamp"+e.getLocalizedMessage());
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
	    String s = "08/20/2018T03:00:00Z";
	    
	    if (args.length != 0) 
	    	s = args[0];
	    
	    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	    try
	    {
	        Date date = simpleDateFormat.parse(s);	        
	        (new CloudSynchService()).serializeFromTime(date.getTime());

	    }
	    catch (ParseException ex)
	    {
	        System.out.println("Exception "+ex);
	    }
		
		
//		Properties props = System.getProperties();
//		props.setProperty("java.net.preferIPv4Stack", "true");
//		//props.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
//		props.setProperty("log4j.configurationFile", "./conf/log4j2.json");
//		props.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level","TRACE");
//		System.setProperties(props);
/**		
		PropertyConfigurator.configure("./conf/log4j2.json");
		InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
*/		
	}
}
