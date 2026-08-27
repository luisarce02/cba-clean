import { TestBed } from '@angular/core/testing';
import { isKeycloakRequest, KEYCLOAK_ISSUER } from './keycloak-url.util';

describe('isKeycloakRequest', () => {
  const keycloakIssuer = 'http://localhost:8090/realms/cba-clean';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: KEYCLOAK_ISSUER, useValue: keycloakIssuer },
      ],
    });
  });

  it('should return true for Keycloak OIDC discovery URL', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8090/realms/cba-clean/.well-known/openid-configuration')).toBe(true);
    });
  });

  it('should return true for Keycloak token endpoint', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8090/realms/cba-clean/protocol/openid-connect/token')).toBe(true);
    });
  });

  it('should return true for Keycloak JWKS endpoint', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8090/realms/cba-clean/protocol/openid-connect/certs')).toBe(true);
    });
  });

  it('should return true for Keycloak authorization endpoint', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8090/realms/cba-clean/protocol/openid-connect/auth')).toBe(true);
    });
  });

  it('should return true for Keycloak logout endpoint', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8090/realms/cba-clean/protocol/openid-connect/logout')).toBe(true);
    });
  });

  it('should return false for application backend URL', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8080/api/v1/reports')).toBe(false);
    });
  });

  it('should return false for incident service URL', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8081/api/v1/incidents')).toBe(false);
    });
  });

  it('should return false for different port on same host', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8091/realms/cba-clean/.well-known/openid-configuration')).toBe(false);
    });
  });

  it('should return false for different host', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://keycloak:8080/realms/cba-clean/.well-known/openid-configuration')).toBe(false);
    });
  });

  it('should return false for relative URL', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('/realms/cba-clean/.well-known/openid-configuration')).toBe(false);
    });
  });

  it('should return false for malformed URL', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('not-a-url')).toBe(false);
    });
  });
});

describe('isKeycloakRequest with empty issuer', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: KEYCLOAK_ISSUER, useValue: '' },
      ],
    });
  });

  it('should return false for any URL when issuer is empty', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8090/realms/cba-clean/.well-known/openid-configuration')).toBe(false);
    });
  });

  it('should return false for backend URL when issuer is empty', () => {
    TestBed.runInInjectionContext(() => {
      expect(isKeycloakRequest('http://localhost:8080/api/v1/reports')).toBe(false);
    });
  });
});
