# Estágio 1: build com Maven (pode ser Gradle)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Baixa dependências primeiro (camada de cache)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Estágio 2: runtime mínimo
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]