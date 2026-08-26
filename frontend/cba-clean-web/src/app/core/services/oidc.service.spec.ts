import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { OidcService } from './oidc.service';

describe('OidcService', () => {
  let service: OidcService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        OidcService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(OidcService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should load discovery document from issuer', async () => {
    const mockDiscovery = {
      issuer: 'http://localhost:8090/realms/cba-clean',
      authorization_endpoint: 'http://localhost:8090/realms/cba-clean/protocol/openid-connect/auth',
      token_endpoint: 'http://localhost:8090/realms/cba-clean/protocol/openid-connect/token',
      end_session_endpoint: 'http://localhost:8090/realms/cba-clean/protocol/openid-connect/logout',
      jwks_uri: 'http://localhost:8090/realms/cba-clean/protocol/openid-connect/certs',
    };

    const resultPromise = service.loadDiscoveryDocument('http://localhost:8090/realms/cba-clean');

    const req = httpMock.expectOne(
      'http://localhost:8090/realms/cba-clean/.well-known/openid-configuration',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockDiscovery);

    const result = await resultPromise;
    expect(result.issuer).toBe('http://localhost:8090/realms/cba-clean');
    expect(result.authorization_endpoint).toContain('/auth');
    expect(result.token_endpoint).toContain('/token');
  });

  it('should cache discovery document', async () => {
    const issuer = 'http://localhost:8090/realms/cba-cache-unique-v2';
    const mockDiscovery = {
      issuer,
      authorization_endpoint: 'http://localhost:8090/auth',
      token_endpoint: 'http://localhost:8090/token',
      end_session_endpoint: 'http://localhost:8090/logout',
      jwks_uri: 'http://localhost:8090/certs',
    };

    const firstCall = service.loadDiscoveryDocument(issuer);
    const req = httpMock.expectOne(
      `${issuer}/.well-known/openid-configuration`,
    );
    req.flush(mockDiscovery);
    await firstCall;

    // Second call should use cache - no HTTP request expected
    const result = await service.loadDiscoveryDocument(issuer);
    expect(result.issuer).toBe(issuer);
  });

  it('should generate code verifier of correct length', () => {
    const verifier = service.generateCodeVerifier();
    expect(verifier).toBeTruthy();
    expect(verifier.length).toBeGreaterThan(20);
  });

  it('should generate different code verifiers', () => {
    const v1 = service.generateCodeVerifier();
    const v2 = service.generateCodeVerifier();
    expect(v1).not.toBe(v2);
  });

  it('should generate code challenge from verifier', async () => {
    const verifier = service.generateCodeVerifier();
    const challenge = await service.generateCodeChallenge(verifier);
    expect(challenge).toBeTruthy();
    expect(challenge).not.toBe(verifier);
  });

  it('should build authorization URL with required params', () => {
    const discovery = {
      issuer: 'http://localhost:8090/realms/cba-clean',
      authorization_endpoint: 'http://localhost:8090/auth',
      token_endpoint: 'http://localhost:8090/token',
      end_session_endpoint: 'http://localhost:8090/logout',
      jwks_uri: 'http://localhost:8090/certs',
    };
    const config = {
      issuer: 'http://localhost:8090/realms/cba-clean',
      clientId: 'cba-clean-web',
      redirectUri: 'http://localhost:4200',
      scope: 'openid',
    };

    const url = service.buildAuthorizationUrl(discovery, config, 'test-challenge', 'test-state');

    expect(url).toContain('client_id=cba-clean-web');
    expect(url).toContain('response_type=code');
    expect(url).toContain('redirect_uri=');
    expect(url).toContain('scope=openid');
    expect(url).toContain('state=test-state');
    expect(url).toContain('code_challenge=test-challenge');
    expect(url).toContain('code_challenge_method=S256');
  });

  it('should build end session URL', () => {
    const discovery = {
      issuer: 'http://localhost:8090/realms/cba-clean',
      authorization_endpoint: 'http://localhost:8090/auth',
      token_endpoint: 'http://localhost:8090/token',
      end_session_endpoint: 'http://localhost:8090/logout',
      jwks_uri: 'http://localhost:8090/certs',
    };
    const config = {
      issuer: 'http://localhost:8090/realms/cba-clean',
      clientId: 'cba-clean-web',
      redirectUri: 'http://localhost:4200',
      scope: 'openid',
    };

    const url = service.getEndSessionUrl(discovery, config);

    expect(url).toContain('client_id=cba-clean-web');
    expect(url).toContain('post_logout_redirect_uri=');
  });

  it('should exchange code for tokens', async () => {
    const discovery = {
      issuer: 'http://localhost:8090/realms/cba-clean',
      authorization_endpoint: 'http://localhost:8090/auth',
      token_endpoint: 'http://localhost:8090/token',
      end_session_endpoint: 'http://localhost:8090/logout',
      jwks_uri: 'http://localhost:8090/certs',
    };
    const config = {
      issuer: 'http://localhost:8090/realms/cba-clean',
      clientId: 'cba-clean-web',
      redirectUri: 'http://localhost:4200',
      scope: 'openid',
    };

    const tokenPromise = service.exchangeCodeForTokens(
      discovery,
      config,
      'test-code',
      'test-verifier',
    );

    const req = httpMock.expectOne('http://localhost:8090/token');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    req.flush({
      access_token: 'test-access-token',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'test-refresh-token',
    });

    const tokens = await tokenPromise;
    expect(tokens.accessToken).toBe('test-access-token');
    expect(tokens.expiresAt).toBeGreaterThan(Date.now());
    expect(tokens.refreshToken).toBe('test-refresh-token');
  });
});
