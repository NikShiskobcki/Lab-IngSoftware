# Etapa de compilación con Maven y OpenJDK 17
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Descarga previa de dependencias para aprovechar la caché de Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia de código fuente y empaquetado del Uber/Fat JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa final de ejecución (imagen ligera JRE Alpine)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copia del JAR resultante desde la etapa de compilación
COPY --from=builder /app/target/turno-publisher.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
