FROM eclipse-temurin:21-jdk-jammy AS builder

# Repo-root build context so aip/ (SID/STAR catalog source of truth) is available.
WORKDIR /workspace

COPY Backend/gradle Backend/gradle
COPY Backend/gradlew Backend/gradlew
COPY Backend/build.gradle Backend/build.gradle
COPY Backend/settings.gradle Backend/settings.gradle
COPY Backend/src Backend/src
COPY aip aip

WORKDIR /workspace/Backend

RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /workspace/Backend/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
