package com.alvoratrack.websocket;

import io.quarkus.websockets.next.*;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/vehicle/{vehicleId}")
public class VehicleWebSocket {

    private static final Logger LOG = Logger.getLogger(VehicleWebSocket.class);

    @OnOpen
    public void onOpen(WebSocketConnection connection, @PathParam String vehicleId) {
        LOG.infof("Vehicle connected: %s, Session: %s", vehicleId, connection.id());
    }

    @OnClose
    public void onClose(WebSocketConnection connection, @PathParam String vehicleId) {
        LOG.infof("Vehicle disconnected: %s, Session: %s", vehicleId, connection.id());
    }

    @OnTextMessage
    public String onMessage(String message, @PathParam String vehicleId) {
        LOG.infof("Message from %s: %s", vehicleId, message);

        return "{\"status\": \"received\", \"vehicleId\": \"" + vehicleId + "\"}";
    }
}