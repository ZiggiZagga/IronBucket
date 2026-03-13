FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/graphite-forge-4.0.1.jar /app/graphite-forge.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/graphite-forge.jar"]