import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { OidcService } from '../services/oidc.service';
import { KEYCLOAK_ISSUER } from '../utils/keycloak-url.util';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let authService: AuthService;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        { provide: KEYCLOAK_ISSUER, useValue: 'http://localhost:8090/realms/cba-clean' },
        AuthService,
        OidcService,
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    httpMock.verify();
  });

  it('should not add Authorization header when no token exists', () => {
    httpClient.get('/test').subscribe();

    const req = httpMock.expectOne('/test');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should add Authorization header when token exists', () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    authService.loadFromStorage();

    httpClient.get('/test').subscribe();

    const req = httpMock.expectOne('/test');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    req.flush({});
  });

  it('should clear token and stop adding header', () => {
    authService.clearTokens();

    httpClient.get('/test').subscribe();

    const req = httpMock.expectOne('/test');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should not add Authorization to Keycloak OIDC discovery request even when token exists', () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    authService.loadFromStorage();

    httpClient
      .get('http://localhost:8090/realms/cba-clean/.well-known/openid-configuration')
      .subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8090/realms/cba-clean/.well-known/openid-configuration',
    );
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should not add Authorization to Keycloak token endpoint even when token exists', () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    authService.loadFromStorage();

    httpClient
      .post('http://localhost:8090/realms/cba-clean/protocol/openid-connect/token', '')
      .subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8090/realms/cba-clean/protocol/openid-connect/token',
    );
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should add Authorization to backend API requests when token exists', () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    authService.loadFromStorage();

    httpClient.get('http://localhost:8080/api/v1/reports').subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    req.flush({});
  });
});
