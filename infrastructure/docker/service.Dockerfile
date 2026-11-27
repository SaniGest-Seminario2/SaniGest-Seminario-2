# syntax=docker/dockerfile:1.7

FROM scratch AS maven-cache

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
ARG SERVICE

COPY pom.xml ./
COPY config ./config
COPY scripts/ci/maven-build.sh scripts/ci/maven-build.sh
COPY libraries/common-security libraries/common-security
COPY libraries/common-company-access libraries/common-company-access
COPY libraries/common-events libraries/common-events
COPY libraries/common-web libraries/common-web
COPY services/company-service/pom.xml services/company-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/catalog-service/pom.xml services/catalog-service/pom.xml
COPY services/sales-service/pom.xml services/sales-service/pom.xml
COPY services/inventory-service/pom.xml services/inventory-service/pom.xml
COPY services/expense-service/pom.xml services/expense-service/pom.xml
COPY services/finance-service/pom.xml services/finance-service/pom.xml
COPY services/${SERVICE}/src services/${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    --mount=type=bind,from=maven-cache,target=/maven-seed \
    mkdir -p /root/.m2/repository && \
    cp -an /maven-seed/. /root/.m2/repository/ && \
    sh scripts/ci/maven-build.sh -pl "services/${SERVICE}" -am -DskipTests package && \
    cp services/"${SERVICE}"/target/"${SERVICE}"-*.jar /workspace/application.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S sanigest && adduser -S -G sanigest -u 10001 sanigest
WORKDIR /app
COPY --from=build --chown=sanigest:sanigest /workspace/application.jar application.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/application.jar"]
