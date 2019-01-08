/**
 *
 */
package signalk.org.cloud_data_synch.service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import signalk.org.cloud_data_synch.service.consumers.Consumer;
import signalk.org.cloud_data_synch.utils.ConfigConstants;

/**
 * @author gdavydov
 *
 */
/*
@FunctionalInterface
interface ExceptionHandler<T, E extends Exception> {
    void accept(T t) throws E;
}
*/
public class SignalKCloudSynchServiceFactory {

	private static Logger logger = LogManager.getLogger(SignalKCloudSynchServiceFactory.class);
	private static Map<String, TDBService> dbService = new HashMap<String, TDBService>();
	private Map<String, Class<Producer>>producers = new HashMap<String, Class<Producer>>();
	private Map<String, Class<Consumer>>consumers = new HashMap<String, Class<Consumer>>();
	protected static Map<String, Class<SignalKCloudSynchService>>services = new HashMap<String, Class<SignalKCloudSynchService>>();
	

	@SuppressWarnings("unchecked")
	public static Class<SignalKCloudSynchService> getConsumer(String className) throws Exception
	{
//		SignalKCloudSynchService instance = null;
		Class<SignalKCloudSynchService> instance =null;
/*
		Class<?> bb;
		instance = SignalKCloudSynchService.class.cast(bb);
*/		
		
		if (className != null && ! className.isEmpty()) {
			if (services.containsKey(className)) {
				instance = services.get(className);
				if (instance == null)  {
					instance = getServiceClass(className);
				    services.put(className,instance);
				}
			}
			else {
				instance = getServiceClass(className);
				services.put(className,instance);
			}
			
			try {
				if (logger.isDebugEnabled())
					logger.debug("Will check for consume method in {}", className );
				Method mthd = instance.getMethod("consume", null);
				return instance;
			}
			catch (Throwable t) {
				throw new Exception("Class "+className+" does nor implement Consumer Inreface. Error: "+t.getLocalizedMessage());
			}
		}
		else {
			if (logger.isWarnEnabled())
				logger.warn("Consumer is missing");
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static Class<SignalKCloudSynchService> getProducer(String className) throws Exception
	{
//		SignalKCloudSynchService instance = null;
		Class<SignalKCloudSynchService> instance =null;
		
		if (className != null && ! className.isEmpty()) {
			if (services.containsKey(className)) {
				instance = services.get(className);
				if (instance == null)  {
					instance = getServiceClass(className);
				}
			}
			else {
				instance = getServiceClass(className);
			}
			
			try {
				if (logger.isDebugEnabled())
					logger.debug("Will check for produce method in {}", className );
//				Method produce = instance.getMethod("produce", String.class);
				Method mthd = instance.getMethod("produce", Object.class);
				
				services.put(className,instance);
			    
				return instance;
			}
			catch (Throwable t) {
				throw new Exception("Class "+className+" does not implement Producer Inreface. Error: "+t.getLocalizedMessage());
			}
		}
		else {
			if (logger.isWarnEnabled())
				logger.warn("Producer is missing");
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private static Class<SignalKCloudSynchService> getServiceClass(String className) throws Exception 
	{
		try {
			if (logger.isDebugEnabled())
				logger.debug("Will instanciate service class: {}", className);
			Class<SignalKCloudSynchService> instance = (Class<SignalKCloudSynchService>) Class.forName(className);

			return instance;
		}
		catch (Throwable t) {
			throw new Exception(t);
		}
	}
	
	public static Map<String, Class<SignalKCloudSynchService>> getServices() {
		return services;
	}

	public static Class<SignalKCloudSynchService> getService(String key) throws Exception
	{
		return services.get(key);
	}

	public static Object exceptionHandler(Exception object)
	{
		// TODO Auto-generated method stub
		return null;
	}
}
