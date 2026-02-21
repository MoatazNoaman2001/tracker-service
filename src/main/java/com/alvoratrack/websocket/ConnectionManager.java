package com.alvoratrack.websocket;

import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ConnectionManager {

    private static final Logger LOG = Logger.getLogger(ConnectionManager.class);

    @Inject
    OpenConnections connections;

    public void sendToVehicle(String vehicleId, String message) {
        long count = connections.stream().count();
        LOG.infof("Sending to vehicle %s, Total connections: %d", vehicleId, count);

        connections.forEach(conn -> {
            String connVehicleId = conn.pathParam("vehicleId");
            LOG.infof("Checking connection: %s, pathParam vehicleId: %s", conn.id(), connVehicleId);

            if (vehicleId.equals(connVehicleId)) {
                LOG.infof("Match found! Sending to %s", connVehicleId);
                conn.sendText(message).subscribe().with(
                        success -> LOG.debugf("Message sent to %s", vehicleId),
                        failure -> LOG.errorf("Failed to send to %s: %s", vehicleId, failure.getMessage())
                );
            }
        });
    }



    public void broadcast(String message) {
        connections.forEach(conn -> {
            conn.sendText(message).subscribe().with(
                    success->LOG.debugf("Message sent to %s", conn.id()),
                    failure->LOG.errorf("Failed to send to %s: %s", conn.id(), failure.getMessage())
            );
        });
        LOG.debugf("Broadcast to %d clients", connections.stream().count());
    }

    public long getConnectionCount() {
        return connections.stream().count();
    }

    public void broadcastToAdminFleet(String message) {
        connections.forEach(conn -> {
            String handshakePath = conn.handshakeRequest().path();
            if (handshakePath != null && handshakePath.contains("/admin/fleet")) {
                conn.sendText(message).subscribe().with(
                        success -> LOG.debugf("Admin fleet message sent to %s", conn.id()),
                        failure -> LOG.errorf("Failed to send admin fleet message to %s: %s", conn.id(), failure.getMessage())
                );
            }
        });
        LOG.debugf("Broadcast to admin fleet clients");
    }

    public long getAdminFleetConnectionCount() {
        return connections.stream()
                .filter(conn -> {
                    String path = conn.handshakeRequest().path();
                    return path != null && path.contains("/admin/fleet");
                })
                .count();
    }
}