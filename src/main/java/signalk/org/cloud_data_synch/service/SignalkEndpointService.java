package signalk.org.cloud_data_synch.service;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import signalk.org.cloud_data_synch.utils.Config;


@Path("/signalk")
//@Api(value = "Signalk Root Endpoint")
public class SignalkEndpointService {

	private static Logger logger = LogManager.getLogger(SignalkEndpointService.class);

	@Operation(summary = "Request signalk endpoints and server details", description = "Returns the json endpoints message for this server")
	@ApiResponses ({
	    @ApiResponse(responseCode = "200", description = "OK"),
	    @ApiResponse(responseCode = "500", description = "Internal server error")
	    })
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response onGet(@Context HttpServletRequest req) {
		try {
			return Response.status(HttpStatus.SC_OK)
			.entity(Config.getDiscoveryMsg(req.getLocalName()).toString())
			.type(MediaType.APPLICATION_JSON).build();
			
		} catch (Exception e) {
			logger.error(e,e);
			return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
		}
	}

}
