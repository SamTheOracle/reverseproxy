FROM arm32v7/adoptopenjdk:11.0.10_9-jdk-hotspot-focal


ENV VERTICLE_FILE reverseproxy-1.0-SNAPSHOT-fat.jar

EXPOSE 8080

COPY $VERTICLE_FILE proxyconfig/

ENTRYPOINT ["sh", "-c"]


RUN mkdir proxyconfig/certificates


CMD ["java -Xmx300m -jar proxyconfig/$VERTICLE_FILE"]
