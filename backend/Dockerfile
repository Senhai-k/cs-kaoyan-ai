ARG CONTAINER_REGISTRY=docker.io
FROM ${CONTAINER_REGISTRY}/library/maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode -DskipTests package

FROM ${CONTAINER_REGISTRY}/library/eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home appuser
WORKDIR /app
COPY --from=build /workspace/target/cs-kaoyan-ai-backend-0.0.1-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 18888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
