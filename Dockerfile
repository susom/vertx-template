FROM openjdk:8-jre
ADD target/vertx-template-1.0-SNAPSHOT.jar /vertx-template-1.0-SNAPSHOT.jar

ENTRYPOINT ["java", "/vertx-template-1.0-SNAPSHOT.jar"]
