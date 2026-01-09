FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY pom.xml ./
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src src
COPY migrations migrations

RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/payments-platform-1.0.0.jar /app/app.jar
COPY migrations /app/migrations

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
