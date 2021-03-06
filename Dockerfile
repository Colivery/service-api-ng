FROM adoptopenjdk/openjdk11:slim
COPY build/libs/service-api-ng-*.jar /app.jar
COPY credentials.json /credentials.json
CMD ["java", "-jar", "/app.jar"]
EXPOSE 8080
