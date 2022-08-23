FROM eclipse-temurin:8-jre-alpine

ENV TZ=America/Los_Angeles
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN mkdir -p /app/logs /app/conf
WORKDIR /app
COPY app.jar app.jar
COPY docker.properties local.properties
COPY log4j.xml log4j.xml

VOLUME /app/conf /app/logs
EXPOSE 8080

CMD ["java", \
     "-Dlog4j.configuration=file:/app/log4j.xml", \
     "-jar","app.jar"]
