FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build

COPY services/Pactum-Scroll /build/services/Pactum-Scroll
COPY services/Graphite-Forge /build/services/Graphite-Forge

RUN mvn -B -f /build/services/Pactum-Scroll/pom.xml -DskipTests install \
	&& mvn -B -f /build/services/Graphite-Forge/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

COPY --from=build /build/services/Graphite-Forge/target/graphite-forge-4.0.1.jar /app/graphite-forge.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/graphite-forge.jar"]