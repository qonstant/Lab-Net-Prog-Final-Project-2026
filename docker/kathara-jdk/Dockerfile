# syntax=docker/dockerfile:1.6
FROM eclipse-temurin:17-jdk-jammy

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        iproute2 \
        iputils-ping \
        net-tools \
        openssh-client \
        procps \
        sed \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /root

EXPOSE 6001 6002 6003 6004

CMD ["/bin/bash"]
