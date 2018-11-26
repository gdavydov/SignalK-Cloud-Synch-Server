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
package nz.co.fortytwo.signalk.artemis.intercept;

import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import signalk.org.cloud_data_synch.service.SignalkMapConvertor;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.Util;


/**
 * Processes full format into individual messages.
 * 
 * @author robert
 * 
 */
public class FullMsgInterceptor extends BaseInterceptor implements Interceptor {

	private static Logger logger = LogManager.getLogger(FullMsgInterceptor.class);
	
	public FullMsgInterceptor() {
		super();
	}

	@Override
	public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
		if(isResponse(packet))
			return true;
		if (packet instanceof SessionSendMessage) {
			SessionSendMessage realPacket = (SessionSendMessage) packet;

			ICoreMessage message = realPacket.getMessage();
			
			if(!Config.JSON_FULL.equals(message.getStringProperty(Config.AMQ_CONTENT_TYPE)))return true;
			
			Json node = Util.readBodyBuffer(message);
			
			// deal with full format
			if (isFullFormat(node)) {
				if (logger.isDebugEnabled())
					logger.debug("processing full {} ", node);
				try {
					NavigableMap<String, Json> map = new ConcurrentSkipListMap<>();
					SignalkMapConvertor.parseFull(node,map,"");
					saveMap(map);
					return true;
				} catch (Exception e) {
					logger.error(e,e);
				}
				
			}
		
		}
		return true;
	}
}
