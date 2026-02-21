package com.alvoratrack.client;

import com.alvoratrack.grpc.VehiclePosition;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class TraccarClient {
    private static Logger LOG = Logger.getLogger(TraccarClient.class);
    public static final Double KPH_TO_KNOTS = 0.539957;

    @ConfigProperty(name="traccar.url")
    String url;

    @ConfigProperty(name = "traccar.timeout", defaultValue = "5")
    int timeout;

    private HttpClient client;

    @PostConstruct
    private void init() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();
    }


    public CompletableFuture<Boolean> sendPosition(VehiclePosition position) {
        double knots = position.getSpeedKph() * KPH_TO_KNOTS;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        url + "/?id=" + position.getVehicleId()
                                + "&lat=" + position.getLatitude()
                                + "&lon=" + position.getLongitude()
                                + "&timestamp=" + position.getTimestamp()
                                + "&speed=" + knots
                                + "&bearing=" + position.getBearing()
                ))
                .timeout(Duration.ofSeconds(timeout))
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        LOG.debugf("Traccar accepted position for vehicle: %s", position.getVehicleId());
                        return true;
                    } else {
                        LOG.warnf("Traccar rejected position. Status: %d, Body: %s",
                                response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(throwable -> {
                    LOG.errorf("Failed to send to Traccar: %s", throwable.getMessage());
                    return false;
                });
    }
}
