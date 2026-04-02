# Build stage
FROM eclipse-temurin:21-jre-alpine

# Set timezone
ENV TZ=America/Sao_Paulo

# Set working directory
WORKDIR /app

# Copy the JAR file from target directory
COPY target/consumer-*.jar app.jar

# Expose port 8081
EXPOSE 8081

# Set JVM options for container environment
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
