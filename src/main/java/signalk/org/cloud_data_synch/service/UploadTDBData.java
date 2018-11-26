/**
 * 
 */
package signalk.org.cloud_data_synch.service;

import org.atmosphere.annotation.Suspend;
import org.atmosphere.config.service.MeteorService;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.Meteor;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.util.Date;

/**
 * @author GD85376
 *
 */

public class UploadTDBData extends HttpServlet {
    @Inject
    @Named("/*")
    private Broadcaster broadcaster;

    /**
     * Create a {@link Meteor} and use it to suspend the response.
     *
     * @param req An {@link HttpServletRequest}
     * @param res An {@link HttpServletResponse}
     */
    @Override
	@Operation(summary = "Signalk DB data Upload", description = "Signalk DB data Upload")
	@ApiResponses ({
	    @ApiResponse(responseCode = "200", description = "OK", 
	    		content = {@Content(mediaType = MediaType.APPLICATION_JSON, 
	    			examples = @ExampleObject(name="update", value = "{\"test\"}"))
	    				}),
	    @ApiResponse(responseCode = "501", description = "Upload not supported"),
	    @ApiResponse(responseCode = "500", description = "Internal server error"),
	    @ApiResponse(responseCode = "403", description = "No permission")
	    })
	@Suspend(contentType = MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("{path:[^?]*}")
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException 
    {
        // Set the logger level to TRACE to see what's happening.
        Meteor.build(req).addListener(new AtmosphereResourceEventListenerAdapter());
    }

    /**
     * Re-use the {@link Meteor} created on the first GET for broadcasting message.
     *
     * @param req An {@link HttpServletRequest}
     * @param res An {@link HttpServletResponse}
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String body = req.getReader().readLine().trim();
        // Simple JSON -- Use Jackson for more complex structure
        // Message looks like { "author" : "foo", "message" : "bar" }
        String author = body.substring(body.indexOf(":") + 2, body.indexOf(",") - 1);
        String message = body.substring(body.lastIndexOf(":") + 2, body.length() - 2);
        broadcaster.broadcast(new Data(author, message).toString());
    }

    private final static class Data {

        private final String text;
        private final String author;

        public Data(String author, String text) {
            this.author = author;
            this.text = text;
        }

        public String toString() {
            return "{ \"text\" : \"" + text + "\", \"author\" : \"" + author + "\" , \"time\" : " + new Date().getTime() + "}";
        }
    }

}
