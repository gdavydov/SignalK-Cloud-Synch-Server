package signalk.org.cloud_data_synch.service;

import java.lang.Exception;

public interface Producer<T>
{
	default long produce(Object obj) throws Exception {
		throw new Exception("Produce is not implemented");
	}
}
