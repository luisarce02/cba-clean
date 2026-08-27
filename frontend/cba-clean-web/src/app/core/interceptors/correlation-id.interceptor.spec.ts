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
import { correlationIdInterceptor } from './correlation-id.interceptor';
import { KEYCLOAK_ISSUER } from '../utils/keycloak-url.util';

describe('correlationIdInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: KEYCLOAK_ISSUER, useValue: 'http://localhost:8090/realms/cba-clean' },
        provideHttpClient(withInterceptors([correlationIdInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should add X-Correlation-ID header when not present', () => {
    httpClient.get('/test').subscribe();

    const req = httpMock.expectOne('/test');
    expect(req.request.headers.has('X-Correlation-ID')).toBe(true);
    const correlationId = req.request.headers.get('X-Correlation-ID');
    expect(correlationId).toBeTruthy();
    expect(correlationId!.length).toBeGreaterThan(0);
    req.flush({});
  });

  it('should preserve existing X-Correlation-ID header', () => {
    const existingId = 'existing-correlation-id-123';
    httpClient
      .get('/test', { headers: { 'X-Correlation-ID': existingId } })
      .subscribe();

    const req = httpMock.expectOne('/test');
    expect(req.request.headers.get('X-Correlation-ID')).toBe(existingId);
    req.flush({});
  });

  it('should not add X-Correlation-ID to Keycloak OIDC discovery request', () => {
    httpClient
      .get('http://localhost:8090/realms/cba-clean/.well-known/openid-configuration')
      .subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8090/realms/cba-clean/.well-known/openid-configuration',
    );
    expect(req.request.headers.has('X-Correlation-ID')).toBe(false);
    req.flush({});
  });

  it('should not add X-Correlation-ID to Keycloak token endpoint', () => {
    httpClient
      .post('http://localhost:8090/realms/cba-clean/protocol/openid-connect/token', '')
      .subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8090/realms/cba-clean/protocol/openid-connect/token',
    );
    expect(req.request.headers.has('X-Correlation-ID')).toBe(false);
    req.flush({});
  });

  it('should not add X-Correlation-ID to Keycloak JWKS endpoint', () => {
    httpClient
      .get('http://localhost:8090/realms/cba-clean/protocol/openid-connect/certs')
      .subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8090/realms/cba-clean/protocol/openid-connect/certs',
    );
    expect(req.request.headers.has('X-Correlation-ID')).toBe(false);
    req.flush({});
  });

  it('should add X-Correlation-ID to application backend requests', () => {
    httpClient.get('http://localhost:8080/api/v1/reports').subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    expect(req.request.headers.has('X-Correlation-ID')).toBe(true);
    req.flush({});
  });
});
