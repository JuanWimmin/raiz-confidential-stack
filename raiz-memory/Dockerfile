# Rust 1.83 is too old for parts of this dependency tree (sqlx 0.8 / axum 0.7
# pull crates requiring a newer toolchain); 1.90-slim builds clean. Cargo.lock
# is copied on purpose: a reproducible image is worth more than a fresh resolve.
FROM rust:1.90-slim AS build
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends pkg-config libssl-dev \
    && rm -rf /var/lib/apt/lists/*
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/release/raiz-memory /usr/local/bin/raiz-memory

# The image always listens on 8090 regardless of what a mounted .env says, so
# the published port and the process agree. Override with -e PORT only if you
# also change the port mapping.
ENV PORT=8090
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
  CMD curl -fsS http://localhost:8090/health || exit 1
CMD ["raiz-memory"]
