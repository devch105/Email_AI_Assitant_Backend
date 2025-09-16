# Use Java 21 JDK
FROM openjdk:21-jdk-slim

# Set working directory inside container
WORKDIR /app

# Copy the JAR file into container
COPY target/email-writer-0.0.1-SNAPSHOT.jar app.jar

# Expose Spring Boot default port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java","-jar","app.jar"]
