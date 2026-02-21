package com.alvoratrack.config;

import com.alvoratrack.service.PositionCache;
import com.alvoratrack.websocket.ConnectionManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

//this class is recommended from opus and really helpfull

@ApplicationScoped
public class HealthChecks {

    @Inject
    ConnectionManager connectionManager;

    @Inject
    PositionCache positionCache;

    @Liveness
    public HealthCheck liveness() {
        // Service is alive if it responds
        return () -> HealthCheckResponse.named("tracker-alive")
                .up()
                .build();
    }

    @Readiness
    public HealthCheck readiness() {
        // Service is ready - add real checks here later (Traccar, AlvoraCore reachable)
        return () -> HealthCheckResponse.named("tracker-ready")
                .up()
                .withData("websocket-connections", connectionManager.getConnectionCount())
                .build();
    }
}