# Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/demoapp-1.0.0.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar","--server.port=8081"]

