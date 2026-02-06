FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY worker-service/target/*.jar app.jar
# Create data directories
RUN mkdir -p /data/raw /data/processed
ENTRYPOINT ["java","-jar","app.jar"]
