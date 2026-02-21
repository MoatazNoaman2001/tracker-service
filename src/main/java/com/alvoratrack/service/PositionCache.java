package com.alvoratrack.service;

import com.alvoratrack.grpc.VehiclePosition;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class PositionCache {

    private static final Logger LOG = Logger.getLogger(PositionCache.class);
    private static final String KEY_PREFIX = "vehicle:position:";

    @Inject
    ReactiveRedisDataSource redisDS;

    @ConfigProperty(name = "position.cache.ttl", defaultValue = "300")
    int ttlSeconds;

    private ReactiveValueCommands<String, String> commands;
    private ReactiveKeyCommands<String> keyCommands;

    @PostConstruct
    void init() {
        commands = redisDS.value(String.class, String.class);
        keyCommands = redisDS.key(String.class);
        LOG.info("Redis PositionCache initialized");
    }

    public void update(VehiclePosition position) {
        String key = KEY_PREFIX + position.getVehicleId();
        String value = toJson(position);

        commands.setex(key, ttlSeconds, value)
                .subscribe().with(
                        success -> LOG.debugf("Cached position for %s", position.getVehicleId()),
                        failure -> LOG.errorf("Failed to cache position: %s", failure.getMessage())
                );
    }

    public Uni<VehiclePosition> get(String vehicleId) {
        String key = KEY_PREFIX + vehicleId;

        return commands.get(key)
                .map(json -> {
                    if (json == null) {
                        return null;
                    }
                    return fromJson(json);
                });
    }

    public Uni<Boolean> exists(String vehicleId) {
        String key = KEY_PREFIX + vehicleId;
        return commands.get(key).map(Objects::nonNull);
    }

    public Uni<List<VehiclePosition>> getAllPositions() {
        return keyCommands.keys(KEY_PREFIX + "*")
                .onItem().transformToMulti(keys -> Multi.createFrom().iterable(keys))
                .onItem().transformToUniAndMerge(key -> commands.get(key)
                        .map(json -> json != null ? fromJson(json) : null))
                .filter(Objects::nonNull)
                .collect().asList();
    }

    private String toJson(VehiclePosition position) {
        return String.format(
                "{\"vehicleId\":\"%s\",\"latitude\":%f,\"longitude\":%f,\"speedKph\":%f,\"bearing\":%d,\"timestamp\":%d,\"altitude\":%f,\"accuracy\":%f,\"ignition\":%b,\"motion\":%b,\"satellites\":%d}",
                position.getVehicleId(),
                position.getLatitude(),
                position.getLongitude(),
                position.getSpeedKph(),
                position.getBearing(),
                position.getTimestamp(),
                position.getAltitude(),
                position.getAccuracy(),
                position.getIgnition(),
                position.getMotion(),
                position.getSatellites()
        );
    }

    private VehiclePosition fromJson(String json) {
        try {
            String vehicleId = extractString(json, "vehicleId");
            double latitude = extractDouble(json, "latitude");
            double longitude = extractDouble(json, "longitude");
            double speedKph = extractDouble(json, "speedKph");
            int bearing = extractInt(json, "bearing");
            long timestamp = extractLong(json);
            double altitude = extractDouble(json, "altitude");
            double accuracy = extractDouble(json, "accuracy");
            boolean ignition = extractBoolean(json, "ignition");
            boolean motion = extractBoolean(json, "motion");
            int satellites = extractInt(json, "satellites");

            return VehiclePosition.newBuilder()
                    .setVehicleId(vehicleId)
                    .setLatitude(latitude)
                    .setLongitude(longitude)
                    .setSpeedKph(speedKph)
                    .setBearing(bearing)
                    .setTimestamp(timestamp)
                    .setAltitude(altitude)
                    .setAccuracy(accuracy)
                    .setIgnition(ignition)
                    .setMotion(motion)
                    .setSatellites(satellites)
                    .build();
        } catch (Exception e) {
            LOG.errorf("Failed to parse position JSON: %s", e.getMessage());
            return null;
        }
    }

    private String extractString(String json, String key) {
        int start = json.indexOf("\"" + key + "\":\"") + key.length() + 4;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private double extractDouble(String json, String key) {
        int start = json.indexOf("\"" + key + "\":") + key.length() + 3;
        int end = findNumberEnd(json, start);
        return Double.parseDouble(json.substring(start, end));
    }

    private int extractInt(String json, String key) {
        int start = json.indexOf("\"" + key + "\":") + key.length() + 3;
        int end = findNumberEnd(json, start);
        return Integer.parseInt(json.substring(start, end));
    }

    private long extractLong(String json) {
        int start = json.indexOf("\"" + "timestamp" + "\":") + "timestamp".length() + 3;
        int end = findNumberEnd(json, start);
        return Long.parseLong(json.substring(start, end));
    }

    private boolean extractBoolean(String json, String key) {
        int start = json.indexOf("\"" + key + "\":") + key.length() + 3;
        return json.substring(start).startsWith("true");
    }

    private int findNumberEnd(String json, int start) {
        int i = start;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '.' || json.charAt(i) == '-')) {
            i++;
        }
        return i;
    }
}