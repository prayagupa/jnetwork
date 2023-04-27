FROM openjdk:11
LABEL name=jnetwork

RUN mkdir -p /usr/local/app

COPY build/libs/jnetwork.jar /usr/local/app/jnetwork.jar

EXPOSE 8080
EXPOSE 9090

CMD ["java", "-jar", "/usr/local/app/jnetwork.jar"]
