# -----------------------------------------------------------------------------
# Stage 1: Build da aplicação
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copia os arquivos de configuração do Gradle primeiro (para reaproveitar cache de camadas)
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./

# Concede permissão de execução ao gradlew e baixa as dependências
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copia o código-fonte da aplicação
COPY src/ src/

# Compila o projeto gerando o JAR executável
RUN ./gradlew bootJar --no-daemon -x test

# -----------------------------------------------------------------------------
# Stage 2: Runtime da aplicação (imagem final leve)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Cria um usuário não-root por questões de segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copia o JAR compilado no stage anterior
COPY --from=builder /app/build/libs/*.jar app.jar

# Expõe a porta padrão do Spring Boot
EXPOSE 8080

# Define limites de memória para a JVM adequados a containers pequenos
ENTRYPOINT ["java", "-jar", "app.jar"]