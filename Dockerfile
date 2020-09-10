FROM openjdk:8-jre-alpine

ENV VERTICLE_FILE reverseproxy-1.0-SNAPSHOT-fat.jar
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080 9000

COPY target/$VERTICLE_FILE $VERTICLE_HOME/

COPY certificates/proxy-keystore-healthcheck.jks $VERTICLE_HOME/

COPY certificates/proxy-keystore-local.jks $VERTICLE_HOME/

COPY certificates/keystore.jks  $VERTICLE_HOME/

WORKDIR $VERTICLE_HOME

ENTRYPOINT ["sh", "-c"]

CMD ["exec java -jar $VERTICLE_FILE"]
