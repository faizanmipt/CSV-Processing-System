FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY download-service/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
