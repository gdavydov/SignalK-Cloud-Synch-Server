package signalk.org.cloud_data_synch.service.producers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import signalk.org.cloud_data_synch.service.SignalKCloudSynchService;
import signalk.org.cloud_data_synch.utils.ConfigUtils;

public class scp extends SignalKCloudSynchService 
{
	protected final int JSCP_SUCCESS = 0;      //0 may for succes
	protected final int JSCP_ERROR = 1;        //1 for error
	protected final int JSCP_FATAL = 2;        //2 for fatal error
	protected final int JSCP_SOMESUCCESS = -1; //-1 may for success, otherwise who knows.
	
	private static Logger logger = LogManager.getLogger(scp.class);

	private final String useUtil = "scp";
	protected boolean wasInitialized = false;
	boolean dash_p = true;

	private String user;
	private String passwd;
	private String host;
	private String targetFolder;
	private String keyFile;
	private JSch jsch = new JSch();
	private Session session; 
	private Channel channel;
	private String commandPart1;
	
	protected void init() throws JSchException, FileNotFoundException 
	{
		user = (String)producerMetadataMap.get(ConfigUtils.USER);
		passwd = (String)producerMetadataMap.get(ConfigUtils.PASSWORD);

		host = (String)producerMetadataMap.get(ConfigUtils.HOST);
		targetFolder = (String)ConfigUtils.getConsumerDataFolder();
		keyFile  = (String)producerMetadataMap.get(ConfigUtils.KEY_FILE);
		
		if (keyFile == null)
			throw new FileNotFoundException(this.getClass().getCanonicalName()+ " "+ConfigUtils.KEY_FILE+" is missing");
		
		jsch = new JSch();
		
		jsch.setLogger(new JSCPLogger(logger));
		
		jsch.addIdentity((String)producerMetadataMap.get(ConfigUtils.KEY_FILE));

		session = jsch.getSession(user, host, 22);
/*
//		session.setPassword("password");

		Properties config = new Properties();
		config.put("StrictHostKeyChecking", "no");
		session.setConfig(config);
*/		
		session.setConfig("StrictHostKeyChecking", "no");
		try {
			if (! session.isConnected())
				session.connect(300000);
		}
		catch (JSchException e) {
			logger.error(" {}", e.getLocalizedMessage());
		}
		
		if (SystemUtils.IS_OS_MAC_OSX || SystemUtils.IS_OS_LINUX) {
			commandPart1 = "scp " + (dash_p ? "-p" : "") + " -t ";
		}	
		else if (SystemUtils.IS_OS_WINDOWS) {
			commandPart1 = "scp " + (dash_p ? "-p" : "") + " -t ";
		}
		wasInitialized = true;
	}
	
	
	@Override
	public long produce(Object file) throws Exception 
	{
/*		
		http://www.svlada.com/ssh-public-key-authentication/
		https://stackoverflow.com/questions/199624/scp-via-java
		
		https://stackoverflow.com/questions/17148948/com-jcraft-jsch-jschexception-auth-fail-with-working-passwords
		
		
pscp -pw spring12 C:\Users\gd85376\workspace\SignalK\signalk-java\SignalK-Cloud-Synch-Server\data_dumps\1545679047282.influxdb.dd dmadmin@lad1pdaht2001:/export/home/dmadmin/data_dumps/a.tmp

//1545679047282.influxdb.dd | 1271 kB | 1271.4 kB/s | ETA: 00:00:00 | 100%
		
*/	
		int jscpError = 0;
		String sourceFile=(String)file;
	    File _lfile = new File(sourceFile);

	    String targetFile=ConfigUtils.getConsumerDataFolder()+"/"+_lfile.getName();
	    long sendBytes=0;
	    
	    if (logger.isDebugEnabled())
			logger.debug("Will SCP {} to {}@{}:{}", file, producerMetadataMap.get(ConfigUtils.USER),producerMetadataMap.get(ConfigUtils.HOST), ConfigUtils.getConsumerDataFolder());

		if (!wasInitialized)
			init();
		
		FileInputStream fis = null;
		try {
		    
			if (logger.isDebugEnabled()) {
				logger.debug("{}. About to connect to {}",useUtil, producerMetadataMap.get(ConfigUtils.HOST));
			}

			if (! session.isConnected())
				session.connect();

			// exec 'scp -t rfile' remotely
			String command = commandPart1 + targetFile;
			if (logger.isDebugEnabled())
				logger.debug("Will execute command: {}", command);
			
			if (channel == null || !channel.isConnected())
				channel = session.openChannel("exec");

			if (logger.isDebugEnabled()) {
				logger.debug("Transmission start");
			}
			
			Long transmitionStart = (new Date()).getTime();

			((ChannelExec) channel).setCommand(command);

			// get I/O streams for remote scp
			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();

			if (!channel.isConnected())
				channel.connect();

			try {
				jscpError = acknoledgment(in);
			}
			catch(Exception e) {
				logger.error("{}. file {} => {}@{}:{}. ERROR:{}", useUtil, sourceFile, producerMetadataMap.get(ConfigUtils.USER),producerMetadataMap.get(ConfigUtils.HOST), ConfigUtils.getConsumerDataFolder(), e.getLocalizedMessage());
			}

			if (dash_p) {
				command = "T " + (_lfile.lastModified() / 1000) + " 0";
				// The access time should be sent here,
				// but it is not accessible with JavaAPI ;-<
				command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
				out.write(command.getBytes());
				out.flush();
				try {
					jscpError = acknoledgment(in);
				}
				catch(Exception e) {
					logger.error("{}. file {} => {}@{}:{}. ERROR:{}", useUtil, sourceFile, producerMetadataMap.get(ConfigUtils.USER),producerMetadataMap.get(ConfigUtils.HOST), ConfigUtils.getConsumerDataFolder(), e.getLocalizedMessage());
					throw e;
				}
			}

			// send "C0644 filesize filename", where filename should not include
			// '/'
			long filesize = _lfile.length();
			command = "C0644 " + filesize + " ";
			if (sourceFile.lastIndexOf('/') > 0) {
				command += sourceFile.substring(sourceFile.lastIndexOf('/') + 1);
			}
			else {
				command += sourceFile;
			}
			command += "\n";
			out.write(command.getBytes());
			out.flush();
			try {
				jscpError = acknoledgment(in);
			}
			catch(Exception e) {
				logger.error("{}. file {} => {}@{}:{}. ERROR:{}", useUtil, sourceFile, producerMetadataMap.get(ConfigUtils.USER),producerMetadataMap.get(ConfigUtils.HOST), ConfigUtils.getConsumerDataFolder(), e.getLocalizedMessage());
				throw e;
			}

			// send a content of lfile
			fis = new FileInputStream(sourceFile);
			byte[] buf = new byte[2048];
			while (true) {
				int len = fis.read(buf, 0, buf.length);
				if (len <= 0)
					break;
				
				out.write(buf, 0, len); // out.flush();
				sendBytes+=len;
			}
			fis.close();
			fis = null;
			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();
			sendBytes+=1;

			if (logger.isInfoEnabled()) {
				logger.info("{}. File {} was sent to the server {} in {}ms. Bytes written: {} ",useUtil, sourceFile, 
						                            producerMetadataMap.get(ConfigUtils.HOST), 
						                            (new Date()).getTime()-transmitionStart, sendBytes+1);
			}
			
			try {
				jscpError = acknoledgment(in);
			}
			catch(Exception e) {
				logger.error("{}. file {} => {}@{}:{}. ERROR:{}", useUtil, sourceFile, producerMetadataMap.get(ConfigUtils.USER),producerMetadataMap.get(ConfigUtils.HOST), ConfigUtils.getConsumerDataFolder(), e.getLocalizedMessage());
				throw e;
			}
			finally {
				try {
					out.close();
					in.close();
				}
				catch(Exception ee) {}
			}
//			channel.disconnect();
//			session.disconnect();
			logger.info("*** SCP END");
			return sendBytes; 
		}
		catch (Exception e) {
			try {
				if (fis != null)
					fis.close();
			}
			catch (Exception ee) {
			}
			logger.error("{}. file {} => {}@{}:{}. ERROR:{}", useUtil, sourceFile, producerMetadataMap.get(ConfigUtils.USER),producerMetadataMap.get(ConfigUtils.HOST), ConfigUtils.getConsumerDataFolder(), e.getLocalizedMessage());
			throw e;
		}
	}
	
	protected int acknoledgment(InputStream in) throws JSchException, IOException {
		int b = in.read();
		// b may be 0 for success,
		// 1 for error,
		// 2 for fatal error,
		// -1
		if (b == 0)
			return b;
		if (b == -1)
			return b;

		if (b == 1 || b == 2) {
			StringBuffer sb = new StringBuffer();
			int c;
			do {
				c = in.read();
				sb.append((char) c);
			}
			while (c != '\n');

			
			if (b == 1) { // error
				throw new JSchException(sb.toString()+". Severity="+b);
			}
			if (b == 2) { // fatal error
				throw new JSchException(sb.toString()+". Severity="+b);
			}
		}
		return b;
	}

/*
	@Override 
	public void consume() throws Exception 
	{
		
	}
*/		
}

class JSCPLogger implements com.jcraft.jsch.Logger {

	static java.util.Hashtable name=new java.util.Hashtable();
	static org.apache.logging.log4j.Logger logger = null;
    static{
        name.put(new Integer(DEBUG), "DEBUG: ");
        name.put(new Integer(INFO), "INFO: ");
        name.put(new Integer(WARN), "WARN: ");
        name.put(new Integer(ERROR), "ERROR: ");
        name.put(new Integer(FATAL), "FATAL: ");
    }
    
    public JSCPLogger(org.apache.logging.log4j.Logger logger) {
    	this.logger = logger;
    }
    public boolean isEnabled(int level)
    {
        return true;
    }

    public void log(int level, String message)
    {
    	if (logger == null) {
	        System.err.print(name.get(new Integer(level)));
	        System.err.println(message);
    	}
    	else {
    		switch (level) {
    		case DEBUG:
    			if (logger.isDebugEnabled())
    				logger.debug("JScp: {} {}", name.get(new Integer(level)), message);
    			break;
    		case INFO:
    			if (logger.isInfoEnabled())
    				logger.info("JScp: {} {}", name.get(new Integer(level)), message);
    			break;
    		case WARN:
    			if (logger.isWarnEnabled())
    				logger.warn("JScp: {} {}", name.get(new Integer(level)), message);
    			break;
    		case ERROR:
    			if (logger.isErrorEnabled())
    				logger.error("JScp: {} {}", name.get(new Integer(level)), message);
    			break;
    		case FATAL:
    			if (logger.isFatalEnabled())
    				logger.fatal("JScp: {} {}", name.get(new Integer(level)), message);
    			break;
    		}
    	}
    }
}
