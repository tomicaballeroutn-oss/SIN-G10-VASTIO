# Imagen del backend para el ambiente de prueba (docker-compose.prod.yml). Multi-arquitectura: amd64 y arm64 (Oracle Ampere).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/vastio-backend-*.jar app.jar

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 vastio     && mkdir -p /datos/legajo && chown vastio /datos/legajo
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER vastio
ENV TZ=America/Argentina/Cordoba \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Argentina/Cordoba"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
