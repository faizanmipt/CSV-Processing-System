FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY upload-service/target/*.jar app.jar
# Create upload directory
RUN mkdir -p /data/raw
ENTRYPOINT ["java","-jar","app.jar"]
