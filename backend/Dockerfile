# --- Build stage: Fat-Jar mit Maven bauen ---
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Dependencies zuerst cachen (nur pom.xml -> Layer bleibt stabil, solange sich Deps nicht ändern)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage: schlankes JRE (Azul Zulu, Alpine) ---
FROM azul/zulu-openjdk-alpine:21-jre
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/webauthn-backend-fat.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
