# Frontend compilado servido por Caddy, que además hace de proxy a /api y obtiene el certificado HTTPS.
# El Caddyfile se monta desde deploy/Caddyfile (ver docker-compose.prod.yml).
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM caddy:2
COPY --from=build /app/dist /srv
