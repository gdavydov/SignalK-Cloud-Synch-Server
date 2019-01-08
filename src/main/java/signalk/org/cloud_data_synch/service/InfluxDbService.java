package signalk.org.cloud_data_synch.service;

import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONFIG;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONTEXT;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.DEFAULT_RESOLUTION;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.DEFAULT_TIMEPERIOD;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.FROMTIME;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PATH;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.RESOLUTION;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.SORT_ORDER;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.TIME;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.TIMEPERIOD;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.TOTIME;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.UPDATES;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.aircraft;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.aton;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.dot;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.meta;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.resources;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.sar;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.self_str;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.sentence;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.skey;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.sourceRef;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.sources;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.timestamp;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.uuid;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.value;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.values;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.version;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.vessels;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_DATA_LOCATION;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mjson.Json;
import okhttp3.OkHttpClient;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigConstants;
import signalk.org.cloud_data_synch.utils.ConfigUtils;
import signalk.org.cloud_data_synch.utils.Util;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.impl.InfluxDBImpl;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

public class InfluxDbService extends SignalKCloudSynchService implements TDBService
{

	private static final String STR_VALUE = "strValue";
	private static final String LONG_VALUE = "longValue";
	private static final String DOUBLE_VALUE = "doubleValue";
	private static final String NULL_VALUE = "nullValue";
	private static final String STR_VALUE_SHORT = "sV";
	private static final String LONG_VALUE_SHORT = "lV";
	private static final String DOUBLE_VALUE_SHORT = "dV";
	private static final String NULL_VALUE_SHORT = "nV";
	private static final String SRC_REF_SHORT = "ref";
	private static final String PRIMARY = "primary";
	private static final String PRIMARY_SHORT = "prim";
	private static final String SOURCE_REF = "sourceRef";
	private static final String SOURCE_REF_SHORT = "src";

	private static final String TAGS = "tags";
	private static final String VALUES = "vals";
	private static final String SERIES = "ser";
	private static final String RESULT = "result";
	
	private static Map<String, String>forwardConversionMap = new HashMap<String, String>();
	static {
		forwardConversionMap.put(STR_VALUE, STR_VALUE_SHORT);
		forwardConversionMap.put(LONG_VALUE, LONG_VALUE_SHORT);
		forwardConversionMap.put(DOUBLE_VALUE, DOUBLE_VALUE_SHORT);
		forwardConversionMap.put(NULL_VALUE, NULL_VALUE_SHORT);
		forwardConversionMap.put(SOURCE_REF, SOURCE_REF_SHORT);
		forwardConversionMap.put(PRIMARY, PRIMARY_SHORT);
		forwardConversionMap.put(uuid, uuid);
		forwardConversionMap.put(skey, skey);
		
	}

	private static Map<String, String>backConversionMap = new HashMap<String, String>();
	static {
		backConversionMap.put(STR_VALUE_SHORT, STR_VALUE);
		backConversionMap.put(LONG_VALUE_SHORT, LONG_VALUE);
		backConversionMap.put(DOUBLE_VALUE_SHORT, DOUBLE_VALUE);
		backConversionMap.put(NULL_VALUE_SHORT, NULL_VALUE);
		backConversionMap.put(SOURCE_REF_SHORT, SOURCE_REF);
		backConversionMap.put(PRIMARY_SHORT, PRIMARY);
		backConversionMap.put(uuid, uuid);
		backConversionMap.put(skey, skey);
	}
	
	private static Logger logger = LogManager.getLogger(InfluxDbService.class);
	private static InfluxDB influxDB;
	private static String dbName = "signalk";
	private static TDBService instance;

	public static final String PRIMARY_VALUE = "primary";
	public static final ConcurrentSkipListMap<String, String> primaryMap = new ConcurrentSkipListMap<>();
	public static boolean allowWrite=false;

	QueryBuilder qBuilder = new QueryBuilder();

	public InfluxDbService() {
		setUpTDb();
	}

	public InfluxDbService(String dbName) {
		InfluxDbService.dbName=dbName;
		setUpTDb();
	}

	public InfluxDbService(String dbName, long timeout) {
		InfluxDbService.dbName=dbName;
		setUpTDb(timeout);
	}

	public static final TDBService setUpTDb(String dbName) {
		return instance = new InfluxDbService(dbName);
	}

	public static final TDBService setUpTDb(String dbName, Long timeout) {
		return instance = new InfluxDbService(dbName, timeout);
	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#setUpInfluxDb()
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void setUpTDb() {
		influxDB = InfluxDBFactory.connect("http://localhost:8086", "admin", "admin");
		try {
			if (!influxDB.databaseExists(dbName))
				influxDB.createDatabase(dbName);
		}
		catch(Exception e) {
			logger.error(e.getLocalizedMessage());
			throw e;
		}

		influxDB.setDatabase(dbName);

		influxDB.enableBatch(BatchOptions.DEFAULTS.exceptionHandler((failedPoints, throwable) -> {
			logger.error("FAILED:"+failedPoints);
			logger.error(throwable);
		}));
		if(primaryMap.size()==0)
			loadPrimary();

	}

	@SuppressWarnings("deprecation")
	@Override
	public void setUpTDb(Long timeout) {
		
		okhttp3.OkHttpClient.Builder client = (new okhttp3.OkHttpClient.Builder()).readTimeout(timeout, TimeUnit.MILLISECONDS);

		influxDB = InfluxDBFactory.connect("http://localhost:8086", "admin", "admin", client);
/*		
		InfluxDBImpl a;
		precision - RFC3339
		format CSV
*/
		try {
			if (!influxDB.databaseExists(dbName))
				influxDB.createDatabase(dbName);
		}
		catch(Exception e) {
			logger.error(e.getLocalizedMessage());
			throw e;
		}

		influxDB.setDatabase(dbName);

		influxDB.enableBatch(BatchOptions.DEFAULTS.exceptionHandler((failedPoints, throwable) -> {
			logger.error("FAILED:"+failedPoints);
			logger.error(throwable);
		}));
		if(primaryMap.size()==0)
			loadPrimary();

	}
	
	
	
	@Override
	public void setWrite(boolean write) {
		allowWrite=write;
		try {
			Config.saveConfig();
		} catch (IOException e) {
			logger.error(e,e);
		}
	}

	@Override
	public boolean getWrite() {
		return allowWrite;
	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#closeInfluxDb()
	 */
	@Override
	public void closeTDb() {
		influxDB.close();
	}

	@Override
	public NavigableMap<String, Json> loadResources(NavigableMap<String, Json> map, Map<String, String> query) {
		//"select * from "+table+" where uuid='"+uuid+"' AND skey=~/"+pattern+"/ group by skey,primary, uuid,sourceRef,owner,grp order by time desc limit 1"
		String queryStr="select * from resources "+getWhereString(query)+" group by skey, primary, uuid,sourceRef order by time desc limit 1";
		return loadResources(map, queryStr);
	}

	private String getWhereString(Map<String, String> query)
	{
		StringBuilder builder = new StringBuilder();
		boolean timeCondition = false;
		if(query==null || query.size()==0)
			return "";
		String joiner=" where ";
		for(Entry<String, String> e: query.entrySet()) {
			switch (e.getKey()) {
				case FROMTIME:
				case TOTIME:
				case TIMEPERIOD:
					timeCondition = true;
					continue;
				default :
					builder.append(joiner)
					.append(e.getKey())
					.append("=~/")
					.append(e.getValue())
					.append("/");
					joiner=" and ";
					continue;
			}
		}
/*
		if (timeCondition) {
			builder.append(joiner).append(timeConditionCalulator(query));
		}
*/
		return builder.toString();

	}
/*
	/**
	 * Use case
	 * 1. ?fromTime=XXX&ToTime=YYYY[&groupInterval=3m] (default 30 min)
	 * 2. ?fromTime=XXX&timePeriod=2h|3d|4m[&groupInterval=3m] (default 30 min) (toTime=fromTime+timePeriod)
	 * 3. ?FromTime=XXXX[&groupInterval=3m] (default 30 min)  (toTime=now)
	 * 4. ?timePeriod=2h|3d|4m[&groupInterval=3m] (default 30 min) (in this case fromTime will be now-DEFAULT_TIMEPERIOD, toTime=now
	 *
	 *
	 * TODO : add grouping parameter and static aggregation mapping table like speed - average, position - null, temperature - average, etc
	 * sum, avg, mean, first, last
	 *
	 * IF toTime is omitted then default value is now
	 *
	 * @param query
	 * @return time condition string
	 */
/*
	private String timeConditionCalulator (Map<String, String> query)
	{
		StringBuilder builder = new StringBuilder();
		String timePeriod = ((timePeriod = query.get(TIMEPERIOD)) == null) ? DEFAULT_TIMEPERIOD : timePeriod;
		String toTime = ((toTime = query.get(TOTIME)) == null) ? "now" : toTime;
		String fromTime = ((fromTime = query.get(FROMTIME)) == null) ? toTime+"-"+timePeriod : fromTime;

		builder.append(TIME)
	       .append(" >= ")
	       .append("'").append(fromTime).append("'")
	       .append(" and ")
	       .append(TIME)
	       .append("<=")
	       .append("'").append(toTime).append("'");

		return builder.toString();
	}
*/
	@Override
	public NavigableMap<String, Json> loadConfig(NavigableMap<String, Json> map, Map<String, String> query) {
		String queryStr="select * from config "+getWhereString(query)+" group by skey order by time desc limit 1";
		return loadConfig(map, queryStr);
	}

	@Override
	public NavigableMap<String, Json> loadData(NavigableMap<String, Json> map, String table, Map<String, String> query, boolean parametersExist) {

		String queryStr=qBuilder.build(table,  query);
//		String queryStr="select * from "+table+getWhereString(query)+" group by uuid,skey,primary,sourceRef order by time desc"+ limit;

		return loadData(map, queryStr);
	}

	public String dumpData(NavigableMap<String, Json> map, String table, Map<String, String> query) throws Exception
	{
		String _queryStr="select max(longValue) as longValue, mean(doubleValue) as doubleValue "; //+table+getWhereString(query)+" group by uuid,skey,primary,sourceRef order by time desc"+ limit;
		
		
		String queryStr=qBuilder.build(_queryStr, table,  query);
		return dumpData(map, queryStr);
	}

	@Override
	public NavigableMap<String, Json> loadSources(NavigableMap<String, Json> map, Map<String, String> query) {
		String queryStr="select * from sources "+getWhereString(query)+" group by skey order by time desc limit 1";
		return loadSources(map, queryStr);
	}

	public NavigableMap<String, Json> loadResources(NavigableMap<String, Json> map, String queryStr) {
		if (logger.isDebugEnabled())
			logger.debug("Query: {}", queryStr);
		Query query = new Query(queryStr, dbName);
		QueryResult result = influxDB.query(query);

		if (result == null || result.getResults() == null)
			return map;
		result.getResults().forEach((r) -> {
			if (r == null ||r.getSeries()==null)
				return;
			r.getSeries().forEach((s) -> {
				if (logger.isDebugEnabled())logger.debug(s);
				if(s==null)return;

				Map<String, String> tagMap = s.getTags();
				String key = s.getName() + dot + s.getTags().get("skey");

				Json val = getJsonValue(s,0);
				if(key.contains(".values.")){
					//handle values
					if (logger.isDebugEnabled())logger.debug("values: {}",val);
					String parentKey = StringUtils.substringBeforeLast(key,".values.");
					String valKey = StringUtils.substringAfterLast(key,".values.");
					String subkey = StringUtils.substringAfterLast(valKey,".value.");

					if (logger.isDebugEnabled())
						logger.debug("KEYS: parentKey={} valKey={} subkey={}", parentKey, valKey, subkey);

					//make parent Json
					Json parent = getParent(map,parentKey);

					//add attributes
					if (logger.isDebugEnabled())logger.debug("Primary value: {}",tagMap.get("primary"));
					boolean primary = Boolean.valueOf((String)tagMap.get("primary"));
					if(primary){
						extractPrimaryValue(parent,s,subkey,val, false);
					}
					else{
						valKey=StringUtils.substringBeforeLast(valKey,".");
						Json valuesJson = Util.getJson(parent,values, null );

						Json subJson = valuesJson.at(valKey);
						if(subJson==null){
							subJson = Json.object();
							valuesJson.set(valKey,subJson);
						}
						extractValue(subJson,s,subkey, val, false);
					}

					return;
				}
				if( (key.endsWith(".value")||key.contains(".value."))){
					if (logger.isDebugEnabled())logger.debug("value: {}",val);
					String subkey = StringUtils.substringAfterLast(key,".value.");

					key = StringUtils.substringBeforeLast(key,".value");

					//make parent Json
					Json parent = getParent(map,key);
					if (logger.isDebugEnabled())logger.debug("Primary value: {}",tagMap.get("primary"));
					boolean primary = Boolean.valueOf((String)tagMap.get("primary"));
					if(primary){
						extractPrimaryValue(parent,s,subkey,val, false);
					}else{
						extractValue(parent,s,subkey, val, false);
					}
					//extractValue(parent,s, subkey, val);

					map.put(key,parent);
					return;
				}

				map.put(key,val);


			});
		});
		return map;
	}

	public NavigableMap<String, Json> loadConfig(NavigableMap<String, Json> map, String queryStr) {
		Query query = new Query(queryStr, dbName);
		QueryResult result = influxDB.query(query);

		if (result == null || result.getResults() == null)
			return map;
		result.getResults().forEach((r) -> {
			if (r.getSeries() == null ||r.getSeries()==null)
				return;
			r.getSeries().forEach((s) -> {
				if (logger.isDebugEnabled())logger.debug(s);
				if(s==null)return;

				Map<String, String> tagMap = s.getTags();
				String key = s.getName() + dot + tagMap.get("skey");

				Json val = getJsonValue(s,0);

				map.put(key,val);


			});
		});
		return map;
	}


	public NavigableMap<String, Json> loadData(NavigableMap<String, Json> map, String queryStr){
		if (logger.isDebugEnabled())
			logger.debug("queryStr: {}",queryStr);

		Query query = new Query(queryStr, dbName);
		QueryResult result = influxDB.query(query);
		//NavigableMap<String, Json> map = new ConcurrentSkipListMap<>();
		if (logger.isDebugEnabled())
			logger.debug(result);
		if(result==null || result.getResults()==null)
			return map;

		result.getResults().forEach((r)-> {
			if (logger.isDebugEnabled())
				logger.debug(r);
			if(r==null||r.getSeries()==null)
				return;
			r.getSeries().forEach(
				(s)->{
					if (logger.isDebugEnabled())
						logger.debug(s);
					if(s==null)
						return;


					Map<String, String> tagMap = s.getTags();
					String key = s.getName()+dot+tagMap.get("uuid")+dot+tagMap.get("skey");

					Json val = null;
					if (s.getValues().size() == 1)
						val = getJsonValue(s,0);
					else
						val= getJsonValues(s,0);

					//add timestamp and sourceRef
					if(key.endsWith(".sentence")){
						if (logger.isDebugEnabled())logger.debug("sentence: {}",val);
						//make parent Json
						String parentKey = StringUtils.substringBeforeLast(key,".");

						Json parent = getParent(map,parentKey);

						parent.set(sentence,val);

						return;

					}
					if(key.contains(".meta.")){
						//add meta to parent of value
						if (logger.isDebugEnabled())logger.debug("meta: {}",val);
						String parentKey = StringUtils.substringBeforeLast(key,".meta.");
						String metaKey = StringUtils.substringAfterLast(key,".meta.");

						//make parent Json
						Json parent = getParent(map,parentKey);

						//add attributes
						addAtPath(parent,"meta."+metaKey, val);

						return;
					}
					if(key.contains(".values.")){
						//handle values
						if (logger.isDebugEnabled())
							logger.debug("key: {}, values: {}",key,val);
						String parentKey = StringUtils.substringBeforeLast(key,".values.");
						String valKey = StringUtils.substringAfterLast(key,".values.");
						String subkey = StringUtils.substringAfterLast(valKey,".value.");

						if (logger.isDebugEnabled())logger.debug("parentKey: {}, valKey: {}, subKey: {}",parentKey,valKey, subkey);
						//make parent Json
						Json parent = getParent(map,parentKey);

						//add attributes
						if (logger.isDebugEnabled())logger.debug("Primary value: {}",tagMap.get("primary"));
						boolean primary = Boolean.valueOf((String)tagMap.get("primary"));
						if(primary) {
							if (val.isArray())
								extractPrimaryValuesEx(parent,s, subkey,val,true);
							else
								extractPrimaryValue(parent,s,subkey,val,true);
						}
						else{

							valKey=StringUtils.substringBeforeLast(valKey,".");
							Json valuesJson = Util.getJson(parent,values, null );
							Json subJson = valuesJson.at(valKey);
							if(subJson==null){
								subJson = Json.object();
								valuesJson.set(valKey,subJson);
							}

							if (val.isArray())
								extractValue(subJson,s,subkey, val, true);
							else
								extractValue(subJson,s,subkey, val, true);
						}

						return;
					}
					if((key.endsWith(".value")||key.contains(".value.")))
					{
						if (logger.isDebugEnabled())logger.debug("value: {}",val);
						String parentKey = StringUtils.substringBeforeLast(key,".values.");
						String subkey = StringUtils.substringAfterLast(key,".value.");

						key = StringUtils.substringBeforeLast(key,".value");

						if (logger.isDebugEnabled())logger.debug("parentKey: {}, valKey: {}, subKey: {}",parentKey, key, subkey);

						//make parent Json
						Json parent = getParent(map,key);
						if (logger.isDebugEnabled())logger.debug("Primary value: {}",tagMap.get("primary"));
						boolean primary = Boolean.valueOf((String)tagMap.get("primary"));
						if(primary) {
							if (val.isArray())
								extractPrimaryValuesEx(parent,s, subkey,val,true);
							else
								extractPrimaryValue(parent,s,subkey,val,true);
						}
						else{
							if (val.isArray())
								extractValue(parent,s,subkey, val, true);
							else
								extractValue(parent,s,subkey, val, true);
						}
						//extractValue(parent,s, subkey, val);

						map.put(key,parent);
						return;
					}

					map.put(key,val);


				});
			});

		// merge maps together
/*
		map.forEach((k,v) -> {
			System.out.println ("key="+k+" Val="+v);
		});
*/
		return map;
	}

	/**
	 * 
	 * @param map
	 * @param queryStr 
	 * @return fileName with data dumps
	 * @throws Exception
	 * 
	 * Also see https://www.programcreek.com/java-api-examples/?api=org.influxdb.dto.QueryResult
	 */
	protected String dumpData(NavigableMap<String, Json> map, String queryStr) throws Exception
	{
		if (logger.isDebugEnabled())
			logger.debug("queryStr: {}", queryStr);

		Query query = new Query(queryStr, dbName);
		QueryResult result = influxDB.query(query);
		ArrayList<QueryResult.Result> aa= (ArrayList<QueryResult.Result>)result.getResults();
		
		
		// QueryResult result = influxDB.query(query, 5000, queryResult ->
		// System.out.println(queryResult));

		// influxDB.query(query, 5000, queryResult ->
		// System.out.println(queryResult));
		// NavigableMap<String, Json> map = new ConcurrentSkipListMap<>();
		if (logger.isDebugEnabled())
			logger.debug(result);
		
		if (result == null || result.getResults() == null)
			return null;
		
//		return formatDumpFile(result);
		
		return serializeDataDumpResults(result);
	}
	
	private String formatDumpFile(QueryResult result) throws Exception
	{
		String charset = "UTF-8";
		PrintWriter pw;

		String fileName = String.valueOf((new Date()).getTime()) + ".influxdb." + ConfigUtils.DEFAULT_DATA_FILE_EXTENTION;

		File dd = new File(ConfigUtils.getProducerDataFolder(), fileName);
		
		if (logger.isDebugEnabled())
			logger.debug("Will dump data into the {}", dd.getCanonicalFile());

		FileOutputStream fos = new FileOutputStream(dd);
		pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos, charset)), false);

		StringBuilder _sb = new StringBuilder();

		result.getResults().forEach((r) -> {
			if (logger.isDebugEnabled())
				logger.debug(r);

			// if(r==null||r.getSeries()==null)
			// return;

			r.getSeries().forEach((s) -> {
				if (logger.isDebugEnabled())
					logger.debug(" {}:", s);
				if (s == null)
					return;

				Map<String, String> tags = s.getTags();
				List<String> cols = s.getColumns();
				List<List<Object>> vals = s.getValues();
				/*
				 * _sb.append(s.getName()); tags.forEach((k,v) -> { if (v !=
				 * null && !v.isEmpty()) { _sb.append(",");
				 * _sb.append(k).append('=').append(String.valueOf(v)); } });
				 * String separator=" ";
				 */
				vals.forEach((dataRow) -> {

					_sb.append(s.getName());
					tags.forEach((k, v) -> {
						if (v != null && !v.isEmpty()) {
							_sb.append(",");
							_sb.append(k).append('=').append(String.valueOf(v));
						}
					});
					String separator = " ";
					int timePos = 0;

					for (int i = 0; i < cols.size(); i++) {
						Object _val = dataRow.get(i);
						if (cols.get(i).equals("time")) {
							timePos = i;
							continue;
						}
						if (_val != null) {
							_sb.append(separator);
							_sb.append(cols.get(i)).append('=').append(String.valueOf(_val));
							separator.replace(separator, ","); // stupidity of
							                                   // lambda
							                                   // expressions
						}
					}

					_sb.append(' ');
					_sb.append(Util.getMillisFromIsoTime(String.valueOf(dataRow.get(timePos))));

					// try {
					_sb.append("\r");
					pw.write(_sb.toString());
					// }
					// catch (IOException e) {
					// logger.catching(e);
					// return;
					// }
					_sb.delete(0, _sb.length());
				});
			});
		});
		pw.flush();
		pw.close();
		// dd.renameTo(new File(ConfigUtils.getProducerDataFolder(), fileName));
		/*
		 * map.forEach((k,v) -> { System.out.println ("key="+k+" Val="+v); });
		 */
		return ConfigUtils.getProducerDataFolder() + "/" + fileName;

	}

	private String serializeDataDumpResults(QueryResult result) throws Exception
	{
		String charset = "UTF-8";
		PrintWriter pw;
		
		Json seriesArray=Json.array();

		String fileName = String.valueOf((new Date()).getTime()) + ".influxdb." + ConfigUtils.DEFAULT_DATA_FILE_EXTENTION;

		File dd = new File(ConfigUtils.getProducerDataFolder(), fileName);
		
		if (logger.isDebugEnabled())
			logger.debug("Will dump data into the {}", dd.getCanonicalFile());

		FileOutputStream fos = new FileOutputStream(dd);
		pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos, charset)), false);

		StringBuilder _sb = new StringBuilder();

		result.getResults().forEach((r) -> {
			if (logger.isDebugEnabled())
				logger.debug(r);

			// if(r==null||r.getSeries()==null)
			// return;
			
			r.getSeries().forEach((s) -> {
				if (logger.isDebugEnabled())
					logger.debug(" {}:", s);
				if (s == null)
					return;

				 Json ser = getJsonValues(s);
				 seriesArray.add(Json.object(s.getName(),ser));
//				 seriesArray.set("name",s.getName());
//				 try {
//					_sb.append("\r");
//					pw.write(_sb.toString());
					// }
					// catch (IOException e) {
					// logger.catching(e);
					// return;
					// }
//					_sb.delete(0, _sb.length());

			});
	    });
		
		pw.write(Json.object(RESULT,seriesArray).toString());
		pw.flush();
		pw.close();
		return ConfigUtils.getProducerDataFolder() + "/" + fileName;
	}


	/**
	 * https://docs.influxdata.com/influxdb/v1.6/guides/writing_data/
	 * 
	 * Note: If your data file has more than 5,000 points, it may be necessary to split that file into several files in order to write your data in batches to InfluxDB. By default, the HTTP request times out after five seconds. InfluxDB will still attempt to write the points after that time out but there will be no confirmation that they were successfully written.
	 * 
	 * @param fileName
	 */
	
	private void writeBulkData(String fileName) {
		
	}
	
	private Json getParent(NavigableMap<String, Json> map, String parentKey)
	{

		//make parent Json
		Json parent = map.get(parentKey);
		if(parent==null){
			parent = Json.object();
			map.put(parentKey,parent);
		}

		map.keySet().forEach((k) -> {
			logger.debug("getParent : Result map entry {}", k);
		});

		return parent;
	}

	public NavigableMap<String, Json> loadSources(NavigableMap<String, Json> map, String queryStr) {
		Query query = new Query(queryStr, dbName);
		QueryResult result = influxDB.query(query);
		// NavigableMap<String, Json> map = new ConcurrentSkipListMap<>();

		if (logger.isDebugEnabled())logger.debug(result);
		if (result == null || result.getResults() == null)
			return map;
		result.getResults().forEach((r) -> {
			if (logger.isDebugEnabled())
				logger.debug(r);
			if (r.getSeries() == null)
				return;
			r.getSeries().forEach((s) -> {

				if (logger.isDebugEnabled())logger.debug("Load source: {}",s);
				if (s == null)
					return;

				String key = s.getName() + dot + s.getTags().get("skey");
				if (logger.isDebugEnabled())logger.debug("Load source map: {} = {}",()->key,()->getJsonValue(s,0));
				map.put(key, getJsonValue(s,0));


			});
		});
		return map;
	}

	private Object getValue(String field, Series s, int row) {
		int i = s.getColumns().indexOf(field);
		if (i < 0)
			return null;
		return (s.getValues().get(row)).get(i);
	}

	private Object getValueEx(String field, Series s, List<Object> vals) {

		for (int i = 0; i < s.getColumns().size(); i++) {
			String v = s.getColumns().get(i);
			if (v.contains(field)) {
				if (i < 0)
					return null;

				return vals.get(i);
			}
		}
		return null;
	}

	private Json getJsonValue(Series s, int row)
	{
		if (logger.isDebugEnabled()) {
			logger.debug("Return values size {}", s.getValues().size());
			s.getValues().forEach((v) -> {
				logger.debug("Recieved values.size {} row {} value {}", row, v);
			});
		}

		Object obj = getValue(LONG_VALUE, s, 0);
		if (obj != null)
			return Json.make(Math.round((Double) obj));
		obj = getValue(NULL_VALUE, s, 0);
		if (obj != null && Boolean.valueOf((String)obj))
			return Json.nil();

		obj = getValue(DOUBLE_VALUE, s, 0);
		if (obj != null)
			return Json.make(obj);

		obj = getValue(STR_VALUE, s, 0);
		if (obj != null) {
			if (obj.equals("true")) {
				return Json.make(true);
			}
			if (obj.equals("false")) {
				return Json.make(false);
			}
			if (obj.toString().trim().startsWith("[") && obj.toString().endsWith("]")) {
				return Json.read(obj.toString());
			}

			return Json.make(obj);

		}
		return Json.nil();

	}

	private Json getJsonValues(Series s, int row)
	{
		Json arrayOfValues = Json.array();

		Json node = Json.object();

		if (logger.isDebugEnabled())
			logger.debug("getJsonValues : Return values size {} skey {}", s.getValues().size(), s.getTags().get(skey));


		s.getValues().forEach((v) -> {

			if (logger.isDebugEnabled())
			 logger.debug("getJsonValues : Recieved value row {} value {}", row, v);

			Object ts = getValueEx("time", s, v);
			if (ts != null) {
				// make predictable 3 digit nano ISO format
				ts = Util.getIsoTimeString(DateTime.parse((String)ts, ISODateTimeFormat.dateTimeParser()).getMillis());
//				node.set(timestamp, Json.make(ts));
			}

			Object obj = getValueEx(LONG_VALUE, s,v);
			if (obj != null)
				arrayOfValues.add(Json.object(ts,Math.round((Double) obj)));

			obj = getValueEx(NULL_VALUE, s, v);
			if (obj != null && Boolean.valueOf((String)obj))
				arrayOfValues.add(Json.object(ts,Json.nil()));

			obj = getValueEx(DOUBLE_VALUE, s, v);
			if (obj != null)
				arrayOfValues.add(Json.object(ts, obj));

			obj = getValueEx(STR_VALUE, s, v);
			if (obj != null) {
				if (obj.equals("true")) {
					arrayOfValues.add(Json.object(ts,"true"));
				}
				if (obj.equals("false")) {
					arrayOfValues.add(Json.object(ts, "false"));
				}
				if (obj.toString().trim().startsWith("[") && obj.toString().endsWith("]")) {
					arrayOfValues.add(Json.object(ts, Json.read(obj.toString())));
				}

				arrayOfValues.add(Json.object(ts,obj));
			}
		});

		return arrayOfValues;

	}

	private Json getJsonValues(Series s)
	{
		Json singleSeries = Json.array();
		Json tags = Json.array();
		Json arrayOfValues = Json.array();
		Json tagSet = Json.object();

		if (logger.isDebugEnabled())
			logger.debug("getJsonValues : Return values size {} skey {}", s.getValues().size(), s.getTags().get(skey));

		s.getTags().forEach((k,v) -> {
			if (forwardConversionMap.containsKey(k))
				tagSet.set(forwardConversionMap.get(k),v);
			else {
				tagSet.set(k,v);
				logger.error("Tag {} has not been converted. Missing short value",  k);
			}	
		});
		
		singleSeries.add(Json.object(TAGS, tagSet));
		
		s.getValues().forEach((v) -> {

			Json singleValue = Json.object();

			if (logger.isDebugEnabled())
			 logger.debug("getJsonValues : Recieved value  value {}", v);

			Object ts = getValueEx("time", s, v);
			if (ts != null) {
				long millis = new DateTime( ts ).getMillis();
				// make predictable 3 digit nano ISO format
				ts = Util.getIsoTimeString(DateTime.parse((String)ts, ISODateTimeFormat.dateTimeParser()).getMillis());
				singleValue.set("ts",millis);
			}

			Object obj = getValueEx(LONG_VALUE, s, v);
			if (obj != null)
				singleValue.set(LONG_VALUE_SHORT,Math.round((Double) obj));

			obj = getValueEx(NULL_VALUE, s, v);
			if (obj != null && Boolean.valueOf((String)obj))
				singleValue.set(NULL_VALUE_SHORT,Json.nil());

			obj = getValueEx(DOUBLE_VALUE, s, v);
			if (obj != null)
				singleValue.set(DOUBLE_VALUE_SHORT, obj);

			obj = getValueEx(STR_VALUE, s, v);
			if (obj != null) {
				if (obj.equals("true")) {
					singleValue.set(STR_VALUE_SHORT,"true");
				}
				if (obj.equals("false")) {
					singleValue.set(STR_VALUE_SHORT, "false");
				}
				if (obj.toString().trim().startsWith("[") && obj.toString().endsWith("]")) {
					singleValue.set(STR_VALUE_SHORT, Json.read(obj.toString()));
				}
			}
			arrayOfValues.add(singleValue);
		});
		singleSeries.add(Json.object(VALUES, arrayOfValues));
		return Json.object(SERIES,singleSeries);
	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#save(java.util.NavigableMap)
	 */
	@Override
	public void save(NavigableMap<String, Json> map) {
		if (logger.isDebugEnabled())
			logger.debug("Save map:  {}" ,map);
		for(Entry<String, Json> e: map.entrySet()){
			save(e.getKey(), e.getValue());
		}
		influxDB.flush();
	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#save(java.lang.String, mjson.Json, mjson.Json)
	 */
	@Override
	public void save(String k, Json v) {
//		logger.getLevel();
		if (logger.isDebugEnabled())
			logger.debug("Save json:  {}={}" , k , v);
		//avoid _attr
		if(k.contains("._attr")){
			return;
		}

		String srcRef = (v.isObject() && v.has(sourceRef) ? v.at(sourceRef).asString() : StringUtils.substringAfter(k,dot+values+dot));
		if(StringUtils.isBlank(srcRef))srcRef="self";
		long tStamp = (v.isObject() && v.has(timestamp) ? Util.getMillisFromIsoTime(v.at(timestamp).asString())
				: System.currentTimeMillis());
		save(k,v,srcRef, tStamp);
	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#save(java.lang.String, mjson.Json, java.lang.String, long, mjson.Json)
	 */
	@Override
	public void save(String k, Json v, String srcRef ,long tStamp) {
		if (v.isPrimitive()|| v.isBoolean()) {

			if (logger.isDebugEnabled())logger.debug("Save primitive:  {}={}", k, v);
			saveData(k, srcRef, tStamp, v.getValue());
			return;
		}
		if (v.isNull()) {

			if (logger.isDebugEnabled())logger.debug("Save null: {}={}",k , v);
			saveData(k, srcRef, tStamp, null);
			return;
		}
		if (v.isArray()) {

			if (logger.isDebugEnabled())logger.debug("Save array: {}={}", k , v);
			saveData(k, srcRef, tStamp, v);
			return;
		}
		if (v.has(sentence)) {
			saveData(k + dot + sentence, srcRef, tStamp, v.at(sentence));
		}
		if (v.has(meta)) {
			for (Entry<String, Json> i : v.at(meta).asJsonMap().entrySet()) {

				if (logger.isDebugEnabled())logger.debug("Save meta: {}={}",()->i.getKey(), ()->i.getValue());
				saveData(k + dot + meta + dot + i.getKey(), srcRef, tStamp, i.getValue());
			}
		}

		if (v.has(values)) {

			for (Entry<String, Json> i : v.at(values).asJsonMap().entrySet()) {

				if (logger.isDebugEnabled())logger.debug("Save values: {}={}",()->i.getKey() ,()-> i.getValue());
				String sRef = StringUtils.substringBefore(i.getKey(),dot+value);
				Json vs = i.getValue();
				long ts = (vs.isObject() && vs.has(timestamp) ? Util.getMillisFromIsoTime(vs.at(timestamp).asString())
						: tStamp);
				save(k,i.getValue(),sRef, ts);

			}
		}

		if (v.has(value)&& v.at(value).isObject()) {
			for (Entry<String, Json> i : v.at(value).asJsonMap().entrySet()) {

				if (logger.isDebugEnabled())logger.debug("Save value object: {}={}" , ()->i.getKey(),()->i.getValue());
				saveData(k + dot + value + dot + i.getKey(), srcRef, tStamp, i.getValue());
			}
			return;
		}


		if (logger.isDebugEnabled())logger.debug("Save value: {} : {}", k, v);
		saveData(k + dot + value, srcRef, tStamp, v.at(value));

		return;
	}

	private void addAtPath(Json parent, String path, Json val) {
		String[] pathArray = StringUtils.split(path, ".");
		Json node = parent;
		for (int x = 0; x < pathArray.length; x++) {
			// add last
			if (x == (pathArray.length - 1)) {

				if (logger.isDebugEnabled())logger.debug("finish: {}",pathArray[x]);
				node.set(pathArray[x], val);
				break;
			}
			// get next node
			Json next = Util.getJson(node,pathArray[x], null);

		}

	}


	private void extractPrimaryValue(Json parent, Series s, String subKey, Json val, boolean useValue) {
		String srcref = s.getTags().get("sourceRef");
		if (logger.isDebugEnabled())logger.debug("extractPrimaryValue: {}:{}",s, srcref);

		Json node = parent;
		Object ts = getValue("time", s, 0);
		if (ts != null) {
			// make predictable 3 digit nano ISO format
			ts = Util.getIsoTimeString(DateTime.parse((String)ts, ISODateTimeFormat.dateTimeParser()).getMillis());
			node.set(timestamp, Json.make(ts));
		}

		if (srcref != null) {
			node.set(sourceRef, Json.make(srcref));
		}

		if(useValue){
			// check if its an object value
			if (StringUtils.isNotBlank(subKey)) {
				Json valJson = Util.getJson(parent,value, null );
				valJson.set(subKey, val);
			}
			else {
				node.set(value, val);
			}
			//if we have a 'values' key, and its primary, copy back into value{}
			//if(parent.has(values)){
//			Util.getJson(parent, values);
//				Json pValues = Json.object(value,parent.at(value).dup(),timestamp,parent.at(timestamp).dup());
//				parent.at(values).set(parent.at(sourceRef).asString(),pValues);
			//}
		}else{
			// check if its an object value
			if (StringUtils.isNotBlank(subKey)) {
				parent.set(subKey, val);
			} else {
				node.set(value, val);
			}
			//if we have a 'values' key, and its primary, copy back into value{}
//			if(parent.has(values)){
//				Json pValues = parent.dup();
//				parent.delAt(values);
//				parent.delAt(sourceRef);
//				parent.at(values).set(parent.at(sourceRef).asString(),pValues);
//			}
		}
		if (logger.isDebugEnabled())logger.debug("extractPrimaryValueObj: {}",parent);
	}


	@SuppressWarnings("unchecked")
	@Deprecated
	// TODO remove
	private void extractPrimaryValues(Json parent, Series s, String subKey, Json val, boolean useValue) {
		String srcref = s.getTags().get("sourceRef");
		if (logger.isDebugEnabled()) {
			logger.debug("extractPrimaryValues: {}:{}",s, srcref);
		}

		Json node = parent;
		Json outResults = Json.array();

		String _uid=s.getTags().get(uuid);
		String _skey=StringUtils.substringBefore(s.getTags().get(skey), "."+values);

		if (logger.isDebugEnabled()) {
			logger.debug("extractPrimaryValues: {}:{}",_skey, _uid);
		}


		final int index = 0;
		// Json array is a List of HashMaps
		val.asList().forEach((v) -> {
			if (logger.isDebugEnabled())
			logger.debug("get the timestamp/value {}",v);

			Map<String, Object> _map = (Map<String, Object>)v;

			node.set(CONTEXT, _uid);

			_map.forEach((ts, _value) -> {
				Json _arrayValue = Json.array();
				Json _node=Json.object();
				Json _subNode=Json.object();
//				if (srcref != null)
				_node.set(sourceRef, srcref != null ? srcref : "");

				_node.set(timestamp, ts);

				_subNode.set(PATH, _skey);

				if (useValue) {
					// check if its an object value
					if (StringUtils.isNotBlank(subKey)) {
						Json valJson = Util.getJson(parent, value, null);
						valJson.set(subKey, _value);
					}
					else {
						_subNode.set(value, _value);
					}
					// if we have a 'values' key, and its primary, copy back
					// into value{}
					// if(parent.has(values)){
					// Util.getJson(parent, values);
					// Json pValues =
					// Json.object(value,parent.at(value).dup(),timestamp,parent.at(timestamp).dup());
					// parent.at(values).set(parent.at(sourceRef).asString(),pValues);
					// }
				}
				else {
					// check if its an object value
					if (StringUtils.isNotBlank(subKey)) {
						parent.set(subKey, _value);
					}
					else {
						_subNode.set(value, _value);
					}
					// if we have a 'values' key, and its primary, copy back
					// into value{}
					// if(parent.has(values)){
					// Json pValues = parent.dup();
					// parent.delAt(values);
					// parent.delAt(sourceRef);
					// parent.at(values).set(parent.at(sourceRef).asString(),pValues);
					// }
				}
				_arrayValue.add(_subNode);
				_node.set(values, _arrayValue);
				outResults.add(_node);
			});
		});

		node.set(UPDATES, outResults);

		if (logger.isDebugEnabled())
			logger.debug("extractPrimaryValuesObj: {}",parent);
	}

	@SuppressWarnings("unchecked")
	private void extractPrimaryValuesEx(Json parent, Series s, String subKey, Json val, boolean useValue) {
		Json node = parent;

		Json  data = node.at(values);

		if (data == null) {
			data = Json.array();
			node.set(values, data);
		}

		Json outResults = data;

		String srcref = s.getTags().get("sourceRef");
		String _uid=s.getTags().get(uuid);
		String _skey=StringUtils.substringBefore(s.getTags().get(skey), "."+values);

		if (logger.isDebugEnabled()) {
			logger.debug("extractPrimaryValues: {}  {}",_skey, _uid);
			logger.debug("extractPrimaryValues: {} {}",srcref, s);
			logger.debug("extractPrimaryValues: subkey : {}",subKey);
		}


		// Json array is a List of HashMaps
		val.asList().forEach((v) -> {
			if (logger.isDebugEnabled())
				logger.debug("get the timestamp/value {}",v);

			Map<String, Object> _map = (Map<String, Object>)v;
			node.set(CONTEXT, _uid);

//			_map.forEach((ts, _value) -> {
			for (String ts : _map.keySet()) {
				Object _value = _map.get(ts);
				Json _subNode=Json.object();
				_subNode.set(sourceRef, srcref != null ? srcref : "");
				_subNode.set(PATH, _skey);
				_subNode.set(timestamp, ts);


				if (useValue) {
					// check if its an object value
					if (StringUtils.isNotBlank(subKey)) {
						Json _j  = null;
						Json valJson = Util.getJson(outResults, _skey, ts);  //return entire subnode if any
						if (valJson == null) {
							valJson=_subNode;
							_j = Json.object();
							valJson.set(value, _j);
							_j.set(subKey, _value);
//							System.out.println("valJson="+valJson);
						}
						else {
							_j = valJson.at(value);
							if (_j == null)  {
								_j = Json.object();
								valJson.set(value, _j);
							}
							_j.set(subKey, _value);
//							System.out.println("valJson_1="+valJson);
							continue;
						}
					}
					else {
						_subNode.set(value, _value);
					}
					// if we have a 'values' key, and its primary, copy back
					// into value{}
					// if(parent.has(values)){
					// Util.getJson(parent, values);
					// Json pValues =
					// Json.object(value,parent.at(value).dup(),timestamp,parent.at(timestamp).dup());
					// parent.at(values).set(parent.at(sourceRef).asString(),pValues);
					// }
				}
				else {
					// check if its an object value
					if (StringUtils.isNotBlank(subKey)) {
						parent.set(subKey, _value);
					}
					else {
						_subNode.set(value, _value);
					}
					// if we have a 'values' key, and its primary, copy back
					// into value{}
					// if(parent.has(values)){
					// Json pValues = parent.dup();
					// parent.delAt(values);
					// parent.delAt(sourceRef);
					// parent.at(values).set(parent.at(sourceRef).asString(),pValues);
					// }
				}
				outResults.add(_subNode);
			}
		});

		if (logger.isDebugEnabled())
			logger.debug("extractPrimaryValuesObj: {}",parent);
	}

	private void extractValue(Json parent, Series s, String subkey, Json val, boolean useValue) {
		String srcref = s.getTags().get("sourceRef");
		if (logger.isDebugEnabled())logger.debug("extractValue: {}:{}",s, srcref);
		Json node = parent;

//		if (StringUtils.isNotBlank(srcref)) {
//			node = Util.getJson(parent,values );
//			node.set(srcref,Json.object());
//			node=node.at(srcref);
//		}

		Object ts = getValue("time", s, 0);
		if (ts != null) {
			// make predictable 3 digit nano ISO format
			ts = Util.getIsoTimeString(DateTime.parse((String)ts, ISODateTimeFormat.dateTimeParser()).getMillis());
			node.set(timestamp, Json.make(ts));
		}

		// true for vessels, eg any node with .values.
		if(useValue){
			if (logger.isDebugEnabled())logger.debug("extractValue: {}:{}",parent.getParentKey(), val);
			if (StringUtils.isNotBlank(subkey)) {
				Json valJson = Util.getJson(parent,value, null );
				valJson.set(subkey, val);
				if (logger.isDebugEnabled())logger.debug("extractValue with subkey: {}",node);
			}
			else {
				node.set(value, val);
				if (logger.isDebugEnabled())logger.debug("extractValue without subkey: {}",node);
			}
//			if (StringUtils.isNotBlank(srcref)) {
//				String skey = s.getTags().get("skey");
//				//if we have a 'value' copy it into values.srcRef.{} too
//				if (logger.isDebugEnabled())logger.debug("extractValue skey: {}",skey);
//				if(!skey.contains(dot+values+dot) && parent.has(value)){
//					Json pValues = Json.object(value,parent.at(value),timestamp,parent.at(timestamp));
//					Util.getJson(parent,values );
//					parent.at(values).set(srcref,pValues);
//				}
//			}
		}else{
			//for source
			if (StringUtils.isNotBlank(subkey)) {
				Json valJson = Util.getJson(parent,value, null );
				valJson.set(subkey, val);
			} else {
				node.set(value, val);
			}
//			if (StringUtils.isNotBlank(srcref)) {
//				//if we have a 'value' copy it into values.srcRef.{} too
//				if(parent.has(value)){
//					Json pValues = Json.object(value,parent.at(value),timestamp,parent.at(timestamp));
//					parent.at(values).set(parent.at(sourceRef).asString(),pValues);
//				}
//			}
		}

		if (logger.isDebugEnabled())logger.debug("extractValue, parent: {}",parent);
	}


	protected void saveData(String key, String sourceRef, long millis, Object val) {
			if(!allowWrite) {
				String clock = null;
				try {
					clock = Config.getConfigProperty(ConfigConstants.CLOCK_SOURCE);
				}catch (Exception e) {
					logger.error(e.getLocalizedMessage());
				}
				if(clock!=null && clock.equals("system")) {
					if (logger.isInfoEnabled())
						logger.info("write enabled for {} : {}",()->val.getClass().getSimpleName(),()->key);

					setWrite(true);
				}else {
//					if (logger.isInfoEnabled())
						logger.error("write not enabled for {} : {} ({})",()->key, ()->val, ()->val.getClass().getSimpleName());
					return;
				}
			}
			if(val!=null)
				if (logger.isDebugEnabled())logger.debug("save {} : {}",()->val.getClass().getSimpleName(),()->key);
			else{
				if (logger.isDebugEnabled())logger.debug("save {} : {}",()->null,()->key);
			}

			if(self_str.equals(key))
				return;
			if(version.equals(key))
				return;

			String[] path = StringUtils.split(key, '.');
			String field = getFieldType(val);
			Builder point = null;
			switch (path[0]) {
			case resources:
				point = Point.measurement(path[0]).time(millis, TimeUnit.MILLISECONDS)
						.tag("sourceRef", sourceRef)
						.tag("uuid", path[1])
						.tag(InfluxDbService.PRIMARY_VALUE, isPrimary(key,sourceRef).toString())
						.tag("skey", String.join(".", ArrayUtils.subarray(path, 1, path.length)));
				influxDB.write(addPoint(point, field, val));
				break;
			case sources:
				point = Point.measurement(path[0]).time(millis, TimeUnit.MILLISECONDS)
						.tag("sourceRef", path[1])
						.tag("skey", String.join(".", ArrayUtils.subarray(path, 1, path.length)));
				influxDB.write(addPoint(point, field, val));
				break;
			case CONFIG:
				point = Point.measurement(path[0]).time(millis, TimeUnit.MILLISECONDS)
						.tag("uuid", path[1])
						.tag("skey", String.join(".", ArrayUtils.subarray(path, 1, path.length)));
				influxDB.write(addPoint(point, field, val));
				//also update the config map
				Config.setProperty(String.join(".", path), Json.make(val));
				break;
			case vessels:
				writeToInflux(path, millis, key, sourceRef, field, val);
				break;
			case aircraft:
				writeToInflux(path, millis, key, sourceRef, field, val);
				break;
			case sar:
				writeToInflux(path, millis, key, sourceRef, field, val);
				break;
			case aton:
				writeToInflux(path, millis, key, sourceRef, field, val);
				break;
			default:
				break;
			}


	}

	private void writeToInflux(String[] path, long millis, String key, String sourceRef, String field,  Object val) {
		Boolean primary = isPrimary(key,sourceRef);
		Builder point = Point.measurement(path[0]).time(millis, TimeUnit.MILLISECONDS)
				.tag("sourceRef", sourceRef)
				.tag("uuid", path[1])
				.tag(InfluxDbService.PRIMARY_VALUE, primary.toString())
				.tag("skey", String.join(".", ArrayUtils.subarray(path, 2, path.length)));
		influxDB.write(addPoint(point, field, val));

	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#loadPrimary()
	 */
	@Override
	public void loadPrimary(){
		primaryMap.clear();
		logger.info("Adding primaryMap");
		QueryResult result = influxDB.query(new Query("select * from vessels where primary='true' group by skey,primary,uuid,sourceRef order by time desc limit 1",dbName));
		if(result==null || result.getResults()==null)
			return ;

		result.getResults().forEach((r)-> {
			if(logger.isTraceEnabled())
				logger.trace(r);
			if(r==null||r.getSeries()==null)
				return;
			r.getSeries().forEach((s)->{
					if(logger.isTraceEnabled())
						logger.trace(s);

					if(s==null)
						return;

					Map<String, String> tagMap = s.getTags();
					String key = s.getName()+dot+tagMap.get("uuid")+dot+StringUtils.substringBeforeLast(tagMap.get("skey"),dot+value);
					primaryMap.put(StringUtils.substringBefore(key,dot+values+dot), tagMap.get("sourceRef"));
					if(logger.isTraceEnabled())
						logger.trace("Primary map: {}={}",key,tagMap.get("sourceRef"));
				});
		});
	}
	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#isPrimary(java.lang.String, java.lang.String)
	 */
	@Override
	public Boolean isPrimary(String key, String sourceRef) {
		//truncate the .values. part of the key
		String mapRef = primaryMap.get(StringUtils.substringBefore(key,dot+values+dot));

		if(mapRef==null){
			//no result = primary
			setPrimary(key, sourceRef);
			if (logger.isDebugEnabled())logger.debug("isPrimary: {}={} : {}",key,sourceRef, true);
			return true;
		}
		if(StringUtils.equals(sourceRef, mapRef)){
			if (logger.isDebugEnabled())logger.debug("isPrimary: {}={} : {}, {}",key,sourceRef, mapRef, true);
			return true;
		}
		if (logger.isDebugEnabled())logger.debug("isPrimary: {}={} : {}, {}",key,sourceRef, mapRef, false);
		return false;
	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#setPrimary(java.lang.String, java.lang.String)
	 */
	@Override
	public Boolean setPrimary(String key, String sourceRef) {
		if (logger.isDebugEnabled())logger.debug("setPrimary: {}={}",key,sourceRef);
		return !StringUtils.equals(sourceRef, primaryMap.put(StringUtils.substringBefore(key,dot+values+dot),sourceRef));
	}

	private Point addPoint(Builder point, String field, Object jsonValue) {
		Object value = null;
		if (logger.isDebugEnabled())logger.debug("addPoint: {} : {}",field,jsonValue);
		if(jsonValue==null)return point.addField(field,true).build();
		if(jsonValue instanceof Json){
			if(((Json)jsonValue).isString()){
				value=((Json)jsonValue).asString();
			}else if(((Json)jsonValue).isNull()){
				value=true;
			}else if(((Json)jsonValue).isArray()){
				value=((Json)jsonValue).toString();
			}else{
				value=((Json)jsonValue).getValue();
			}
		}else{
			value=jsonValue;
		}
		if(value instanceof Boolean)return point.addField(field,((Boolean)value).toString()).build();
		if(value instanceof Double)return point.addField(field,(Double)value).build();
		if(value instanceof Float)return point.addField(field,(Double)value).build();
		if(value instanceof BigDecimal)return point.addField(field,((BigDecimal)value).doubleValue()).build();
		if(value instanceof Long)return point.addField(field,(Long)value).build();
		if(value instanceof Integer)return point.addField(field,((Integer)value).longValue()).build();
		if(value instanceof BigInteger)return point.addField(field,((BigInteger)value).longValue()).build();
		if(value instanceof String)return point.addField(field,(String)value).build();
		if (logger.isDebugEnabled())logger.debug("addPoint: unknown type: {} : {}",field,value);
		return null;
	}

	private String getFieldType(Object value) {

		if(value==null){
			if (logger.isDebugEnabled())logger.debug("getFieldType: {} : {}",value,value);
			return NULL_VALUE;
		}
		if (logger.isDebugEnabled())logger.debug("getFieldType: {} : {}",value.getClass().getName(),value);
		if(value instanceof Json){
			if(((Json)value).isNull())return NULL_VALUE;
			if(((Json)value).isArray())return STR_VALUE;
			value=((Json)value).getValue();
		}
		if(value instanceof Double)return DOUBLE_VALUE;
		if(value instanceof BigDecimal)return DOUBLE_VALUE;
		if(value instanceof Long)return LONG_VALUE;
		if(value instanceof BigInteger)return LONG_VALUE;
		if(value instanceof Integer)return LONG_VALUE;
		if(value instanceof String)return STR_VALUE;
		if(value instanceof Boolean)return STR_VALUE;

		if (logger.isDebugEnabled())logger.debug("getFieldType:unknown type: {} : {}",value.getClass().getName(),value);
		return null;
	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.artemis.service.TDBService#close()
	 */
	@Override
	public void close() {
		influxDB.close();
	}

	public InfluxDB getInfluxDB() {
		return influxDB;
	}

	public static String getDbName() {
		return dbName;
	}

	public static void setDbName(String dbName) {
		InfluxDbService.dbName = dbName;
	}
	
	@Override
	public long produce(Object _obj) throws Exception
	{
		return 0;
		
	}

	@Override
	public long consume() throws Exception
	{
		return 0;
		
	}
}

/**
 *
 * @author gdavydov
 * Use case
 * 1. ?fromTime=XXX&ToTime=YYYY[&timeIncrement=3m] (default 30 min)
 * 2. ?fromTime=XXX&timePeriod=2h|3d|4m[&timeIncrement=3m] (default 30 min) (toTime=fromTime+timePeriod)
 * 3. ?FromTime=XXXX[&timeIncrement=3m] (default 30 min)  (toTime=now)
 * 4. ?timePeriod=2h|3d|4m[&timeIncrementl=3m] (default 30 min) (in this case fromTime will be now-DEFAULT_TIMEPERIOD, toTime=now
 *
 *
 * TODO : add static aggregate function mapping table like speed - average, position - null, temperature - average, etc
 * sum, avg, mean, first, last
 *
 * IF toTime is omitted then default value is now
 *
 */
class QueryBuilder
{

	private static Logger logger = LogManager.getLogger(InfluxDbService.class);

	private boolean trivialQuery=false;
	private String timePeriod =DEFAULT_TIMEPERIOD;
	private String toTime="now";
	private String fromTime=FROMTIME;
	private String timeResolution=DEFAULT_RESOLUTION;
	private String sortOrder="asc";
	private String aggregateFunction="mean";
	private String limit="limit 1";
	private static final String nowRegs = "now";
	private static final Pattern nowPattern = Pattern.compile(nowRegs, Pattern.CASE_INSENSITIVE);


	/**
	 * SELECT mean(*) FROM "vessels" where skey=~/navigation.courseOverGroundTrue/ AND uuid =~/80a3bcf0-d1a5-467e-9cd9-35c1760bb2d3/
	 * and time >= 1534569852000ms and time <= 1534785852000ms
	 * GROUP BY uuid, skey, time(30m) fill(null)
	 * @param table
	 * @param queryParams
	 * @return
	 */
	public String build(String table, Map<String, String> queryParams)
	{
		if (logger.isDebugEnabled())
			logger.debug("recived params fromTime={}, toTime={}, timeperiod={}",queryParams.get(FROMTIME), queryParams.get(TOTIME), queryParams.get(TIMEPERIOD));

		if (queryParams.get(TIMEPERIOD) != null && queryParams.get(TOTIME) != null || queryParams.get(FROMTIME) != null) {
			limit="";
			timePeriod = ((timePeriod = queryParams.get(TIMEPERIOD)) == null) ? DEFAULT_TIMEPERIOD : timePeriod;
			toTime = ((toTime = queryParams.get(TOTIME)) == null) ? "now()" : toTime;
			fromTime = ((fromTime = queryParams.get(FROMTIME)) == null) ? toTime+"-"+timePeriod : fromTime;
			timeResolution = ((timeResolution = queryParams.get(RESOLUTION)) == null) ? DEFAULT_RESOLUTION : timeResolution;
			sortOrder = ((sortOrder = queryParams.get(SORT_ORDER)) == null) ? "asc" : sortOrder;

			if (logger.isDebugEnabled()) {
				logger.debug("fromTime={}",fromTime);
				logger.debug("toTime={}",toTime);
				logger.debug("timeperiod={}",timePeriod);
				logger.debug("timeSlice={}, ",timeResolution);
				logger.debug("sortOrder={}, ",sortOrder);
			}
			trivialQuery = false;
		}
		else
			trivialQuery = true;

		StringBuilder builder = new StringBuilder("select ");

		if (!trivialQuery) {
			builder.append(aggregateFunction).append("(*)");
			return buildNonTrivialQuery (builder, table,queryParams);
		}
		builder.append("*");
		return buildTrivialQuery (builder, table,queryParams);
}

	public String build(String queryStr, String table, Map<String, String> queryParams)
	{
		if (logger.isDebugEnabled())
			logger.debug("recived params fromTime={}, toTime={}, timeperiod={}",queryParams.get(FROMTIME), queryParams.get(TOTIME), queryParams.get(TIMEPERIOD));

		if (queryParams.get(TIMEPERIOD) != null && queryParams.get(TOTIME) != null || queryParams.get(FROMTIME) != null) {
			limit="";

			timePeriod = ((timePeriod = queryParams.get(TIMEPERIOD)) == null) ? DEFAULT_TIMEPERIOD : timePeriod;
			toTime = ((toTime = queryParams.get(TOTIME)) == null) ? "now()" : toTime;
			fromTime = ((fromTime = queryParams.get(FROMTIME)) == null) ? toTime+"-"+timePeriod : fromTime;
			timeResolution = ((timeResolution = queryParams.get(RESOLUTION)) == null) ? DEFAULT_RESOLUTION : timeResolution;
			sortOrder = ((sortOrder = queryParams.get(SORT_ORDER)) == null) ? "asc" : sortOrder;

			if (logger.isDebugEnabled()) {
				logger.debug("fromTime={}",fromTime);
				logger.debug("toTime={}",toTime);
				logger.debug("timeperiod={}",timePeriod);
				logger.debug("timeSlice={}, ",timeResolution);
				logger.debug("sortOrder={}, ",sortOrder);
			}
			trivialQuery = false;
		}
		else
			trivialQuery = true;

		StringBuilder builder = new StringBuilder(queryStr);

		if (!trivialQuery) {
			return buildNonTrivialQuery (builder, table,queryParams);
		}
		return buildTrivialQuery (builder, table,queryParams);
	}

	private String buildNonTrivialQuery(StringBuilder builder, String table, Map<String, String> queryParams)
	{
		if (logger.isDebugEnabled())
			logger.debug("Will build non-trivial query");

		builder.append("from ")
			.append(table)
			.append(getWhereString(queryParams))
			.append(" group by uuid,skey,primary,sourceRef,strValue,nullValue,")
			.append("time(").append(timeResolution).append(")")
			.append(" order by time ")
			.append (sortOrder);
//			.append(" fill(null)");

		return builder.toString();
	}

	/**
	 * Builds trivial query without any time constraints
	 *
	 * @param table
	 * @param queryParams
	 * @param limit - how many records should be returned.
	 * @return
	 */
	private String buildTrivialQuery(StringBuilder builder, String table, Map<String, String> queryParams)
	{
		if (logger.isDebugEnabled())
			logger.debug("Will buuld trivial query");

		builder.append(" from ").append(table)
			.append(getWhereString(queryParams))
			.append(" group by uuid,skey,primary,sourceRef order by time ")
			.append(" desc ")
//			.append(" asc ")
			.append("limit 1");

		return builder.toString();
	}

	private String getWhereString(Map<String, String> query)
	{
		StringBuilder builder = new StringBuilder();
		if (query == null || query.size() == 0)
			return "";
		String joiner = " where ";
		if (query.get(uuid) != null) {
			builder.append(joiner);
			builder.append(uuid)
			.append("=~/")
			.append(query.get(uuid))
			.append("/");
			joiner = " and ";
		}
		if (query.get(skey) != null) {
			builder.append(joiner)
			.append(skey)
			.append("=~/")
			.append(query.get(skey))
			.append("/");
			joiner = " and ";
		}

		if (!trivialQuery) {
			builder.append(joiner).append(timeConditionCalulator(query));
		}
		return builder.toString();
	}

	/**
	 * @param query
	 * @return time condition string
	 */
	private String timeConditionCalulator (Map<String, String> query)
	{
		StringBuilder builder = new StringBuilder();

		builder.append(TIME)
	       .append(" >= ")
	       .append(timeMillisOfSeconds(fromTime))
	       .append(" and ")
	       .append(TIME)
	       .append("<=")
	       .append(timeMillisOfSeconds(toTime));

		return builder.toString();
	}

	/**
	 * Converts valid time stamp into milly seconds.
	 * NOTE: time start with now - will not be converted an returned as -is
	 * @param inTime
	 * @return
	 */
	private String timeMillisOfSeconds(String inTime)
	{
		String outTime = inTime;
		
		if (inTime.equalsIgnoreCase("now"))
			return outTime;
		
		Matcher matcher = nowPattern.matcher(outTime);

		if (!matcher.find()) {
			try {
				if(outTime.contains("T")) {
					outTime=String.valueOf(Util.getMillisFromIsoTime(outTime));
				}
				else if (outTime.contains("Z")) {
					outTime=outTime.replace(" ", "T");
					outTime=String.valueOf(Util.getMillisFromIsoTime(outTime));
				}
				else{
					outTime=String.valueOf(Util.getMillisFromIsoTime(outTime));
				}
				return outTime+"ms";
			}
			catch(Exception e) {
				logger.error("Exception {}", e.getLocalizedMessage());
				e.printStackTrace();
			}
		}
		return outTime;
	}
}
