# Build em dois estágios: o primeiro compila com Maven+JDK; o segundo leva só
# o JAR para uma imagem enxuta (JRE), sem Maven, código-fonte nem cache.

# ---------- Estágio 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar só o pom.xml primeiro e baixar as dependências: enquanto o pom não
# mudar, essa camada fica em cache e o build seguinte é muito mais rápido.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl: usado pelo healthcheck do Compose (a imagem JRE não o traz).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Não roda como root: se a aplicação for comprometida, o estrago é menor.
RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
