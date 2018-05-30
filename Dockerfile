FROM openjdk:8-jre
ADD target/vertx-template-1.0-SNAPSHOT.jar /vertx-template-1.0-SNAPSHOT.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/vertx-template-1.0-SNAPSHOT.jar"]

