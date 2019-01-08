/**
 * 
 */
package signalk.org.cloud_data_synch.utils;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import signalk.org.cloud_data_synch.service.CloudSynchService;
import signalk.org.cloud_data_synch.utils.ConfigUtils.SynchConfigObject;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Consts;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.HttpEntity;

/**
 * @author gdavydov@gmail.com
 * https://stackoverflow.com/questions/49901102/equivalent-of-curl-command-in-java *
 */
public class HttpUtils
{

	public static int PING_ERROR = -99;
	private static Logger logger = LogManager.getLogger(CloudSynchService.class);

	public static long ping(String host, String _to)
	{
		long ret = 0;
		try {
			int timeOut = 10;
			if (_to.contains("m")) { // minutes
				_to = _to.replace("m", "");
				timeOut = Integer.parseInt(_to) * 60 * 1000;
			}
			else if (_to.contains("s")) { // seconds
				_to = _to.replace("s", "");
				timeOut = Integer.parseInt(_to) * 1000;
			}

			long _ret = ping1(host, timeOut);
			ret = _ret > ret ? _ret : ret;
		}
		catch (IOException e) {
			logger.error("Exception in ping to {} Error: {}", host, e.getLocalizedMessage());
			return PING_ERROR;
		}
		return ret;
	}

	private static long ping1(String ipAddress, int timeOut) throws IOException
	{
		// int _timeOut=500;

		InetAddress inet;
		try {
			inet = InetAddress.getByName(ipAddress);
		}
		catch (UnknownHostException e) {
			try {
				logger.info("Can not resolve host {} by name. Will try by address", ipAddress);
				inet = InetAddress.getByAddress(ipAddress.getBytes());
			}
			catch (UnknownHostException ex) {
				logger.error("Can not resolve host {} in any way", ipAddress);
				throw new IOException(ex.getLocalizedMessage());
			}
		}

		long startTm = (new Date()).getTime();

		if (logger.isDebugEnabled())
			logger.debug("Sending Ping Request to {}. Current Time:{}", ipAddress, String.valueOf(startTm));

		if (inet.isReachable(timeOut)) {

			long reachedIn = (new Date()).getTime() - startTm;

			if (logger.isInfoEnabled())
				logger.debug("Host is reachable - {} time - {}ms", ipAddress, reachedIn);

			return reachedIn;

		}
		else {
			if (logger.isInfoEnabled())
				logger.debug("Host is NOT reachable - {}", ipAddress);

			return ConfigUtils.DEFAULT_SAT_RESPONCE_TIME;
		}
	}

	
	public String postCurl(String url, Map<String, String> postParams, Map<String, String> header, String cookies)  throws ClientProtocolException, IOException 
	{
		return sendPost(url, postParams, header, cookies);
	}
	
	public String simpleSendPost(String url, Map<String, String> postParams, 
	        Map<String, String> header, String cookies) throws ClientProtocolException, IOException 
	{
	    HttpPost httpPost = new HttpPost(url);
	    List<NameValuePair> formParams = new ArrayList<NameValuePair>();
/*
	    HttpClientBuilder clientBuilder = HttpClients..createDefault();
	    CloseableHttpClient httpclient = clientBuilder.build();
*/
	    CloseableHttpClient httpclient = HttpClients.createDefault();
	    if (header != null) {
	    	header.forEach((k,v) ->  {
	            httpPost.addHeader(k, v);
	    	});
	    }	
	    httpPost.setHeader("Cookie", cookies);

	    if (postParams != null) {
	    	postParams.forEach((k,v) ->  {
	    		formParams.add(new BasicNameValuePair(k, v));
		    });
	    }

	    UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8);
	    httpPost.setEntity(formEntity);

	    CloseableHttpResponse httpResponse = httpclient.execute(httpPost);

	    HttpEntity entity = httpResponse.getEntity();
	    
	    String pageContent = null;
		if (entity != null) {
	        pageContent = EntityUtils.toString(entity);
	    }

	    return pageContent;
	}
		
	/**
	 * 
	 * @param host
	 * @param user
	 * @param passwd
	 * @param fileName
	 * @param appType - "application/json", "application/zip", 
	 * @param contentType - ContentType.DEFAULT_BINARY, ContentType.DEFAULT_TEXT, etc.
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Map<Integer, String> simplePostFile(String host, String user, String passwd, String fileName, String appType, ContentType contentType) throws ClientProtocolException, IOException 
	{
			    CloseableHttpClient client = HttpClients.createDefault();
			    HttpPost httpPost = new HttpPost(fileName);
			 
			    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			    builder.addTextBody("username", user);
			    builder.addTextBody("password", passwd);
			    InputStream inputStream = new FileInputStream(fileName);
//			    builder.addBinaryBody("upstream", inputStream, ContentType.create("application/zip"), fileName);
//			    builder.addBinaryBody("upfile", file, ContentType.DEFAULT_BINARY, textFileName);
//			    builder.addTextBody("text", message, ContentType.DEFAULT_TEXT);
			    builder.addBinaryBody("upstream", inputStream, ContentType.create(appType), fileName);
			    
			    HttpEntity multipart = builder.build();
			    httpPost.setEntity(multipart);
			 
			    CloseableHttpResponse httpResponse = client.execute(httpPost);
			    
			    client.close();

			    HttpEntity entity = httpResponse.getEntity();
			    
			    String pageContent = null;
				if (entity != null) {
			        pageContent = EntityUtils.toString(entity);
			    }
				
				Map<Integer, String> ret = new HashMap<Integer, String>();
				ret.put(httpResponse.getStatusLine().getStatusCode(), pageContent);
				return ret;
	}
	/**
	 * General post with all bells and whistles
	 * @param url
	 * @param postParams
	 * @param header
	 * @param cookies
	 * @param user
	 * @param passwd
	 * @param fileName
	 * @param appType
	 * @param contentType
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public String sendPost(String url, Map<String, String> postParams, 
	        Map<String, String> header, String cookies, String user, String passwd, String fileName, String appType, ContentType contentType) throws ClientProtocolException, IOException 
	{
	    HttpPost httpPost = new HttpPost(url);
	    List<NameValuePair> formParams = new ArrayList<NameValuePair>();

	    CloseableHttpClient httpclient = HttpClients.createDefault();
	    if (header != null) {
	    	header.forEach((k,v) ->  {
	            httpPost.addHeader(k, v);
	    	});
	    }	
	    httpPost.setHeader("Cookie", cookies);

	    if (postParams != null) {
	    	postParams.forEach((k,v) ->  {
	    		formParams.add(new BasicNameValuePair(k, v));
		    });
	    }

	    UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8);
	    httpPost.setEntity(formEntity);
	    
	    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
	    builder.addTextBody("username", user);
	    builder.addTextBody("password", passwd);
	    InputStream inputStream = new FileInputStream(fileName);
//	    builder.addBinaryBody("upstream", inputStream, ContentType.create("application/zip"), fileName);
//	    builder.addBinaryBody("upfile", file, ContentType.DEFAULT_BINARY, textFileName);
//	    builder.addTextBody("text", message, ContentType.DEFAULT_TEXT);
	    builder.addBinaryBody("upstream", inputStream, ContentType.create(appType), fileName);
	    
	    HttpEntity multipart = builder.build();
	    httpPost.setEntity(multipart);

	    CloseableHttpResponse httpResponse = httpclient.execute(httpPost);

	    HttpEntity entity = httpResponse.getEntity();
	    
	    String pageContent = null;
		if (entity != null) {
	        pageContent = EntityUtils.toString(entity);
	    }

	    return pageContent;
	}
}