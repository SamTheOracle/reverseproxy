FROM openjdk:8-jre-alpine

ENV VERTICLE_FILE reverseproxy-1.0-SNAPSHOT.jar

ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

COPY target/$VERTICLE_FILE  $VERTICLE_HOME/

WORKDIR $VERTICLE_HOME

ENTRYPOINT ["sh", "-c"]

CMD ["exec java -jar $VERTICLE_FILE"]
