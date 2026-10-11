# Build uses a host-vendored Gradle distribution (see `make prepare-docker-gradle`).
# That avoids downloading gradle-*-bin.zip inside the image (known hang on some labs).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

COPY docker-gradle /opt/gradle
ENV PATH="/opt/gradle/bin:${PATH}"
ENV GRADLE_USER_HOME=/root/.gradle

COPY settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/notifications \
    gradle bootJar --no-daemon -x test \
    && cp build/libs/backend-0.0.1-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
