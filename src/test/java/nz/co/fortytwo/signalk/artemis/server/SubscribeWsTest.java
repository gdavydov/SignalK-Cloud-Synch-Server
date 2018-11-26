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
package nz.co.fortytwo.signalk.artemis.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.CONFIG;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.FORMAT_DELTA;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.POLICY_IDEAL;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.SIGNALK_API;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.SIGNALK_DISCOVERY;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.SIGNALK_WS;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.electrical;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.nav;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.restUrl;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.sources;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.tanks;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.version;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.vessels;
import static signalk.org.cloud_data_synch.utils.SignalKConstants.websocketUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import static org.asynchttpclient.Dsl.*;
import mjson.Json;
import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigConstants;
import signalk.org.cloud_data_synch.utils.Util;


public class SubscribeWsTest extends BaseServerTest {

	private static Logger logger = LogManager.getLogger(SubscribeWsTest.class);
	String jsonDiff = null;
	private int restPort = 8080;
	private String wsPort = "8080";

	public SubscribeWsTest() {

		try (ClientSession session = Util.getLocalhostClientSession("admin", "admin");
				ClientProducer producer = session.createProducer();	
				){
			sendMessage(session, producer, "$GPRMC,144629.20,A,5156.91111,N,00434.80385,E,0.295,,011113,,,A*78");
		} catch (ActiveMQException e) {
			logger.error(e,e);
		} catch (Exception e) {
			logger.error(e,e);
		}
	}

	
	@Test
	public void shouldGetAllData() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {
		
			String resp = getUrlAsString(c,SIGNALK_API,restPort);
			 Json respJson = Json.read(resp);
			 assertTrue(resp, respJson.has(vessels));
			 
		}
	}
	@Test
	public void shouldGetWsPublicUrls() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {
			Json json = getUrlAsJson(c,SIGNALK_DISCOVERY, restPort);
			logger.debug("shouldGetWsPublicUrls: {}", json);
			assertEquals("ws://localhost:" + wsPort + SIGNALK_WS,
					json.at("endpoints").at("v1").at(websocketUrl).asString());
			assertEquals("http://localhost:" + restPort + SIGNALK_API+"/",
					json.at("endpoints").at("v1").at(restUrl).asString());
			assertEquals("1.0.0",
					json.at("endpoints").at("v1").at(version).asString());

			String resp = getUrlAsString(c,SIGNALK_API + "/self", restPort);
			
			 assertEquals("\""+Config.getConfigProperty(ConfigConstants.UUID)+"\"", resp);
			
			 resp = getUrlAsString(c,SIGNALK_API,restPort);
			 Json respJson = Json.read(resp);
			 assertTrue(resp, respJson.has(vessels));
			
			 resp = getUrlAsString(c,SIGNALK_API+"/",restPort);
			 respJson = Json.read(resp);
			 assertTrue(resp, respJson.has(vessels));
			
			 resp = getUrlAsString(c,SIGNALK_API+"/"+vessels,restPort);
			 respJson = Json.read(resp);
			 assertTrue(resp, respJson.has(Config.getConfigProperty(ConfigConstants.UUID)));
			
			 resp = getUrlAsString(c,SIGNALK_API+"/"+vessels+"/",restPort);
			 respJson = Json.read(resp);
			 assertTrue(resp, respJson.has(Config.getConfigProperty(ConfigConstants.UUID)));
			
			 resp = getUrlAsString(c,SIGNALK_API+"/ves",restPort);
			 respJson = Json.read(resp);
			 assertTrue(resp, respJson.at(vessels).has(Config.getConfigProperty(ConfigConstants.UUID)));
			
			 resp = getUrlAsString(c,SIGNALK_API+"/vssls",restPort);
			 assertEquals("{}",resp);
			
			resp = getUrlAsString(c,SIGNALK_API + "/" + vessels + "/", restPort);
			respJson = Json.read(resp);
			assertTrue(respJson.has(Config.getConfigProperty(ConfigConstants.UUID)));

			resp = getUrlAsString(c,SIGNALK_API + "/" + vessels + "/self/nav", restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.at(nav).has("courseOverGroundTrue"));

			resp = getUrlAsString(c,SIGNALK_API + "/" + vessels + "/self/nav*", restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.at(nav).has("courseOverGroundTrue"));

			//logger.debug(getUrlAsString(c,SIGNALK_API + "/", "admin","admin",restPort));
			resp = getUrlAsString(c,SIGNALK_API + "/" + sources, "admin","admin",restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.has("NMEA0183"));

			resp = getUrlAsString(c,SIGNALK_API + "/" + sources + "/", "admin","admin",restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.has("NMEA0183"));

			resp = getUrlAsString(c,SIGNALK_API + "/sou","admin","admin", restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.at(sources).has("NMEA0183"));

			resp = getUrlAsString(c,SIGNALK_API + "/" + CONFIG, "admin", "admin", restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.has(CONFIG));

			resp = getUrlAsString(c,SIGNALK_API + "/" + CONFIG + "/", "admin", "admin", restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.has(CONFIG));
			// assertTrue( resp.contains("\"ports\": [\""));
			resp = getUrlAsString(c,SIGNALK_API + "/con", "admin", "admin", restPort);
			respJson = Json.read(resp);
			assertTrue(resp, respJson.has(CONFIG));
		}
			//no login, authenticationInterceptor sends back 301 redirect so no result
		try (final AsyncHttpClient c = asyncHttpClient();) {	
			String resp = getUrlAsString(c,SIGNALK_API + "/" + CONFIG, restPort);
			assertEquals("",resp);

			resp = getUrlAsString(c,SIGNALK_API + "/" + CONFIG + "/", restPort);
			assertEquals("",resp);
			
			resp = getUrlAsString(c,SIGNALK_API + "/con", restPort);
			Json respJson = Json.read(resp);
			
			assertTrue(resp, respJson.has("self"));
			assertFalse(resp, respJson.has("config"));
			// special case, we have /vessels/self/, which should be found by
			// this..
			resp = getUrlAsString(c,SIGNALK_API + "/" + vessels + "/urn", restPort);
			 respJson = Json.read(resp);
			logger.debug(resp);
			assertTrue(resp, respJson.has("urn:mrn:signalk:uuid:5da2f032-fc33-43f0-bc24-935bf55a17d1"));
		}
	}

	@Test
	public void shouldGetApiData() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {
			// get auth
			Response r2 = c.prepareGet("http://localhost:" + restPort + SIGNALK_API + "/vessels")
					.setHeader("Authorization", "Basic YWRtaW46YWRtaW4=").execute().get();
			Json json = Json.read(r2.getResponseBody());
			logger.debug("Endpoint json:" + json);
			// urn:mrn:imo:mmsi:245139000
			//
			assertTrue(json.has(Config.getConfigProperty(ConfigConstants.UUID)));
		}

	}

	@Test
	public void shouldGetApiSelfData() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {

			Response r2 = c.prepareGet("http://localhost:" + restPort + SIGNALK_API + "/self")
					.setHeader("Authorization", "Basic YWRtaW46YWRtaW4=").execute().get();
			String json = r2.getResponseBody();
			logger.debug("Endpoint json:" + json);
			assertEquals("\""+Config.getConfigProperty(ConfigConstants.UUID)+"\"", json);
		}
	}

	@Test
	public void shouldGetApiUUIDData() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {

			String resp = getUrlAsString(c, SIGNALK_API + "/vessels/"+Config.getConfigProperty(ConfigConstants.UUID)+"/uuid","admin","admin",restPort);
			logger.debug("Endpoint json:" + resp);
			assertEquals("\""+Config.getConfigProperty(ConfigConstants.UUID)+"\"", resp);
		}
	}

	@Test
	public void shouldGetApiSubset() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {

			// get a sessionid
			// Response r1 =
			// c.prepareGet("http://localhost:"+restPort+SIGNALK_AUTH+"/demo/pass").execute().get();
			// assertEquals(200, r1.getStatusCode());
			Response r2 = c
					.prepareGet("http://localhost:" + restPort + SIGNALK_API + "/vessels/urn:mrn:imo:mmsi:123456789")
					.setHeader("Authorization", "Basic YWRtaW46YWRtaW4=").execute().get();
			Json json = Json.read(r2.getResponseBody());
			logger.debug("Endpoint json:" + json);
			// assertEquals("ws://localhost:"+wsPort+SIGNALK_WS,
			// json.at("endpoints").at("v1").at(websocketUrl).asString());
		}
	}

	@Test
	public void shouldGetApiForSelf() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {

			// get a sessionid
			// Response r1 =
			// c.prepareGet("http://localhost:"+restPort+SIGNALK_AUTH+"/demo/pass").execute().get();
			// assertEquals(200, r1.getStatusCode());
			Response r2 = c.prepareGet("http://localhost:" + restPort + SIGNALK_API + "/vessels/self")
					.execute().get();
			Json json = Json.read(r2.getResponseBody());
			logger.debug("Endpoint json:" + json);
			assertFalse(json.has("NMEA2000"));
			//assertTrue(json.has(env));
			assertTrue(json.has(nav));
			//assertTrue(json.has(electrical));
			//assertTrue(json.has(tanks));
		}
	}

	@Test
	public void shouldGetApiForSources() throws Exception {

		try (final AsyncHttpClient c = asyncHttpClient();) {
			String rslt = getUrlAsString(c, SIGNALK_API + "/sources","admin","admin",restPort);
			
			Json json = Json.read(rslt);
			logger.debug("Endpoint json:" + json);
			assertTrue(json.has("NMEA0183"));
			assertFalse(json.has(nav));
			assertFalse(json.has(electrical));
			assertFalse(json.has(tanks));
		}
	}

	@Test
	public void shouldGetSubscribeWsResponse() throws Exception {
		final List<String> received = new ArrayList<String>();
		final CountDownLatch latch = new CountDownLatch(5);
		try (final AsyncHttpClient c = asyncHttpClient();) {

			String restUrl = "ws://localhost:" + restPort + SIGNALK_WS;
			logger.debug("Open websocket at: " + restUrl);		   
			WebSocket websocket = c.prepareGet(restUrl).setCookies(getCookies("admin", "admin"))
					.execute(new WebSocketUpgradeHandler.Builder().build()).get();

			websocket.addWebSocketListener(new WebSocketListener() {

				@Override
				public void onBinaryFrame(byte[] message, boolean b, int i) {
					logger.info("received BYTES --> " + String.valueOf(message));
				}

				@Override
				public void onTextFrame(String message, boolean b, int i) {
					logger.info("received --> " + message);
					received.add(message);
				}

				@Override
				public void onError(Throwable t) {
					logger.error(t);
				}

				@Override
				public void onOpen(WebSocket websocket) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onClose(WebSocket websocket, int code, String reason) {
					// TODO Auto-generated method stub
					
				}


			});
			// subscribe
			String subscribeMsg = getSubscriptionJson("vessels.self","navigation",1000,1000,FORMAT_DELTA,POLICY_IDEAL).toString();
			websocket.sendTextFrame(subscribeMsg, true, 0);
			
			logger.debug("Sent subscribe = " + subscribeMsg);
	
			latch.await(10, TimeUnit.SECONDS);
			logger.debug("Completed receive ");

			websocket.sendCloseFrame();
			
			assertTrue(received.size() > 1);
			// assertTrue(latch3.await(15, TimeUnit.SECONDS));
			String fullMsg = null;
			for (String msg : received) {
				logger.debug("Received msg = " + msg);
				if (msg.contains("\"updates\":[{\"") && msg.contains("\"path\":\"navigation")) {
					fullMsg = msg;
				}
			}
			assertTrue(received.size() > 1);

			// Json sk =
			// Json.read("{\"context\":\"vessels."+SignalKConstants.self+".navigation\",\"updates\":[{\"values\":[{\"path\":\"courseOverGroundTrue\",\"value\":3.0176},{\"path\":\"speedOverGround\",\"value\":3.85}],\"source\":{\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"device\":\"/dev/actisense\",\"src\":\"115\",\"pgn\":\"128267\"}}]}");
			// Json sk =
			// Json.read("{\"context\":\"vessels.motu\",\"updates\":[{\"values\":[{\"path\":\"navigation.courseOverGroundTrue\",\"value\":3.0176},{\"path\":\"navigation.speedOverGround\",\"value\":3.85}],\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"source\":{\"device\":\"/dev/actisense\",\"src\":\"115\",\"pgn\":\"128267\"}}]}");
			assertNotNull(fullMsg);
			assertTrue(fullMsg.contains("\"context\":\"vessels." + Config.getConfigProperty(ConfigConstants.UUID) + "\""));
			assertTrue(fullMsg.contains("\"path\":\"navigation.courseOverGroundTrue\""));
			assertTrue(fullMsg.contains("\"value\":"));
			assertTrue(fullMsg.contains("\"updates\":[{"));

		}
	}

}
