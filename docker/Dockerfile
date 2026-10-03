FROM maven:3.9.9-eclipse-temurin-21 AS build

ARG MODULE
WORKDIR /workspace

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY banco-legacy-core/pom.xml banco-legacy-core/pom.xml
COPY banco-legacy-batch/pom.xml banco-legacy-batch/pom.xml
COPY banco-legacy-config-server/pom.xml banco-legacy-config-server/pom.xml
COPY banco-legacy-discovery-server/pom.xml banco-legacy-discovery-server/pom.xml
COPY banco-legacy-account-service/pom.xml banco-legacy-account-service/pom.xml
COPY banco-legacy-anomaly-service/pom.xml banco-legacy-anomaly-service/pom.xml
COPY banco-legacy-auth/pom.xml banco-legacy-auth/pom.xml
COPY banco-legacy-web-bff/pom.xml banco-legacy-web-bff/pom.xml
COPY banco-legacy-mobile-bff/pom.xml banco-legacy-mobile-bff/pom.xml
COPY banco-legacy-atm-bff/pom.xml banco-legacy-atm-bff/pom.xml

RUN mvn -B -pl "${MODULE}" -am dependency:go-offline

COPY banco-legacy-core/src banco-legacy-core/src
COPY banco-legacy-batch/src banco-legacy-batch/src
COPY banco-legacy-config-server/src banco-legacy-config-server/src
COPY banco-legacy-discovery-server/src banco-legacy-discovery-server/src
COPY banco-legacy-account-service/src banco-legacy-account-service/src
COPY banco-legacy-anomaly-service/src banco-legacy-anomaly-service/src
COPY banco-legacy-auth/src banco-legacy-auth/src
COPY banco-legacy-web-bff/src banco-legacy-web-bff/src
COPY banco-legacy-mobile-bff/src banco-legacy-mobile-bff/src
COPY banco-legacy-atm-bff/src banco-legacy-atm-bff/src

RUN mvn -B -pl "${MODULE}" -am package -DskipTests \
    && cp "${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar" /workspace/application.jar

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --home-dir /app spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/application.jar application.jar

USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
