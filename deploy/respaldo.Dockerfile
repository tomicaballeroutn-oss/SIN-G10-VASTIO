FROM postgres:17
# Clientes PostgreSQL 17 y flock existentes; rclone sin credenciales en la imagen.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates rclone \
    && rm -rf /var/lib/apt/lists/*
