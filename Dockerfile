FROM ubuntu:20.04

WORKDIR /app

COPY target/sample-java-app-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]