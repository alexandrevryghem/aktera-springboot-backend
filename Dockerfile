FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom first so Docker caches the dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -q -DskipTests

FROM eclipse-temurin:21-jre-jammy

# Non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Own the file as the non-root user
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
