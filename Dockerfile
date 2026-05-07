FROM eclipse-temurin:21-jre
LABEL name=jnetwork

RUN mkdir -p /usr/local/app && apt-get update && apt-get install -y telnet net-tools traceroute

COPY build/libs/jnetwork.jar /usr/local/app/jnetwork.jar

EXPOSE 8080
EXPOSE 9090

CMD ["java", "-jar", "/usr/local/app/jnetwork.jar"]
