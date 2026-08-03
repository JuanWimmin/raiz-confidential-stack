FROM rust:1.83-slim AS build
WORKDIR /app
COPY Cargo.toml ./
COPY src ./src
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/release/raiz-memory /usr/local/bin/raiz-memory
EXPOSE 8090
CMD ["raiz-memory"]
