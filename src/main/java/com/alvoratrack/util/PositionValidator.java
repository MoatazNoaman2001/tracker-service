package com.alvoratrack.util;

import com.alvoratrack.grpc.VehiclePosition;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

@ApplicationScoped
public class PositionValidator {

    private static Logger Log = Logger.getLogger(PositionValidator.class);

    public String validatePosition(VehiclePosition position){
        if (position.getVehicleId().isBlank()) {
            Log.errorf("Invalid Vehicle ID: %s", position.getVehicleId());
            return "Invalid Vehicle ID";
        }
        if (position.getLatitude() > 90 || position.getLatitude() < -90){
            Log.errorf("Invalid Latitude: %f", position.getLatitude());
            return "Invalid Latitude";
        }
        if (position.getLongitude() > 180 || position.getLongitude() < -180){
            Log.errorf("Invalid Longitude: %f", position.getLatitude());
            return "Invalid Longitude";
        }
        if (position.getSpeedKph() > 300) {
            Log.errorf("Invalid Speed (very big): %f", position.getSpeedKph());
            return "Invalid Speed";
        }
        long currentTime = System.currentTimeMillis() / 1000;
        if (position.getTimestamp() > currentTime + 60) {
            Log.errorf("Invalid Timestamp (future): %d", position.getTimestamp());
            return "Invalid Timestamp";
        }

        return null;
    }
}
