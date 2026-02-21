####
# Multi-stage Dockerfile for Tracker Service (Quarkus)
####

###
# Stage 1: Build the application
###
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven configuration files
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B \
    -Daether.syncContext.named.factory=noop

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests \
    -Dquarkus.package.type=fast-jar \
    -Daether.syncContext.named.factory=noop

###
# Stage 2: Create the runtime image
###
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.23

ENV LANGUAGE='en_US:en'
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

WORKDIR /deployments

# Copy the built application from builder stage
COPY --from=builder --chown=185 /app/target/quarkus-app/lib/ ./lib/
COPY --from=builder --chown=185 /app/target/quarkus-app/*.jar ./
COPY --from=builder --chown=185 /app/target/quarkus-app/app/ ./app/
COPY --from=builder --chown=185 /app/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8083

USER 185

ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
