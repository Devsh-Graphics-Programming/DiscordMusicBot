FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package && \
    mkdir -p /out && \
    cp target/*-All.jar /out/JMusicBot.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /out/JMusicBot.jar /app/JMusicBot.jar
COPY docker/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENV JMUSICBOT_HOME=/data
VOLUME ["/data"]

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
