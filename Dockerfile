
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Primeiro só o pom: enquanto ele não mudar, a camada de dependências fica em cache
# e os builds seguintes não baixam tudo de novo.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Os testes rodam no CI (./mvnw verify); aqui só empacota.
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Não roda como root.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home app

COPY --from=build /workspace/target/crossborder-api-*.jar app.jar

USER 10001
EXPOSE 8080

# O "dia" do limite diário e da cotação é o dia no Brasil. Sem isso o container roda em UTC e o dia
# viraria às 21h de Brasília (o limite resetaria 3 horas antes da meia-noite).
ENV TZ=America/Sao_Paulo

# A JVM já enxerga o limite de memória do container; isso só define quanto dele vira heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# O servidor TCP do H2 (porta 9092) é conveniência de desenvolvimento: fora do container.
ENTRYPOINT ["java", "-jar", "app.jar", "--app.h2-tcp-server.enabled=false"]
