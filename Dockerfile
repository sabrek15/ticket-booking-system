# ==========================================
# STAGE 1: Build (Works on Apple Silicon ARM64 & Intel AMD64)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# 1. Copy POM files first for optimal layer caching
COPY pom.xml .
COPY booking-common/pom.xml booking-common/
COPY booking-core/pom.xml booking-core/
COPY booking-api/pom.xml booking-api/

# 2. Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# 3. Copy source code across all modules
COPY booking-common/src booking-common/src
COPY booking-core/src booking-core/src
COPY booking-api/src booking-api/src

# 4. Package executable JAR (skip tests during container build)
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Lightweight Production Runtime (Official Multi-Arch JRE)
# ==========================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# 1. Security: Create non-root user (Standard Linux syntax)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 2. Copy the fat JAR from the builder stage
COPY --from=builder /build/booking-api/target/booking-api-1.0.0-SNAPSHOT.jar app.jar

# 3. Set ownership and switch to non-root user
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]