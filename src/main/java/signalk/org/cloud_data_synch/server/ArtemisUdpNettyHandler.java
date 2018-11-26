/*
 * 
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 * 
 * This file is part of the signalk-server-java project
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package signalk.org.cloud_data_synch.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import mjson.Json;
import nz.co.fortytwo.signalk.artemis.subscription.SubscriptionManagerFactory;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigConstants;
import signalk.org.cloud_data_synch.utils.Util;

@Sharable
public class ArtemisUdpNettyHandler extends SimpleChannelInboundHandler<DatagramPacket> {

	private static Logger logger = LogManager.getLogger(ArtemisUdpNettyHandler.class);
	
	private static ClientSession rxSession;
	private static ClientProducer producer;
	
	private BiMap<String, InetSocketAddress> socketList = HashBiMap.create();
	//private BiMap<String, ClientSession> sessionList = HashBiMap.create();
	private Map<String, ChannelHandlerContext> channelList = new HashMap<>();
	//private BiMap<String, ClientProducer> producerList = HashBiMap.create();
	private BiMap<String, ClientConsumer> consumerList = HashBiMap.create();

	private String outputType;
	//private ClientConsumer consumer;

	static {
		try {
			rxSession = Util.getVmSession(Config.getConfigProperty(Config.ADMIN_USER),
					Config.getConfigProperty(Config.ADMIN_PWD));
			producer = rxSession.createProducer();
			rxSession.start();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
	
	public ArtemisUdpNettyHandler(String outputType) throws Exception {
		this.outputType = outputType;
	}

	private synchronized void send(ClientMessage msg) throws ActiveMQException{
		producer.send(Config.INCOMING_RAW, msg);
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// Send greeting for a new connection.
		NioDatagramChannel udpChannel = (NioDatagramChannel) ctx.channel();
		if (logger.isDebugEnabled())
			logger.debug("channelActive: {}", udpChannel.localAddress());
		// TODO: associate the ip with a user?
		// TODO: get user login

		if (logger.isDebugEnabled())
			logger.debug("channelActive, ready: {}", ctx);

	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
		String request = packet.content().toString(CharsetUtil.UTF_8);
		if (logger.isDebugEnabled())
			logger.debug("Sender {} sent request:{}" , packet.sender() , request);
		String sessionId = packet.sender().getAddress().getHostAddress()+":"+packet.sender().getPort();//ctx.channel().id().asLongText();
		NioDatagramChannel udpChannel = (NioDatagramChannel) ctx.channel();
		String localAddress = udpChannel.localAddress().getAddress().getHostAddress();
		InetAddress remoteAddress = packet.sender().getAddress(); 
		if(rxSession==null || rxSession.isClosed()){
			ctx.close().channel().close();
			if (logger.isDebugEnabled())logger.debug("Closed channel for : {}", sessionId);
			return;
		}
		if (!socketList.inverse().containsKey(packet.sender())) {
			
			socketList.put(sessionId, packet.sender());
			if (logger.isDebugEnabled())
				logger.debug("Added Sender {}, session:{} " , packet.sender(), sessionId);
			ctx.channel()
					.writeAndFlush(new DatagramPacket(
							Unpooled.copiedBuffer(Util.getWelcomeMsg().toString() + "\r\n", CharsetUtil.UTF_8),
							packet.sender()));
			// setup consumer
			if(!consumerList.containsKey(sessionId)){
				createTemporaryQueue("outgoing.reply." + sessionId, RoutingType.ANYCAST, sessionId);
				
				ClientConsumer consumer = rxSession.createConsumer(sessionId, false);
				consumer.setMessageHandler(new MessageHandler() {
	
					@Override
					public void onMessage(ClientMessage message) {
						try {
							message.acknowledge();
							process(message);
						}catch(Exception e) {
							logger.error(e.getMessage(), e);
						}
	
					}
				});
				consumerList.put(sessionId,consumer);
			}
			if(!channelList.containsKey(sessionId)){
				channelList.put(sessionId,ctx);
			}
		}

		Map<String, Object> headers = getHeaders(sessionId, remoteAddress, localAddress);
		ClientMessage ex = null;
		synchronized (rxSession) {
			ex=rxSession.createMessage(false);
		}
		ex.getBodyBuffer().writeString(request);
		ex.putStringProperty(Config.AMQ_REPLY_Q, sessionId);
		
		for (String hdr : headers.keySet()) {
			ex.putStringProperty(hdr, headers.get(hdr).toString());
		}
		send(ex);
	}

	private synchronized void createTemporaryQueue(String string, RoutingType anycast, String session) throws ActiveMQException {
		try{
			synchronized (rxSession) {
				rxSession.createTemporaryQueue("outgoing.reply." + session, RoutingType.ANYCAST, session);
			}
		}catch (ActiveMQQueueExistsException e) {
				logger.debug(e);
		} 
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

	@Override
	public boolean acceptInboundMessage(Object msg) throws Exception {
		if (msg instanceof Json || msg instanceof String)
			return true;
		return super.acceptInboundMessage(msg);
	}

	private Map<String, Object> getHeaders(String wsSession, InetAddress remoteAddress, String localIp) throws Exception {
		
		Map<String, Object> headers = new HashMap<>();
		headers.put(Config.AMQ_SESSION_ID, wsSession);
		headers.put(Config.AMQ_CORR_ID, wsSession);
		headers.put(Config.MSG_SRC_IP, remoteAddress.getHostAddress());
		headers.put(Config.MSG_SRC_BUS, "udp." + remoteAddress.getHostAddress().replace('.', '_'));
		//TODO: fix UDP ip network source
		if (logger.isDebugEnabled())
			logger.debug("IP: local:{}, remote:{}", localIp, remoteAddress.getHostAddress());
		if (remoteAddress.isLoopbackAddress()|| remoteAddress.isAnyLocalAddress()) {
			headers.put(Config.MSG_SRC_TYPE, Config.INTERNAL_IP);
		} else {
			headers.put(Config.MSG_SRC_TYPE, Config.EXTERNAL_IP);
		}
		headers.put(ConfigConstants.OUTPUT_TYPE, outputType);
		return headers;
	}

	@Override
	protected void finalize() throws Throwable{
		channelList.clear();
		socketList.clear();
		for(Entry<String, ClientConsumer> entry:consumerList.entrySet()){
			try {
				entry.getValue().close();
			} catch (ActiveMQException e) {
				logger.error(e,e);
			}
		}
		consumerList.clear();
//		if (producer != null) {
//			try {
//				producer.close();
//			} catch (ActiveMQException e) {
//				logger.warn(e, e);
//			}
//		}
//
//		if (rxSession != null) {
//			try {
//				rxSession.close();
//			} catch (ActiveMQException e) {
//				logger.warn(e, e);
//			}
//		}
	}

	public void process(ClientMessage message) throws Exception {

		String msg = Util.readBodyBufferToString(message);
		if (logger.isDebugEnabled())
			logger.debug("UDP sending msg : {} ", msg);
		if (msg != null) {
			// get the session
			String sessionId = message.getStringProperty(Config.AMQ_SUB_DESTINATION);
			if (logger.isDebugEnabled())
				logger.debug("UDP session id: {}", sessionId);
			if (Config.SK_SEND_TO_ALL.equals(sessionId)) {
				// udp
				
					for (InetSocketAddress client : socketList.values()) {
						if (logger.isDebugEnabled())
							logger.debug("Sending udp: {}", msg);
						if(channelList.get(sessionId)==null || !channelList.get(sessionId).channel().isWritable()){
							
							//cant send, kill it
							try {
								consumerList.get(sessionId).close();
							} catch (ActiveMQException e) {
								logger.error(e.getMessage(), e);
							}
							try {
								SubscriptionManagerFactory.getInstance().removeByTempQ(sessionId);
							} catch (Exception e) {
								logger.error(e.getMessage(), e);
							}
							consumerList.remove(sessionId);
							channelList.remove(sessionId);
							socketList.remove(sessionId);
							continue;
						}
						((NioDatagramChannel) channelList.get(sessionId).channel()).writeAndFlush(
								new DatagramPacket(Unpooled.copiedBuffer(msg + "\r\n", CharsetUtil.UTF_8), client));
						if (logger.isDebugEnabled())
							logger.debug("Sent udp to {}", client);
					}
				
				
			} else {

				// udp
		
					final InetSocketAddress client = socketList.get(sessionId);
					if (logger.isDebugEnabled())
						logger.debug("Sending udp: {}", msg);
					// udpCtx.pipeline().writeAndFlush(msg+"\r\n");
					if(channelList.get(sessionId)==null || !channelList.get(sessionId).channel().isWritable()){
					
						//cant send, kill it
						try {
							consumerList.get(sessionId).close();
						} catch (ActiveMQException e) {
							logger.error(e.getMessage(), e);
						}
						try {
							SubscriptionManagerFactory.getInstance().removeByTempQ(sessionId);
						} catch (Exception e) {
							logger.error(e.getMessage(), e);
						}
						consumerList.remove(sessionId);
						channelList.remove(sessionId);
						socketList.remove(sessionId);
						return;
					}
					((NioDatagramChannel) channelList.get(sessionId).channel()).writeAndFlush(
							new DatagramPacket(Unpooled.copiedBuffer(msg + "\r\n", CharsetUtil.UTF_8), client));
					if (logger.isDebugEnabled())
						logger.debug("Sent udp for session: {}", sessionId);
			
				
			}
		}

	}
}
