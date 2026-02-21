package com.alvoratrack.grpc;


import com.alvoratrack.client.AlvoraCoreClient;
import com.alvoratrack.client.TraccarClient;
import com.alvoratrack.service.PositionCache;
import com.alvoratrack.util.PositionValidator;
import com.alvoratrack.websocket.ConnectionManager;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;


import static com.alvoratrack.grpc.PositionAck.*;

@GrpcService
public class VehicleTrackingGrpcService extends com.alvoratrack.grpc.VehicleTrackingServiceGrpc.VehicleTrackingServiceImplBase {
    private static final Logger LOG = Logger.getLogger(VehicleTrackingGrpcService.class);
    @Inject
    PositionValidator validator;

    @Inject
    TraccarClient traccarClient;

    @Inject
    AlvoraCoreClient alvoraCoreClient;

    @Inject
    ConnectionManager connections;

    @Inject
    PositionCache positionCache;

    @Override
    public void reportPosition(com.alvoratrack.grpc.VehiclePosition request, StreamObserver<com.alvoratrack.grpc.PositionAck> responseObserver) {

        String validation = validator.validatePosition(request);
        if (validation != null) {
            com.alvoratrack.grpc.PositionAck ack = com.alvoratrack.grpc.PositionAck.newBuilder()
                    .setReceived(false)
                    .setVehicleId(request.getVehicleId())
                    .setStatus("invalid")
                    .setMessage(validation)
                    .build();

            responseObserver.onNext(ack);
            responseObserver.onCompleted();
            return;
        }

        LOG.infof("Position received - Vehicle: %s, Lat: %f, Lon: %f, Speed: %f km/h",
                request.getVehicleId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getSpeedKph());

        traccarClient.sendPosition(request)
                .thenAccept(success -> {
                    if (!success) {
                        LOG.warnf("Failed to forward to Traccar: %s", request.getVehicleId());
                    }
                });

        alvoraCoreClient.sendPosition(request)
                .thenAccept(success -> {
                    if (!success) {
                        LOG.warnf("Failed to notify AlvoraCore: %s", request.getVehicleId());
                    }
                });

        String wsMessage = String.format("""
        {
            "type": "position",
            "vehicleId": "%s",
            "latitude": %f,
            "longitude": %f,
            "speedKph": %f,
            "timestamp": %d
        }
        """,
                request.getVehicleId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getSpeedKph(),
                request.getTimestamp());

        connections.sendToVehicle(request.getVehicleId(), wsMessage);

        positionCache.update(request);

        connections.broadcastToAdminFleet(wsMessage);

        com.alvoratrack.grpc.PositionAck ack = newBuilder()
                .setReceived(true)
                .setVehicleId(request.getVehicleId())
                .setStatus("ok")
                .setMessage("Position received successfully")
                .build();

        responseObserver.onNext(ack);

        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<com.alvoratrack.grpc.VehiclePosition> streamPositions(
            StreamObserver<com.alvoratrack.grpc.PositionAck> responseObserver
    ) {

        LOG.info("New streaming connection opened");

        return new StreamObserver<>() {

            @Override
            public void onNext(com.alvoratrack.grpc.VehiclePosition position) {
                // Called for each position received

                // 1. Validate
                String error = validator.validatePosition(position);

                if (error != null) {
                    responseObserver.onNext(com.alvoratrack.grpc.PositionAck
                            .newBuilder()
                            .setReceived(false)
                            .setVehicleId(position.getVehicleId())
                            .setStatus("invalid")
                            .setMessage(error)
                            .build());
                    return;
                }

                // 2. Log
                LOG.infof("Stream position - Vehicle: %s, Lat: %f, Lon: %f",
                        position.getVehicleId(),
                        position.getLatitude(),
                        position.getLongitude());

                // 3. Forward to Traccar & AlvoraCore
                traccarClient.sendPosition(position);
                alvoraCoreClient.sendPosition(position);
                positionCache.update(position);

                // 3.5 Broadcast to admin fleet WebSocket
                String wsMessage = String.format("""
                {
                    "type": "position",
                    "vehicleId": "%s",
                    "latitude": %f,
                    "longitude": %f,
                    "speedKph": %f,
                    "timestamp": %d
                }
                """,
                        position.getVehicleId(),
                        position.getLatitude(),
                        position.getLongitude(),
                        position.getSpeedKph(),
                        position.getTimestamp());
                connections.broadcastToAdminFleet(wsMessage);

                // 4. Acknowledge
                responseObserver.onNext(com.alvoratrack.grpc.PositionAck
                        .newBuilder()
                        .setReceived(true)
                        .setVehicleId(position.getVehicleId())
                        .setStatus("ok")
                        .setMessage("Position streamed")
                        .build());
            }

            @Override
            public void onError(Throwable t) {
                LOG.errorf("Stream error: %s", t.getMessage());
            }

            @Override
            public void onCompleted() {
                LOG.info("Stream completed");
                responseObserver.onCompleted();
            }
        };
    }


}
