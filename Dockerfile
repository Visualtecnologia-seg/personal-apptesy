# ---------- Etapa 1: compilar o backend ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Baixa as dependências primeiro (fica em cache entre deploys)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline -Djava.version=17 || true

# Compila o projeto
COPY src src
RUN mvn -q -B package -DskipTests -Djava.version=17 && cp target/*.jar /app/app.jar

# ---------- Etapa 2: imagem leve só para rodar ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
RUN mkdir -p logs

# Render/Railway informam a porta pela variável PORT
ENV PORT=8080
EXPOSE 8080

# Limite de memória pensado para planos gratuitos (512 MB)
ENV JAVA_OPTS="-Xmx350m -Xss512k -XX:+UseSerialGC -Duser.timezone=America/Sao_Paulo"

CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]
