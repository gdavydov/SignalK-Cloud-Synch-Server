package nz.co.fortytwo.signalk.artemis.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static signalk.org.cloud_data_synch.utils.Config.AMQ_CONTENT_TYPE;
import static signalk.org.cloud_data_synch.utils.Config.JSON_DELTA;
import static signalk.org.cloud_data_synch.utils.Config._0183;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONTEXT;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PATH;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.UPDATES;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.dot;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.env_depth_belowTransducer;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.nav_courseOverGroundTrue;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.nav_speedOverGround;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.self_str;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.source;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.timestamp;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.value;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.values;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.vessels;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.script.ScriptException;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import mjson.Json;
import signalk.org.cloud_data_synch.utils.Util;

public class NMEAMsgInterceptorTest extends BaseMsgInterceptorTest {
	private static Logger logger = LogManager.getLogger(NMEAMsgInterceptorTest.class);
	
    private NMEAMsgInterceptor interceptor ;// 1

    public NMEAMsgInterceptorTest() throws Exception {
    	try {
			interceptor  = new NMEAMsgInterceptor();
		} catch (FileNotFoundException | NoSuchMethodException | ScriptException e) {
			logger.error(e,e);
		}
	}
    
    @Before
    public void before(){
    	
    }
    
    @Test
	public void shouldAvoidJson() throws ActiveMQException {
    	Json json = Json.read("{\"context\":\"vessels.self\",\"updates\":[{\"values\":[{\"path\":\"propulsion.engine_1.revolutions\",\"value\":40.30333333333333}],\"source\":{\"sentence\":\"RPM\",\"talker\":\"II\",\"type\":\"NMEA0183\"},\"timestamp\":\"2018-05-14T02:43:29.224Z\"}]}");
		ClientMessage message = getClientMessage(json.toString(), JSON_DELTA, false); 
		SessionSendMessage packet = new SessionSendMessage((CoreMessage) message);

		assertTrue(interceptor.intercept(packet, null));
		ICoreMessage msg = packet.getMessage();
		assertEquals(JSON_DELTA,msg.getStringProperty(AMQ_CONTENT_TYPE));
//		{"context":"vessels.self","updates":[{"values":[{"path":"navigation.position","value":{"latitude":51.9485185,"longitude":4.580064166666666}},{"path":"navigation.courseOverGroundTrue","value":0},{"path":"navigation.speedOverGround","value":0.151761149557269},{"path":"navigation.magneticVariation","value":0},{"path":"navigation.magneticVariationAgeOfService","value":1383317189},{"path":"navigation.datetime","value":"2013-11-01T14:46:29.000Z"}],"source":{"sentence":"RMC","talker":"GP","type":"NMEA0183"},"timestamp":"2013-11-01T14:46:29.000Z"}]}"
		String content = Util.readBodyBufferToString(msg);;
		logger.debug("NMEA converted message: {}",content);
		Json after = Json.read(content);
		
		assertTrue(Util.isDelta(after));
		assertEquals(json,after);
    }
    
    @Test
	public void shouldProcessRPM() throws ActiveMQException {	
		ClientMessage message = getClientMessage("$IIRPM,E,1,2418.2,10.5,A*5F", _0183, false); 
		SessionSendMessage packet = new SessionSendMessage((CoreMessage) message);

		assertTrue(interceptor.intercept(packet, null));
		
		HashMap<String,Object> map=new HashMap<>();
		map.put("propulsion.engine_1.revolutions",40.30333333333333d); 
		checkConversion(packet,map);
		
	}
    
    @Test
	public void shouldProcessDBT() throws ActiveMQException {	
		ClientMessage message = getClientMessage("$IIDPT,4.1,0.0*45", _0183, false); 
		SessionSendMessage packet = new SessionSendMessage((CoreMessage) message);

		assertTrue(interceptor.intercept(packet, null));
		
		HashMap<String,Object> map=new HashMap<>();
		map.put(env_depth_belowTransducer,4.1d);
		checkConversion(packet,map);
		
	}
	
	@Test
	public void shouldProcessRMB() throws ActiveMQException {	
		ClientMessage message = getClientMessage("$ECRMB,A,0.000,L,001,002,4653.550,N,07115.984,W,2.505,334.205,0.000,V*04", _0183, false); 
		SessionSendMessage packet = new SessionSendMessage((CoreMessage) message);

		assertTrue(interceptor.intercept(packet, null));
		
		HashMap<String,Object> map=new HashMap<>();
		//map.put("environment.depth.belowTransducer navigation.courseRhumbline.nextPoint.value.lattitude",46.8925d);
		map.put("navigation.courseRhumbline.nextPoint.bearingTrue",5.83297762795949d);
		map.put("navigation.courseRhumbline.nextPoint.velocityMadeGood",0d);
		map.put("navigation.courseRhumbline.nextPoint.distance",4639.260003915535d);
		map.put("navigation.courseRhumbline.crossTrackError",0d);

		checkConversion(packet,map);
		
	}
	
	@Test
	public void shouldProcessRMC() throws ActiveMQException {
		ClientMessage message = getClientMessage("$GPRMC,144629.20,A,5156.91111,N,00434.80385,E,0.295,,011113,,,A*78", _0183, false); 
		SessionSendMessage packet = new SessionSendMessage((CoreMessage) message);

		assertTrue(interceptor.intercept(packet, null));
		
		HashMap<String,Object> map=new HashMap<>();
		map.put(nav_speedOverGround,0.151761149557269d);
		map.put(nav_courseOverGroundTrue,0d);
		checkConversion(packet,map);
		
	}
	private void checkConversion(SessionSendMessage packet, HashMap<String, Object> map) {
		ICoreMessage msg = packet.getMessage();
		assertEquals(JSON_DELTA,msg.getStringProperty(AMQ_CONTENT_TYPE));
//		{"context":"vessels.self","updates":[{"values":[{"path":"navigation.position","value":{"latitude":51.9485185,"longitude":4.580064166666666}},{"path":"navigation.courseOverGroundTrue","value":0},{"path":"navigation.speedOverGround","value":0.151761149557269},{"path":"navigation.magneticVariation","value":0},{"path":"navigation.magneticVariationAgeOfService","value":1383317189},{"path":"navigation.datetime","value":"2013-11-01T14:46:29.000Z"}],"source":{"sentence":"RMC","talker":"GP","type":"NMEA0183"},"timestamp":"2013-11-01T14:46:29.000Z"}]}"
		String content = Util.readBodyBufferToString(msg);;
		logger.debug("NMEA converted message: {}",content);
		Json json = Json.read(content);
		
		assertTrue(Util.isDelta(json));
		
		assertEquals(vessels+dot+self_str, json.at(CONTEXT).asString());
		
		assertTrue(json.has(UPDATES));
		Json valJson = json.at(UPDATES).asJsonList().get(0);
		List<Json> list = valJson.at(values).asJsonList();
		for(Entry<String, Object> e:map.entrySet()){
			//must have each value
			boolean found=false;
			for(Json v:list){
				assertTrue(valJson.has(timestamp));
				assertTrue(valJson.has(source));
				assertTrue(valJson.has(values));
				//logger.debug("check: {} {} ",e.getKey(),v.at(PATH).asString());
				logger.debug("map.put(\"{}\",{}d); ",v.at(PATH).asString(),v.at(value));
				if(e.getKey().equals(v.at(PATH).asString())){
					found=true;
					if(e.getValue() instanceof Double){
						assertEquals((Double)e.getValue(),v.at(value).asDouble(),0.0001);
					}
					if(e.getValue() instanceof String){
						assertEquals((String)e.getValue(),v.at(value).asString());
					}
				}
			
			}
			assertTrue(e.getKey(),found);

		}
		
	}
}
