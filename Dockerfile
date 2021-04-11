FROM openjdk:11

ENV VERTICLE_FILE reverseproxy-1.0-SNAPSHOT-fat.jar

EXPOSE 8080

COPY target/$VERTICLE_FILE proxyconfig/

ENTRYPOINT ["sh", "-c"]


RUN mkdir proxyconfig/certificates

CMD ["exec java -Xmx300m -jar proxyconfig/$VERTICLE_FILE"]
