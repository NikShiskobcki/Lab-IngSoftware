# Etapa de compilación con Maven y OpenJDK 17
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Descarga previa de dependencias para optimizar la caché de capas Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia del código fuente y compilación
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa de ejecución sobre imagen ligera JRE Alpine
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copia del Uber JAR generado
COPY --from=builder /app/target/turno-subscriber.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
