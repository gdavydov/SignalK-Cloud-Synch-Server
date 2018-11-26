package nz.co.fortytwo.signalk.artemis.intercept;

import static signalk.org.cloud_data_synch.utils.SignalKConstants.ALL;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONFIG;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONTEXT;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.FORMAT_DELTA;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.FORMAT_FULL;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.GET;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PARAMETERS;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.PATH;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.UPDATES;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.aircraft;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.aton;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.dot;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.resources;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.sar;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.skey;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.sources;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.uuid;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.vessels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import signalk.org.cloud_data_synch.service.SignalkMapConvertor;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.SignalKConstants;
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
 * Converts SignalK GET interceptor
 *
 * @author robert
 *
 */

public class GetMsgInterceptor extends BaseInterceptor implements Interceptor {


	private static Logger logger = LogManager.getLogger(GetMsgInterceptor.class);
	private boolean fullOutput=true;


	/**
	 * Reads Delta GET message and returns the result in full format. Does nothing if json
	 * is not a GET, and returns the original message
	 *
	 * @param node
	 * @return
	 */

	@Override
	public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException
	{
		if(isResponse(packet))
			return true;

		if (packet instanceof SessionSendMessage) {
			SessionSendMessage realPacket = (SessionSendMessage) packet;

			ICoreMessage message = realPacket.getMessage();

			if (!Config.JSON_DELTA.equals(message.getStringProperty(Config.AMQ_CONTENT_TYPE)))
				return true;

			Json node = Util.readBodyBuffer(message);
			String correlation = message.getStringProperty(Config.AMQ_CORR_ID);
			String destination = message.getStringProperty(Config.AMQ_REPLY_Q);

			// deal with diff format
			if (node.has(CONTEXT) && (node.has(GET))) {
				if (logger.isDebugEnabled())
					logger.debug("GET msg: {}", node.toString());
				String ctx = node.at(CONTEXT).asString();
				String jwtToken = null;
				if(node.has(SignalKConstants.TOKEN)&&!node.at(SignalKConstants.TOKEN).isNull()) {
					jwtToken=node.at(SignalKConstants.TOKEN).asString();
				}
				String root = StringUtils.substringBefore(ctx,dot);
				root = Util.sanitizeRoot(root);


				//limit to explicit series
				if (!vessels.equals(root)
					&& !CONFIG.equals(root)
					&& !sources.equals(root)
					&& !resources.equals(root)
					&& !aircraft.equals(root)
					&& !sar.equals(root)
					&& !aton.equals(root)
					&& !ALL.equals(root)){
					try{
						sendReply(String.class.getSimpleName(),destination,FORMAT_FULL,correlation,Json.object());
						return true;
					} catch (Exception e) {
						logger.error(e, e);
						throw new ActiveMQException(ActiveMQExceptionType.INTERNAL_ERROR, e.getMessage(), e);
					}
				}
				String qUuid = StringUtils.substringAfter(ctx,dot);
				if(StringUtils.isBlank(qUuid))
					qUuid="*";
				ArrayList<String> fullPaths=new ArrayList<>();

				Map<String, String> queryMap = new HashMap<>();
				try {
					NavigableMap<String, Json> map = new ConcurrentSkipListMap<>();
					boolean parametersExsist = false;
					fullOutput=true;

					queryMap.put(uuid ,Util.regexPath(qUuid).toString());

					for(Json p: node.at(GET).asJsonList()) {
						if (p.at(PATH) != null) {
							String path = p.at(PATH).asString();
							path=Util.sanitizePath(path);
							fullPaths.add(Util.sanitizeRoot(ctx+dot+path));
							path=Util.regexPath(path).toString();
							if(StringUtils.isNotBlank(qUuid))
								queryMap.put(skey,path);
						}
						else if (p.at(PARAMETERS) != null) {
							p.at(PARAMETERS).asMap().forEach((k,v) -> {
									queryMap.put(k, String.valueOf(v));
							});
							parametersExsist = true;
							fullOutput=false;
						}
						else {
							logger.error ("Error: Get: has neither path nor paramaters key --> {}", node.at(GET).toString());
						}
					}
					switch (root) {
						case CONFIG:
							tdbService.loadConfig(map, queryMap);
							break;
						case resources:
							tdbService.loadResources(map, queryMap);
							break;
						case sources:
							tdbService.loadSources(map, queryMap);
							break;
						case vessels:
							tdbService.loadData(map, vessels, queryMap, parametersExsist);
							break;
						case aircraft:
							tdbService.loadData(map, aircraft, queryMap, parametersExsist);
							break;
						case sar:
							tdbService.loadData(map, sar, queryMap, parametersExsist);
							break;
						case aton:
							tdbService.loadData(map, aton, queryMap, parametersExsist);
							break;
						case ALL:
							tdbService.loadData(map, vessels, null, parametersExsist);
							//loadAllDataFromInflux(map,aircraft);
							//loadAllDataFromInflux(map,sar);
							//loadAllDataFromInflux(map,aton);
						default:
						}
//						if (logger.isDebugEnabled())
//							logger.debug("GET sql : {}", sql);
//					}

					if (logger.isDebugEnabled())
						logger.debug("GET  token: {}, map : {}",jwtToken, map);

					
					Json json = null;
					if (fullOutput)
						json = SignalkMapConvertor.mapToFull(map,jwtToken);
					else {
//						qUuid = StringUtils.substringAfter(ctx,dot);
						json = SignalkMapConvertor.mapToUpdatesDeltaEx(map, queryMap);
						json.set(CONTEXT, ctx);
					}
					String fullPath = StringUtils.getCommonPrefix(fullPaths.toArray(new String[]{}));
					//fullPath=StringUtils.remove(fullPath,".*");
					//fullPath=StringUtils.removeEnd(fullPath,".");

					// for REST we only send back the sub-node, so find it
					if (logger.isDebugEnabled()) {
						logger.debug("GET node(a.k.a fullPath) : {}", fullPath);
						logger.debug("GET json : {}", json);
					}

					if (StringUtils.isNotBlank(fullPath) && !root.startsWith(CONFIG) && !root.startsWith(ALL)) {
						if (fullOutput)
							json = Util.findNodeMatch(json, fullPath);
					}
					if (fullOutput)
						sendReply(map.getClass().getSimpleName(),destination,FORMAT_FULL,correlation,json);
					else
						sendReply(map.getClass().getSimpleName(),destination,FORMAT_DELTA,correlation,json);

				}
				catch (Exception e) {
					logger.error(e, e);
					throw new ActiveMQException(ActiveMQExceptionType.INTERNAL_ERROR, e.getMessage(), e);
				}
			}
		}
		return true;
	}
}
