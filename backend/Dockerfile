# =============================================================================
# Imagem do backend FleetOps — build multi-estágio.
# =============================================================================

# --- Estágio 1: compilação -----------------------------------------------------
FROM gradle:8.14-jdk21 AS builder
WORKDIR /build

# As dependências são resolvidas antes de copiar o código-fonte, para que uma
# alteração em um arquivo .java não invalide o cache de download do Gradle.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle --no-daemon dependencies --quiet || true

COPY config ./config
COPY src ./src
RUN gradle --no-daemon bootJar --quiet

# --- Estágio 2: execução -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Executa como usuário sem privilégios: um comprometimento do processo não vira
# acesso root ao container.
RUN addgroup -S fleetops && adduser -S fleetops -G fleetops

WORKDIR /app
COPY --from=builder --chown=fleetops:fleetops /build/build/libs/fleetops.jar ./fleetops.jar

USER fleetops
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Duser.timezone=UTC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/fleetops.jar"]
