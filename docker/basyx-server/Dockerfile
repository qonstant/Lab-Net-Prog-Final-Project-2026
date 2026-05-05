# syntax=docker/dockerfile:1.6
FROM --platform=$BUILDPLATFORM gradle:8.7-jdk17-jammy AS deps

ARG BASYX_VERSION=1.4.0

WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon -PbasyxVersion="${BASYX_VERSION}" copyRuntimeDeps

FROM eclipse-temurin:17-jre-jammy

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        iproute2 \
        net-tools \
        procps \
        sed \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/basyx
COPY --from=deps /src/build/runtime-libs/ ./lib/
COPY context.properties aas.properties ./

ENV BASYX_CONTEXT=/opt/basyx/context.properties
ENV BASYX_AAS=/opt/basyx/aas.properties

EXPOSE 4001

CMD ["java", "-cp", "/opt/basyx/lib/*", "org.eclipse.basyx.components.aas.executable.AASServerExecutable"]
