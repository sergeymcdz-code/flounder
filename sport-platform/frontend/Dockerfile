# Frontend image: React + Vite static build served by Caddy.
# Stage 1 builds dist/ with Node, stage 2 serves it on port 80 with SPA fallback.

# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /build

# VITE_* variables are inlined at build time, so the API base URL is a build arg
# (passed from infra/compose.prod.yaml as https://${DOMAIN}).
ARG VITE_SENTRY_DSN
ENV VITE_SENTRY_DSN=${VITE_SENTRY_DSN}
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- runtime ----------
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist /srv
EXPOSE 80
