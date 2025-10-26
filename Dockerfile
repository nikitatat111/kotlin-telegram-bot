FROM gradle:8.8-jdk17-alpine AS build
WORKDIR /home/gradle/src

COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle ./gradle
RUN gradle --version

COPY . .
RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
ENV APP_HOME=/app
WORKDIR $APP_HOME

COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

CMD ["sh","-c","java $JAVA_OPTS -jar app.jar"]
