# syntax=docker/dockerfile:1
# athena-ingest: loads an OKB into Postgres. Build from the repo root: docker build -f ingest/Dockerfile -t athena-ingest .
FROM ghcr.io/astral-sh/uv:python3.11-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && git config --system --add safe.directory '*'   # the OKB is mounted from the host, owned by another uid
WORKDIR /app
COPY db/schema.sql db/schema.sql
COPY ingest/pyproject.toml ingest/uv.lock ingest/
RUN cd ingest && uv sync --frozen --no-dev --no-install-project
COPY ingest/athena_ingest ingest/athena_ingest
RUN cd ingest && uv sync --frozen --no-dev
WORKDIR /app/ingest
ENTRYPOINT ["uv", "run", "--frozen", "--no-dev", "athena-ingest"]
CMD ["--help"]
