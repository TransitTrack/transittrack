FROM eclipse-temurin:23
USER root
WORKDIR /app
COPY ./app/build/libs/app-3.0.0-SNAPSHOT.jar .
COPY ./app/src/main/resources/config/application.yml .