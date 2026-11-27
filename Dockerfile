# syntax=docker/dockerfile:1

# Imagem da API Juridora em dois estágios:
#   1. build: compila com o JDK 25 (Temurin, a mesma distribuição do CI) e extrai as camadas do jar;
#   2. runtime: executa só com o JRE 25, como usuário sem privilégios.
#
# Os testes não rodam aqui: o CI já executa `./mvnw verify` e o Testcontainers precisaria de Docker
# dentro do build.
#
# Construir:  docker build -t juridora-api .
# Executar:   docker run -p 8080:8080 \
#               -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/juridora \
#               -e SPRING_DATASOURCE_USERNAME=app_user -e SPRING_DATASOURCE_PASSWORD=... \
#               -e SPRING_FLYWAY_USER=app_owner -e SPRING_FLYWAY_PASSWORD=... \
#               -e JWT_CHAVE_PRIVADA=... -e JWT_CHAVE_PUBLICA=... \
#               juridora-api

# ---------- Estágio 1: build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Dependências primeiro: a camada só é refeita quando o pom.xml ou o wrapper mudam.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests \
    && cp target/juridora-api-*.jar application.jar

# Separa o jar em camadas (dependências mudam pouco; a aplicação muda sempre),
# para o Docker reaproveitar as camadas de dependências entre versões.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system juridora && useradd --system --gid juridora --no-create-home juridora

COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER juridora

# Heap proporcional ao limite de memória do contêiner.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
