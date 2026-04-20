FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre AS runtime
RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup
USER appuser
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /app/data
VOLUME /app/data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]