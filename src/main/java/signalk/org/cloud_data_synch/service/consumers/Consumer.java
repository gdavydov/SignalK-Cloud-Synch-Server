package signalk.org.cloud_data_synch.service.consumers;

public interface Consumer<T> 
{
	default long consume() throws Exception {
		throw new Exception("consume Not implemented");
	}
}
