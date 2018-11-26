package nz.co.fortytwo.signalk.artemis.intercept;

import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import mjson.Json;
import signalk.org.cloud_data_synch.service.InfluxDbService;
import signalk.org.cloud_data_synch.service.SignalkMapConvertor;
import signalk.org.cloud_data_synch.service.TDBService;
import signalk.org.cloud_data_synch.service.TDBServiceFactory;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigConstants;
import signalk.org.cloud_data_synch.utils.SignalKConstants;
import signalk.org.cloud_data_synch.utils.Util;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.MessagePacket;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;

public class BaseInterceptor {
	private static Logger logger = LogManager.getLogger(BaseInterceptor.class);
//	protected static TDBService influx = new InfluxDbService();
	protected static TDBService tdbService = null;

	protected static ClientSession txSession;
	protected static ClientProducer producer;

	public BaseInterceptor ()
	{
		try {
			tdbService = TDBServiceFactory.getService(Config.dbName, Config.dbType);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error("FAILED: Can not connect to {} type {}.", Config.dbName, Config.dbType);
			logger.error("  {}", e.getLocalizedMessage());
			logger.error(e.getStackTrace());
		}
	}
	protected void init() {
		try{

			txSession = Util.getVmSession(Config.getConfigProperty(Config.ADMIN_USER),
					Config.getConfigProperty(Config.ADMIN_PWD));
			producer = txSession.createProducer();

			txSession.start();

			if(Config.getConfigProperty(ConfigConstants.CLOCK_SOURCE).equals("system")) {
				tdbService.setWrite(true);
			}
		}
		catch(Exception e){
			logger.error(e.getLocalizedMessage(),e);
		}
	}

	protected boolean isDelta(Json node){
		return Util.isDelta(node);
	}

	protected boolean isSubscribe(Json node){
		return Util.isSubscribe(node);
	}

	protected boolean isFullFormat(Json node) {
		return Util.isFullFormat(node);
	}

	protected boolean isN2k(Json node) {
		return Util.isN2k(node);
	}

	public void convertSource(Json j, String srcBus, String msgType) {
		Json srcJson = Util.convertSourceToRef(j,srcBus,msgType);
		saveSource(srcJson);
	}
	public void convertFullSrcToRef(Json node, String srcBus, String msgSrcType) {
		if (logger.isDebugEnabled())
			logger.debug("Converting source in full: {}", node.toString());
		//recurse keys
		node.asJsonMap().forEach((k,j) -> {
				if(j.isObject() && j.has(SignalKConstants.source)) {
					convertSource(j,srcBus, msgSrcType);
				}else {
					convertFullSrcToRef(j, srcBus, msgSrcType);
				}
			});
	}
	protected void sendReply(String simpleName, String destination, String format, Json json, ServerSession s) throws Exception {
		sendReply(String.class.getSimpleName(),destination,format,null,json);
	}

	protected void  sendReply(String simpleName, String destination, String format, String correlation, Json json) throws Exception {
		if(txSession==null){
			init();
		}
		if(json==null || json.isNull())
			json=Json.object();

		ClientMessage txMsg = null;
		synchronized (txSession) {
			txMsg = txSession.createMessage(false);
		}
		//txMsg.putStringProperty(Config.JAVA_TYPE, type);
		if(correlation!=null)
			txMsg.putStringProperty(Config.AMQ_CORR_ID, correlation);
		txMsg.putStringProperty(Config.AMQ_SUB_DESTINATION, destination);
		txMsg.putBooleanProperty(Config.SK_SEND_TO_ALL, false);
		txMsg.putStringProperty(SignalKConstants.FORMAT, format);
		txMsg.putBooleanProperty(SignalKConstants.REPLY, true);
		txMsg.putStringProperty(Config.AMQ_CORR_ID, correlation);
		txMsg.setExpiration(System.currentTimeMillis()+5000);
		txMsg.getBodyBuffer().writeString(json.toString());
		if (logger.isDebugEnabled())
			logger.debug("Msg body = {}",json.toString());
		synchronized (txSession) {
			producer.send("outgoing.reply." +destination,txMsg);
		}
	}

	protected void saveMap(NavigableMap<String, Json> map) {
		tdbService.save(map);
	}

	protected void saveSource(Json srcJson) {
		if(srcJson==null)
			return;
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<>();
		SignalkMapConvertor.parseFull(srcJson,map, "");
		saveMap(map);
	}

	protected boolean isResponse(Packet packet) {
		if (packet.isResponse())
			return true;
		if (packet instanceof MessagePacket) {
			ICoreMessage message = ((MessagePacket) packet).getMessage();
			if(message.getBooleanProperty(SignalKConstants.REPLY))return true;
		}
		return false;
	}
}
