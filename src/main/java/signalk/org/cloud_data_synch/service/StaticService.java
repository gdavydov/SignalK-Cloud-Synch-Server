package signalk.org.cloud_data_synch.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import signalk.org.cloud_data_synch.utils.Config;
import signalk.org.cloud_data_synch.utils.ConfigConstants;


@Path( "/")
public class StaticService {

	public StaticService() throws Exception {
		super();
		logger.debug("StaticService starting..");
	}

	private static Logger logger = LogManager.getLogger(StaticService.class);

	private static java.nio.file.Path staticDir = Paths.get(Config.getConfigProperty(ConfigConstants.STATIC_DIR));
	
	
	@GET
	public Response get(@Context HttpServletRequest req) {
		return getResponse("/index.html",req);
	}
	
	@GET
	@Path( "{file:[^?]*}")
	public Response getResource(@Context HttpServletRequest req) {
			String targetPath = req.getPathInfo();
			return getResponse(targetPath,req);
	}
	private Response getResponse(String targetPath,HttpServletRequest req){
		try {
			targetPath= StringUtils.defaultIfEmpty(targetPath, "index.html");
			
			if(targetPath.endsWith("/")){
				targetPath=targetPath+"index.html";
			}
			targetPath= StringUtils.removeStart(targetPath, "/");
			
			if (logger.isDebugEnabled())logger.debug("serve {}",targetPath);
			java.nio.file.Path target = Paths.get(Config.getConfigProperty(ConfigConstants.STATIC_DIR),targetPath);
			
			if (!target.startsWith(staticDir)) {
				logger.warn("Forbidden request for {} from {}",target,req);
				return Response.status(HttpStatus.SC_FORBIDDEN).build();
			}
			if(Files.isDirectory(target)){
				target = Paths.get(target.toString(),"index.html");
			}
			return Response.status(HttpStatus.SC_OK)
					.type(Files.probeContentType(target))
					.entity(FileUtils.readFileToByteArray(target.toFile()))
					.build();
			
		}catch(NoSuchFileException | FileNotFoundException nsf){
			logger.warn(nsf.getMessage());
			return Response.status(HttpStatus.SC_NOT_FOUND).build();
		}
		catch (IOException e) {
			logger.error(e,e);
			return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
		}
		
	}
	
	
}
