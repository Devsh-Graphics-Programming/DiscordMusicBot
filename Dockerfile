FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package && \
    mkdir -p /out && \
    cp target/*-All.jar /out/JMusicBot.jar

FROM golang:1.25-bookworm AS golibrespot-build
WORKDIR /src

ARG APT_FLAGS="-o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30"

RUN apt-get ${APT_FLAGS} update && apt-get ${APT_FLAGS} install -y --no-install-recommends \
    git \
    pkg-config \
    libogg-dev \
    libvorbis-dev \
    flac \
    libflac-dev \
    libasound2-dev && \
    rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch v0.7.1 https://github.com/devgianlu/go-librespot.git go-librespot
WORKDIR /src/go-librespot
RUN go build -o /out/go-librespot ./cmd/daemon

FROM debian:bookworm-slim
WORKDIR /app

ARG APT_FLAGS="-o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30"

RUN apt-get ${APT_FLAGS} update && apt-get ${APT_FLAGS} install -y --no-install-recommends \
    ffmpeg \
    ca-certificates \
    libasound2 && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /opt/java/openjdk /opt/java/openjdk
COPY --from=build /out/JMusicBot.jar /app/JMusicBot.jar
COPY --from=golibrespot-build /out/go-librespot /usr/local/bin/go-librespot
COPY docker/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh /usr/local/bin/go-librespot

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV JMUSICBOT_HOME=/data
VOLUME ["/data"]

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
