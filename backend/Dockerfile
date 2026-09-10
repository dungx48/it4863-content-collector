FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/collector.jar ./collector.jar
RUN mkdir -p /app/data && chown 10001:10001 /app/data
USER 10001:10001
ENV SERVER_HOST=0.0.0.0 SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD ["java", "-jar", "collector.jar", "--health-check"]
ENTRYPOINT ["java", "-jar", "collector.jar"]
CMD ["--serve"]
