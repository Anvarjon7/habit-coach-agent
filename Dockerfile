# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy@sha256:4b1a74fbffbe6b4e7b6f0171a472478282fb463790a51edf10e465d578f22b69 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269 AS runtime
RUN groupadd --gid 10001 coach \
    && useradd --uid 10001 --gid coach --no-create-home --shell /usr/sbin/nologin coach
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/habit-coach-agent-0.0.1-SNAPSHOT.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
