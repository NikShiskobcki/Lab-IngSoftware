# syntax=docker/dockerfile:1
# maven
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# descarga de dependencias
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

# empaquetado
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests

# ejecucion
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/target/turnos-api.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]




