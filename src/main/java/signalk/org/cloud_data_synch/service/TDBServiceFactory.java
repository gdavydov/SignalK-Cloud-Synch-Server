/**
 *
 */
package signalk.org.cloud_data_synch.service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import signalk.org.cloud_data_synch.utils.ConfigConstants;

/**
 * @author gdavydov
 *
 */


@FunctionalInterface
interface ExceptionHandler<T, E extends Exception> {
    void accept(T t) throws E;
}

public class TDBServiceFactory  extends SignalKCloudSynchServiceFactory
{

	private static Logger logger = LogManager.getLogger(TDBServiceFactory.class);
	private static Map<String, TDBService> dbService = new HashMap<String,  TDBService>();

	public static TDBService getService(String dbName,String dbType) throws Exception
	{
		return getService(dbName,dbType, 0);
	}
	
	public static TDBService getService(String dbName,String dbType, long timeout) throws Exception
	{
		String serviceName=null;
		Class<?> instance=null;
		TDBService service = null;

		if (dbType.equals(ConfigConstants.INFLUX_DB))
			serviceName = dbType + "Service";
		else {
			logger.error("Database {} currently is not supported", dbType);
			throw new Exception ("Database "+dbType+" currently is not supported");
		}

		if ((service = dbService.get(dbType + "-" + dbName)) != null) {
			if (logger.isDebugEnabled())
				logger.debug("Found Service for {}-{}. Will Add.",dbType,dbName);
			
			return service;			
		}

		String className=TDBService.class.getPackage().getName()+"."+serviceName;
		/**
		 * Check services tables
		 */
		if (SignalKCloudSynchServiceFactory.getServices().containsKey(className)) {
			instance = SignalKCloudSynchServiceFactory.getServices().get(className);
		}
		else {
			instance = null;
		}

		if (instance == null) {
			try {
				instance = Class.forName(className);
			}
			catch (Throwable t) {
				throw new Exception(t);
			}
		}
		
		if (! signalk.org.cloud_data_synch.service.TDBService.class.isAssignableFrom(instance)) {
			throw new Exception ("Class "+instance.getName()+" is not instance of signalk.org.cloud_data_synch.service.TDBService");
		}
		
		try {
			if (timeout == 0) {
				Method setUpMethod = instance.getMethod("setUpTDb", String.class);
				service = (TDBService) setUpMethod.invoke(null, dbName);
			}	
			else {
				Method setUpMethod = instance.getMethod("setUpTDb", String.class, Long.class);
				service = (TDBService) setUpMethod.invoke(null, dbName, timeout);
			}
			dbService.put(className + "-" + dbName, service);
			return service;			
		}
		catch (Throwable t) {
			throw new Exception(t);
		}
	}

	public static Object exceptionHandler(Exception object)
	{
		// TODO Auto-generated method stub
		return null;
	}
}
