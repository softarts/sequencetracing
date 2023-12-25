FROM azul/zulu-openjdk:17.0.7-17.42.19-jre

COPY build/libs/*SNAPSHOT.jar /app/app.jar
COPY ../opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"
ENV OTEL_TRACES_EXPORTER=logging
ENV OTEL_METRICS_EXPORTER=logging
ENV OTEL_LOGS_EXPORTER=logging
ENV OTEL_INSTRUMENTATION_HTTP_SERVER_CAPTURE_REQUEST_HEADERS=x-bdd-corrid

EXPOSE 8080

ENTRYPOINT ["java",  \
            "-server", \
            "-cp", "/app:/app/lib/*", \
            "-XX:MaxRAMPercentage=60.0", \
            "-XX:+UnlockDiagnosticVMOptions", \
            "-XX:NativeMemoryTracking=summary", \
            "-XX:+PrintNMTStatistics", \
            "-jar", "/app/app.jar"]