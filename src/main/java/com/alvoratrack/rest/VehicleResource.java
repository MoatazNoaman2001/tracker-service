package com.alvoratrack.rest;

import com.alvoratrack.grpc.VehiclePosition;
import com.alvoratrack.service.PositionCache;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/vehicle")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VehicleResource {

    private static final Logger LOG = Logger.getLogger(VehicleResource.class);

    @Inject
    PositionCache positionCache;

    @GET
    @Path("/{vehicleId}/position")
    public Uni<Response> getVehiclePosition(@PathParam("vehicleId") String vehicleId) {
        LOG.infof("Getting position for vehicle: %s", vehicleId);

        return positionCache.get(vehicleId)
                .map(position -> {
                    if (position == null) {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity("{\"error\": \"Vehicle not found\", \"vehicleId\": \"" + vehicleId + "\"}")
                                .build();
                    }

                    String json = String.format("""
                            {
                                "vehicleId": "%s",
                                "latitude": %f,
                                "longitude": %f,
                                "speedKph": %f,
                                "bearing": %d,
                                "timestamp": %d
                            }
                            """,
                            position.getVehicleId(),
                            position.getLatitude(),
                            position.getLongitude(),
                            position.getSpeedKph(),
                            position.getBearing(),
                            position.getTimestamp());

                    return Response.ok(json).build();
                });
    }

    @GET
    @Path("/{vehicleId}/status")
    public Uni<Response> getVehicleStatus(@PathParam("vehicleId") String vehicleId) {
        LOG.infof("Getting status for vehicle: %s", vehicleId);

        return positionCache.get(vehicleId)
                .map(position -> {
                    if (position == null) {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity("{\"error\": \"Vehicle not found\"}")
                                .build();
                    }

                    String status = position.getSpeedKph() > 5 ? "moving" : "stopped";

                    String json = String.format("""
                            {
                                "vehicleId": "%s",
                                "status": "%s",
                                "speedKph": %f
                            }
                            """,
                            vehicleId,
                            status,
                            position.getSpeedKph());

                    return Response.ok(json).build();
                });
    }

    @POST
    @Path("/{vehicleId}/status")
    public Response updateVehicleStatus(@PathParam("vehicleId") String vehicleId, String body) {
        LOG.infof("Updating status for vehicle: %s, body: %s", vehicleId, body);

        String json = String.format("""
                {
                    "vehicleId": "%s",
                    "message": "Status update received"
                }
                """, vehicleId);

        return Response.ok(json).build();
    }

    @GET
    @Path("/all/positions")
    public Uni<Response> getAllPositions() {
        LOG.info("Getting all vehicle positions");

        return positionCache.getAllPositions()
                .map(positions -> {
                    List<String> positionJsonList = positions.stream()
                            .map(p -> String.format("""
                                    {
                                        "vehicleId": "%s",
                                        "latitude": %f,
                                        "longitude": %f,
                                        "speedKph": %f,
                                        "bearing": %d,
                                        "timestamp": %d
                                    }""",
                                    p.getVehicleId(),
                                    p.getLatitude(),
                                    p.getLongitude(),
                                    p.getSpeedKph(),
                                    p.getBearing(),
                                    p.getTimestamp()))
                            .collect(Collectors.toList());

                    String json = String.format("""
                            {
                                "count": %d,
                                "positions": [%s]
                            }
                            """,
                            positions.size(),
                            String.join(",", positionJsonList));

                    return Response.ok(json).build();
                });
    }
}