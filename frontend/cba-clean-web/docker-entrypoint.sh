#!/bin/sh
set -eu

# Default upstreams for Docker Compose compatibility.
# In Azure these are overridden via Container Apps environment variables.
: "${REPORT_SERVICE_URL:=http://report-service:8080}"
: "${INCIDENT_SERVICE_URL:=http://incident-service:8081}"
: "${KEYCLOAK_URL:=http://keycloak:8080}"

# Runtime frontend env for OIDC / API URLs (injected into window.__env via env.js).
# Default to same-origin proxy + local Keycloak for compose; override in Azure.
: "${FRONTEND_API_BASE_URL:=/api/v1}"
: "${FRONTEND_INCIDENT_API_BASE_URL:=/api/v1}"
: "${FRONTEND_KEYCLOAK_ISSUER:=http://localhost:8090/realms/cba-clean}"
: "${FRONTEND_KEYCLOAK_REDIRECT_URI:=}"
: "${FRONTEND_KEYCLOAK_CLIENT_ID:=cba-clean-web}"
: "${FRONTEND_KEYCLOAK_SCOPE:=openid}"

if [ -z "${FRONTEND_KEYCLOAK_REDIRECT_URI}" ]; then
  # If not explicitly set, use the request origin at runtime - fallback is handled in Angular
  FRONTEND_KEYCLOAK_REDIRECT_URI="__FRONTEND_ORIGIN__"
fi

# Generate nginx.conf from template
# Only REPORT_SERVICE_URL / INCIDENT_SERVICE_URL / KEYCLOAK_URL are templated inside nginx.conf
export REPORT_SERVICE_URL INCIDENT_SERVICE_URL KEYCLOAK_URL
envsubst '${REPORT_SERVICE_URL} ${INCIDENT_SERVICE_URL} ${KEYCLOAK_URL}' \
  < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/conf.d/default.conf

# Generate runtime env.js consumed by Angular (src/environments/environment.ts reads window.__env)
cat > /usr/share/nginx/html/env.js <<EOF
(function(window){
  window.__env = window.__env || {};
  window.__env.apiBaseUrl = "${FRONTEND_API_BASE_URL}";
  window.__env.incidentApiBaseUrl = "${FRONTEND_INCIDENT_API_BASE_URL}";
  window.__env.keycloakIssuer = "${FRONTEND_KEYCLOAK_ISSUER}";
  window.__env.keycloakRedirectUri = "${FRONTEND_KEYCLOAK_REDIRECT_URI}";
  window.__env.keycloakClientId = "${FRONTEND_KEYCLOAK_CLIENT_ID}";
  window.__env.keycloakScope = "${FRONTEND_KEYCLOAK_SCOPE}";
  // If redirect URI was left as origin placeholder, resolve it in browser
  if (window.__env.keycloakRedirectUri === "__FRONTEND_ORIGIN__") {
    window.__env.keycloakRedirectUri = window.location.origin;
  }
})(this);
EOF

echo "[entrypoint] nginx config generated:"
cat /etc/nginx/conf.d/default.conf
echo "[entrypoint] env.js generated:"
cat /usr/share/nginx/html/env.js

exec nginx -g 'daemon off;'
