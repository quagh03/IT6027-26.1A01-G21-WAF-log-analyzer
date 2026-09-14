FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp build/libs/backend-0.0.1-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
