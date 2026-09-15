FROM docker.io/library/maven:3.9.11-eclipse-temurin-25@sha256:407c4423cec0cf2981055bc2c6c0dc211d9605b6669279b95997f2d1c7e91e2c AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
COPY containers/Healthcheck.java /build/Healthcheck.java
RUN javac -d /build/health /build/Healthcheck.java
RUN mvn -B -DskipTests package

FROM docker.io/library/eclipse-temurin:25-jre@sha256:15090d159279e5c158473eccb48cd87f57b3e3a47511a797eb5a7a7ea6f86b0f
WORKDIR /app
COPY --from=build /build/target/home-companion-0.1.0.jar /app/home-companion.jar
COPY --from=build /build/health/Healthcheck.class /app/Healthcheck.class
USER 10001:10001
ENV SERVER_ADDRESS=0.0.0.0 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/home-companion.jar"]
