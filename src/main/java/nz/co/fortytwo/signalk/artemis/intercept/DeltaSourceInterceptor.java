package nz.co.fortytwo.signalk.artemis.intercept;

import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONFIG;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.GET;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PUT;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.UPDATES;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.Util;

/*
*
* Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
* Web: www.42.co.nz
* Email: robert@42.co.nz
* Author: R T Huitema
*
* This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
* WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/

/**
 * Converts SignalK delta format 'source' entry to 'sourceRef' 
 * 
 * @author robert
 * 
 */

public class DeltaSourceInterceptor extends BaseInterceptor implements Interceptor {

	private static Logger logger = LogManager.getLogger(DeltaSourceInterceptor.class);
	
	/**
	 * Reads Delta format JSON and inserts in the influxdb. Does nothing if json
	 * is not an update, and returns the original message
	 * 
	 * @param node
	 * @return
	 */

	@Override
	public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
		if(isResponse(packet))return true;
		if (packet instanceof SessionSendMessage) {
			SessionSendMessage realPacket = (SessionSendMessage) packet;

			ICoreMessage message = realPacket.getMessage();
			
			if (!Config.JSON_DELTA.equals(message.getStringProperty(Config.AMQ_CONTENT_TYPE)))
				return true;
			
			String srcBus = message.getStringProperty(Config.MSG_SRC_BUS);
			String msgSrcType = message.getStringProperty(Config.MSG_SRC_TYPE);
			Json node = Util.readBodyBuffer(message);
			
			if (logger.isDebugEnabled())
				logger.debug("Delta msg: {}", node.toString());

			// deal with diff format
			if (isDelta(node) && !node.has(GET)) {
				try {
					if (logger.isDebugEnabled())
						logger.debug("Converting source in delta: {}", node.toString());
					if(node.has(UPDATES)){
						node.at(UPDATES).asJsonList().forEach((j) -> {
							convertSource(j,srcBus, msgSrcType);
						});
					}
					if(node.has(PUT)){
						node.at(PUT).asJsonList().forEach((j) -> {
							convertSource(j,srcBus, msgSrcType);
						});
					}
					if(node.has(CONFIG)){
						node.at(CONFIG).asJsonList().forEach((j) -> {
							convertSource(j,srcBus, msgSrcType);
						});
					}
					message.getBodyBuffer().clear();
					message.getBodyBuffer().writeString(node.toString());
					return true;
				} catch (Exception e) {
					logger.error(e, e);
					throw new ActiveMQException(ActiveMQExceptionType.INTERNAL_ERROR, e.getMessage(), e);
				}

			}
		}
		return true;

	}

	
	
	
	

}
