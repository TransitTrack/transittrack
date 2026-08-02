/* (C)2026 */
package org.transitclock.api.version;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.transitclock.api.data.ApiVersion;

@Path("/")
public class VersionApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersion() {
        return Response.ok(VersionReader.read()).build();
    }
}
