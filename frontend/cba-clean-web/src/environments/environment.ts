export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  incidentApiBaseUrl: 'http://localhost:8081/api/v1',
  keycloak: {
    issuer: 'http://localhost:8090/realms/cba-clean',
    redirectUri: 'http://localhost:4200',
    clientId: 'cba-clean-web',
    scope: 'openid',
  },
};
