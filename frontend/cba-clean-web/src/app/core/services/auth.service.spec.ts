import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { OidcService } from './oidc.service';

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

  it('should not clear refresh token when access token is expired (non-destructive getToken)', () => {
    const payload = { roles: ['OPERATOR'], exp: Math.floor(Date.now() / 1000) - 10 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() - 1000));
    localStorage.setItem('cba_clean_refresh_token', 'stored-refresh');

    service.loadFromStorage();

    expect(service.getToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    // refresh token must still be present for silent renewal
    expect(localStorage.getItem('cba_clean_refresh_token')).toBe('stored-refresh');
    // getToken should NOT have cleared refresh token
    expect(service.getToken()).toBeNull();
    expect(localStorage.getItem('cba_clean_refresh_token')).toBe('stored-refresh');
  });

  it('should decode base64url payload correctly', () => {
    const payload = { roles: ['OPERATOR'], preferred_username: 'op', exp: Math.floor(Date.now() / 1000) + 3600 };
    const json = JSON.stringify(payload);
    // base64url encode
    const b64 = btoa(json).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const token = `header.${b64}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
    service.loadFromStorage();
    expect(service.hasRole('OPERATOR')).toBe(true);
    expect(service.getUsername()).toBe('op');
  });
});
