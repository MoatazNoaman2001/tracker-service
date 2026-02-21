package com.alvoratrack.websocket;

import io.quarkus.websockets.next.*;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/admin/fleet")
public class AdminFleetWebSocket {

    private static final Logger LOG = Logger.getLogger(AdminFleetWebSocket.class);

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        LOG.infof("Admin fleet client connected: %s", connection.id());
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        LOG.infof("Admin fleet client disconnected: %s", connection.id());
    }

    @OnTextMessage
    public String onMessage(String message, WebSocketConnection connection) {
        LOG.infof("Message from admin client %s: %s", connection.id(), message);
        return "{\"status\": \"received\", \"type\": \"admin\"}";
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable error) {
        LOG.errorf("Error on admin fleet connection %s: %s", connection.id(), error.getMessage());
    }
}
