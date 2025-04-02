# Stage 1: Build
FROM eclipse-temurin:21 AS build

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy only the POM file first to leverage Docker caching for dependencies
COPY pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline

# Copy the rest of the source code

COPY src ./src

# Clean, compile, and package the application (produces JAR)
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21

WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/marketplace-service-0.0.1-SNAPSHOT.jar /app/

# Expose the application port
EXPOSE 8080

# Run the application using the JAR file
ENTRYPOINT ["java", "-jar", "/app/marketplace-service-0.0.1-SNAPSHOT.jar"]

