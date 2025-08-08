FROM maven:3.9.6-eclipse-temurin-21 as build

COPY src src
COPY pom.xml pom.xml

RUN mvn clean package -DskipTests

FROM bellsoft/liberica-openjdk-debian:21

WORKDIR /app

COPY --from=build target/MarketTracker-1.0.0.jar MarketTracker.jar

ENTRYPOINT ["java","-jar","MarketTracker.jar"]