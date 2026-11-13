# syntax=docker/dockerfile:1
# athena-mcp: the Kotlin server (REST + MCP over Streamable HTTP). Build: docker build -t athena-mcp .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null   # cache dependencies in their own layer
COPY src src
RUN ./gradlew --no-daemon installDist -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /tmp athena
COPY --from=build /src/build/install/athena-mcp /opt/athena-mcp
USER athena
# The model is mounted at /models (see docker-compose.yml); without it, search is lexical only.
ENV PORT=8080 \
    ATHENA_MODEL_DIR=/models \
    DJL_CACHE_DIR=/tmp/djl
EXPOSE 8080
ENTRYPOINT ["/opt/athena-mcp/bin/athena-mcp"]
CMD ["serve"]
