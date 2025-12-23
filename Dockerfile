# Stage 1: Build the application
# We use a Maven image with Java 21 because your pom.xml specifies <java.version>21</java.version>
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .

# Download dependencies (this layer will be cached unless pom.xml changes)
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Package the application (skipping tests to speed up the build)
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Expose the port defined in Spring Boot (default 8080)
EXPOSE 8080

# Copy the jar from the build stage
# Note: We find the jar ending in .jar but exclude original-*.jar if it exists
COPY --from=build /app/target/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]