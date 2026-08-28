declare const window: any;

function runtimeEnv(key: string, fallback: string): string {
  return window?.__env?.[key] ?? fallback;
}

// Production environment is build-time fallback only.
// At runtime in Docker/ACA, values are injected via /env.js (docker-entrypoint.sh).
// This allows the same image to run in compose (localhost) and Azure (ACA FQDNs) without rebuild,
// and keeps Nginx same-origin proxy as the default (FRONTEND_*_BASE_URL=/api/v1).
export const environment = {
  production: true,
  apiBaseUrl: runtimeEnv('apiBaseUrl', '/api/v1'),
  incidentApiBaseUrl: runtimeEnv('incidentApiBaseUrl', '/api/v1'),
  keycloak: {
    issuer: runtimeEnv('keycloakIssuer', 'http://localhost:8090/realms/cba-clean'),
    redirectUri: runtimeEnv('keycloakRedirectUri', window?.location?.origin ?? 'http://localhost:4200'),
    clientId: runtimeEnv('keycloakClientId', 'cba-clean-web'),
    scope: runtimeEnv('keycloakScope', 'openid'),
  },
};
