package com.alvoratrack.client;

import com.alvoratrack.grpc.VehiclePosition;
import com.alvoratrack.grpc.fleet.FleetPositionAck;
import com.alvoratrack.grpc.fleet.FleetPositionUpdate;
import com.alvoratrack.grpc.fleet.FleetTrackingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class AlvoraCoreClient {
    private static final Logger LOG = Logger.getLogger(AlvoraCoreClient.class);

    @ConfigProperty(name = "quarkus.grpc.clients.alvoracore.host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "quarkus.grpc.clients.alvoracore.port", defaultValue = "9001")
    int port;

    private ManagedChannel channel;
    private FleetTrackingServiceGrpc.FleetTrackingServiceStub asyncStub;
    private FleetTrackingServiceGrpc.FleetTrackingServiceBlockingStub blockingStub;

    @PostConstruct
    void init() {
        LOG.infof("Initializing AlvoraCore gRPC client - connecting to %s:%d", host, port);
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        asyncStub = FleetTrackingServiceGrpc.newStub(channel);
        blockingStub = FleetTrackingServiceGrpc.newBlockingStub(channel);
        LOG.info("AlvoraCore gRPC client initialized");
    }

    @PreDestroy
    void shutdown() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                LOG.info("AlvoraCore gRPC channel shut down");
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while shutting down gRPC channel");
                Thread.currentThread().interrupt();
            }
        }
    }

    public CompletableFuture<Boolean> sendPosition(VehiclePosition position) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        FleetPositionUpdate update = FleetPositionUpdate.newBuilder()
                .setVehicleId(position.getVehicleId())
                .setLatitude(position.getLatitude())
                .setLongitude(position.getLongitude())
                .setSpeedKph(position.getSpeedKph())
                .setTimestamp(position.getTimestamp())
                .setEvent(position.getSpeedKph() > 5 ? "moving" : "stopped")
                .build();

        asyncStub.updatePosition(update, new StreamObserver<>() {
            @Override
            public void onNext(FleetPositionAck ack) {
                if (ack.getReceived()) {
                    LOG.debugf("AlvoraCore accepted position for vehicle: %s", position.getVehicleId());
                    future.complete(true);
                } else {
                    LOG.warnf("AlvoraCore rejected position for vehicle %s: %s",
                            position.getVehicleId(), ack.getMessage());
                    future.complete(false);
                }
            }

            @Override
            public void onError(Throwable t) {
                LOG.errorf("Failed to send position to AlvoraCore: %s", t.getMessage());
                future.complete(false);
            }

            @Override
            public void onCompleted() {
                // Response already handled in onNext
            }
        });

        return future;
    }

    public boolean sendPositionSync(VehiclePosition position) {
        try {
            FleetPositionUpdate update = FleetPositionUpdate.newBuilder()
                    .setVehicleId(position.getVehicleId())
                    .setLatitude(position.getLatitude())
                    .setLongitude(position.getLongitude())
                    .setSpeedKph(position.getSpeedKph())
                    .setTimestamp(position.getTimestamp())
                    .setEvent(position.getSpeedKph() > 5 ? "moving" : "stopped")
                    .build();

            FleetPositionAck ack = blockingStub.updatePosition(update);
            return ack.getReceived();
        } catch (Exception e) {
            LOG.errorf("Sync send to AlvoraCore failed: %s", e.getMessage());
            return false;
        }
    }
}
