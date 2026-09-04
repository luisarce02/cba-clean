import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import { AuthService } from './auth.service';
import { OidcService } from './oidc.service';

const PENDING_LOGOUT_KEY = 'cba_clean_pending_logout';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        OidcService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    httpMock.verify();
  });

  function authenticateAs(role: string, username?: string): void {
    const payload: Record<string, unknown> = {
      roles: [role],
      exp: Math.floor(Date.now() / 1000) + 3600,
    };
    if (username) {
      payload['preferred_username'] = username;
    }
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
    localStorage.setItem('cba_clean_refresh_token', 'test-refresh-token');
    service.loadFromStorage();
  }

  function mockOidc(oidcService: OidcService): void {
    vi.spyOn(oidcService, 'loadDiscoveryDocument').mockResolvedValue({
      issuer: 'http://localhost:8090/realms/cba-clean',
      authorization_endpoint: 'http://localhost:8090/auth',
      token_endpoint: 'http://localhost:8090/token',
      end_session_endpoint: 'http://localhost:8090/logout',
      jwks_uri: 'http://localhost:8090/certs',
    });
    vi.spyOn(oidcService, 'getEndSessionUrl').mockReturnValue(
      'http://localhost:8090/logout?client_id=cba-clean-web&post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A4200',
    );
  }

  // -------------------------------------------------------------------
  // Basic token tests (unchanged from original)
  // -------------------------------------------------------------------

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return null when no token is set', () => {
    expect(service.getToken()).toBeNull();
  });

  it('should report not authenticated when no token', () => {
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should report authenticated when valid token exists in localStorage', () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    service.loadFromStorage();
    expect(service.isAuthenticated()).toBe(true);
  });

  it('should clear tokens and report not authenticated', () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    service.clearTokens();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.getToken()).toBeNull();
  });

  it('should return false for hasRole when not authenticated', () => {
    expect(service.hasRole('REPORTER')).toBe(false);
  });

  it('should return true for hasRole when token contains matching role', () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    service.loadFromStorage();
    expect(service.hasRole('REPORTER')).toBe(true);
    expect(service.hasRole('OPERATOR')).toBe(false);
  });

  it('should return false for hasRole when token has no roles claim', () => {
    const payload = { exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    service.loadFromStorage();
    expect(service.hasRole('REPORTER')).toBe(false);
  });

  it('should return empty username when not authenticated', () => {
    expect(service.getUsername()).toBe('');
  });

  it('should extract username from token', () => {
    const payload = {
      preferred_username: 'reporter',
      roles: ['REPORTER'],
      exp: Math.floor(Date.now() / 1000) + 3600,
    };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    service.loadFromStorage();
    expect(service.getUsername()).toBe('reporter');
  });

  it('should clear tokens from localStorage', () => {
    localStorage.setItem('cba_clean_access_token', 'test-token');
    localStorage.setItem('cba_clean_expires_at', '12345');
    localStorage.setItem('cba_clean_refresh_token', 'refresh');

    service.clearTokens();

    expect(localStorage.getItem('cba_clean_access_token')).toBeNull();
    expect(localStorage.getItem('cba_clean_expires_at')).toBeNull();
    expect(localStorage.getItem('cba_clean_refresh_token')).toBeNull();
  });

  it('should emit authenticated state via observable', () => {
    let authenticated = false;
    service.isAuthenticated$.subscribe((value) => (authenticated = value));

    expect(authenticated).toBe(false);

    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));

    service.loadFromStorage();
    expect(authenticated).toBe(true);
  });

  it('should not clear refresh token when access token is expired', () => {
    const payload = { roles: ['OPERATOR'], exp: Math.floor(Date.now() / 1000) - 10 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() - 1000));
    localStorage.setItem('cba_clean_refresh_token', 'stored-refresh');

    service.loadFromStorage();

    expect(service.getToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('cba_clean_refresh_token')).toBe('stored-refresh');
  });

  it('should decode base64url payload correctly', () => {
    const payload = { roles: ['OPERATOR'], preferred_username: 'op', exp: Math.floor(Date.now() / 1000) + 3600 };
    const json = JSON.stringify(payload);
    const b64 = btoa(json).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const token = `header.${b64}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
    service.loadFromStorage();
    expect(service.hasRole('OPERATOR')).toBe(true);
    expect(service.getUsername()).toBe('op');
  });

  // -------------------------------------------------------------------
  // Logout flow tests
  // -------------------------------------------------------------------

  describe('logout()', () => {
    let oidcService: OidcService;

    beforeEach(() => {
      oidcService = TestBed.inject(OidcService);
      mockOidc(oidcService);
    });

    it('should set pendingLogout flag in sessionStorage', async () => {
      authenticateAs('REPORTER');
      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBeNull();

      await service.logout();

      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBe('1');
    });

    it('should NOT clear local tokens when initiating logout', async () => {
      authenticateAs('REPORTER');
      await service.logout();

      expect(localStorage.getItem('cba_clean_access_token')).not.toBeNull();
      expect(localStorage.getItem('cba_clean_expires_at')).not.toBeNull();
      expect(localStorage.getItem('cba_clean_refresh_token')).not.toBeNull();
    });

    it('should NOT change authenticated state when initiating logout', async () => {
      authenticateAs('REPORTER');
      expect(service.isAuthenticated()).toBe(true);

      await service.logout();

      expect(service.isAuthenticated()).toBe(true);
    });

    it('should load discovery document and build end session URL', async () => {
      authenticateAs('REPORTER');
      await service.logout();

      expect(oidcService.loadDiscoveryDocument).toHaveBeenCalledOnce();
      expect(oidcService.getEndSessionUrl).toHaveBeenCalledOnce();
    });

    it('should fall back to redirect URI if discovery document fails', async () => {
      authenticateAs('REPORTER');
      vi.spyOn(oidcService, 'loadDiscoveryDocument').mockRejectedValueOnce(new Error('network error'));

      await service.logout();

      expect(localStorage.getItem('cba_clean_access_token')).not.toBeNull();
    });
  });

  // -------------------------------------------------------------------
  // Cancelled logout lifecycle
  //
  // Simulates: user clicks logout → Keycloak page → user cancels → returns.
  // init() detects pendingLogout, attempts refresh — refresh succeeds
  // (Keycloak session still alive) → tokens updated, pendingLogout cleared.
  // -------------------------------------------------------------------

  describe('cancelled logout lifecycle', () => {
    let oidcService: OidcService;

    beforeEach(() => {
      oidcService = TestBed.inject(OidcService);
      mockOidc(oidcService);
    });

    it('init() should attempt refresh and succeed when user cancels logout', async () => {
      authenticateAs('REPORTER', 'reporter1');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      vi.spyOn(oidcService, 'refreshTokens').mockResolvedValue({
        accessToken: 'refreshed-token',
        expiresAt: Date.now() + 3600000,
        refreshToken: 'new-refresh',
      });

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(oidcService.refreshTokens).toHaveBeenCalledOnce();
      expect(freshService.isAuthenticated()).toBe(true);
      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBeNull();
      expect(localStorage.getItem('cba_clean_access_token')).toBe('refreshed-token');
    });

    it('should preserve REPORTER role after cancelled logout', async () => {
      authenticateAs('REPORTER', 'reporter1');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      const refreshedPayload = { roles: ['REPORTER'], preferred_username: 'reporter1', exp: Math.floor(Date.now() / 1000) + 3600 };
      const refreshedToken = `header.${btoa(JSON.stringify(refreshedPayload))}.sig`;
      vi.spyOn(oidcService, 'refreshTokens').mockResolvedValue({
        accessToken: refreshedToken,
        expiresAt: Date.now() + 3600000,
        refreshToken: 'new-refresh',
      });

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(freshService.isAuthenticated()).toBe(true);
      expect(freshService.hasRole('REPORTER')).toBe(true);
    });

    it('should preserve OPERATOR role after cancelled logout', async () => {
      authenticateAs('OPERATOR', 'operator1');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      const refreshedPayload = { roles: ['OPERATOR'], preferred_username: 'operator1', exp: Math.floor(Date.now() / 1000) + 3600 };
      const refreshedToken = `header.${btoa(JSON.stringify(refreshedPayload))}.sig`;
      vi.spyOn(oidcService, 'refreshTokens').mockResolvedValue({
        accessToken: refreshedToken,
        expiresAt: Date.now() + 3600000,
        refreshToken: 'new-refresh',
      });

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(freshService.isAuthenticated()).toBe(true);
      expect(freshService.hasRole('OPERATOR')).toBe(true);
    });

    it('should allow second logout after cancelled logout', async () => {
      authenticateAs('REPORTER');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      vi.spyOn(oidcService, 'refreshTokens').mockResolvedValue({
        accessToken: 'refreshed-token',
        expiresAt: Date.now() + 3600000,
        refreshToken: 'new-refresh',
      });

      const freshService = TestBed.inject(AuthService);
      await freshService.init();
      expect(freshService.isAuthenticated()).toBe(true);

      // Second logout — should work
      await freshService.logout();
      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBe('1');
      expect(localStorage.getItem('cba_clean_access_token')).not.toBeNull();
    });

    it('init() should clear pendingLogout flag after processing', async () => {
      authenticateAs('REPORTER');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      vi.spyOn(oidcService, 'refreshTokens').mockResolvedValue({
        accessToken: 'refreshed-token',
        expiresAt: Date.now() + 3600000,
        refreshToken: 'new-refresh',
      });

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBeNull();
    });

    it('should clear pendingLogout when tokens are successfully stored via tryRefresh()', async () => {
      authenticateAs('REPORTER');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      vi.spyOn(oidcService, 'refreshTokens').mockResolvedValue({
        accessToken: 'new-token',
        expiresAt: Date.now() + 3600000,
        refreshToken: 'new-refresh',
      });

      await service.tryRefresh();

      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBeNull();
    });
  });

  // -------------------------------------------------------------------
  // Confirmed logout lifecycle
  //
  // Simulates: user clicks logout → Keycloak page → user confirms →
  // Keycloak destroys session → refresh token is revoked.
  // init() detects pendingLogout, attempts refresh — refresh FAILS →
  // clearTokens() → user becomes unauthenticated.
  // -------------------------------------------------------------------

  describe('confirmed logout lifecycle', () => {
    let oidcService: OidcService;

    beforeEach(() => {
      oidcService = TestBed.inject(OidcService);
      mockOidc(oidcService);
    });

    it('init() should clear tokens when refresh fails after confirmed logout', async () => {
      authenticateAs('REPORTER');
      expect(service.isAuthenticated()).toBe(true);
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      vi.spyOn(oidcService, 'refreshTokens').mockRejectedValue(new Error('invalid_grant'));

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(freshService.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('cba_clean_access_token')).toBeNull();
      expect(localStorage.getItem('cba_clean_refresh_token')).toBeNull();
      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBeNull();
    });

    it('init() should clear tokens for OPERATOR after confirmed logout', async () => {
      authenticateAs('OPERATOR');
      expect(service.isAuthenticated()).toBe(true);
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      vi.spyOn(oidcService, 'refreshTokens').mockRejectedValue(new Error('invalid_grant'));

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(freshService.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('cba_clean_access_token')).toBeNull();
      expect(localStorage.getItem('cba_clean_refresh_token')).toBeNull();
    });

    it('init() should emit unauthenticated via isAuthenticated$ after confirmed logout', async () => {
      authenticateAs('REPORTER');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');

      vi.spyOn(oidcService, 'refreshTokens').mockRejectedValue(new Error('invalid_grant'));

      const freshService = TestBed.inject(AuthService);
      let authenticated = true;
      freshService.isAuthenticated$.subscribe((v) => (authenticated = v));

      await freshService.init();

      expect(authenticated).toBe(false);
    });

    it('init() should clear tokens when no refresh token exists during pendingLogout', async () => {
      authenticateAs('REPORTER');
      sessionStorage.setItem(PENDING_LOGOUT_KEY, '1');
      // Remove refresh token
      localStorage.removeItem('cba_clean_refresh_token');

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(freshService.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('cba_clean_access_token')).toBeNull();
      expect(sessionStorage.getItem(PENDING_LOGOUT_KEY)).toBeNull();
    });

    it('init() should attempt refresh normally when pendingLogout is NOT set', async () => {
      // Expired access token with valid refresh token — should refresh
      const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) - 10 };
      const token = `header.${btoa(JSON.stringify(payload))}.sig`;
      localStorage.setItem('cba_clean_access_token', token);
      localStorage.setItem('cba_clean_expires_at', String(Date.now() - 1000));
      localStorage.setItem('cba_clean_refresh_token', 'valid-refresh');

      vi.spyOn(oidcService, 'refreshTokens').mockResolvedValue({
        accessToken: 'refreshed-token',
        expiresAt: Date.now() + 3600000,
        refreshToken: 'new-refresh',
      });

      const freshService = TestBed.inject(AuthService);
      await freshService.init();

      expect(oidcService.refreshTokens).toHaveBeenCalledOnce();
      expect(freshService.isAuthenticated()).toBe(true);
    });

    it('should clear tokens when refresh fails after confirmed logout (tryRefresh)', async () => {
      authenticateAs('REPORTER');
      expect(service.isAuthenticated()).toBe(true);

      await service.logout();
      expect(service.isAuthenticated()).toBe(true);

      // User confirms logout → Keycloak destroys session → refresh fails
      vi.spyOn(oidcService, 'refreshTokens').mockRejectedValue(new Error('invalid_grant'));

      const refreshResult = await service.tryRefresh();

      expect(refreshResult).toBe(false);
      expect(service.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('cba_clean_access_token')).toBeNull();
      expect(localStorage.getItem('cba_clean_refresh_token')).toBeNull();
    });

    it('should clear tokens when refresh fails for OPERATOR after confirmed logout', async () => {
      authenticateAs('OPERATOR');
      expect(service.isAuthenticated()).toBe(true);

      await service.logout();
      expect(service.isAuthenticated()).toBe(true);

      vi.spyOn(oidcService, 'refreshTokens').mockRejectedValue(new Error('invalid_grant'));

      const refreshResult = await service.tryRefresh();

      expect(refreshResult).toBe(false);
      expect(service.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('cba_clean_access_token')).toBeNull();
    });

    it('after confirmed logout, isAuthenticated$ should emit false', async () => {
      authenticateAs('REPORTER');
      let authenticated = true;
      service.isAuthenticated$.subscribe((v) => (authenticated = v));

      await service.logout();

      vi.spyOn(oidcService, 'refreshTokens').mockRejectedValue(new Error('invalid_grant'));
      await service.tryRefresh();

      expect(authenticated).toBe(false);
    });
  });
});
