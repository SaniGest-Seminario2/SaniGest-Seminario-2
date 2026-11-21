# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
COPY config ./config
COPY templates/spring-boot-service/pom.xml templates/spring-boot-service/pom.xml
COPY services/company-service/pom.xml services/company-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/company-service/src services/company-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    sh config/maven-build.sh -pl services/company-service -am -DskipTests package && \
    cp services/company-service/target/company-service-*.jar /workspace/application.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S sanigest && adduser -S -G sanigest -u 10001 sanigest
WORKDIR /app
COPY --from=build --chown=sanigest:sanigest /workspace/application.jar application.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/application.jar"]
