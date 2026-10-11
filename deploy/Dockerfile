# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY catalog-core catalog-core
COPY catalog-adapters catalog-adapters
COPY catalog-app catalog-app
RUN --mount=type=cache,target=/root/.m2 mvn -B -q verify
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/catalog-app/target/catalog-app-2.0.0-SNAPSHOT.jar app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","app.jar"]
