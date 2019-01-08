/**
 * 
 */
package signalk.org.cloud_data_synch.service;

import java.util.Map;

import signalk.org.cloud_data_synch.service.consumers.Consumer;
import signalk.org.cloud_data_synch.utils.ConfigUtils.SynchConfigObject;

/**
 * @author gregd
 *
 */
public class SignalKCloudSynchService implements Producer<Object>, Consumer<Object>
{
	protected SynchConfigObject synchConfigObject;
	protected Map<String, Object> producerMetadataMap;
	protected Map<String, Object> consumerMetadataMap;
	
	public void setUp(SynchConfigObject _so) 
	{
		synchConfigObject = _so;
	}

	public void setUpProducer(Map<String, Object> _map) 
	{
		producerMetadataMap = _map;
	}
	public void setUpConsumer(Map _map) 
	{
		consumerMetadataMap = _map;
	}

	public long produce(Object obj) throws Exception 
	{
		throw new Exception("Produce is not implemented");
	}

	public long consume() throws Exception 
	{
		throw new Exception("Consume is not implemented");
	}	

}
