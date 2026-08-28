# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine AS runtime

# envsubst is in gettext package on Alpine
RUN apk add --no-cache gettext

# Defaults for Docker Compose networking; overridden in Azure via env vars
ENV REPORT_SERVICE_URL=http://report-service:8080
ENV INCIDENT_SERVICE_URL=http://incident-service:8081
ENV KEYCLOAK_URL=http://keycloak:8080

# Use template + entrypoint to inject ACA FQDNs at startup (no Docker resolver 127.0.0.11 needed)
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

COPY --from=build /app/dist/cba-clean-web/browser /usr/share/nginx/html

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
