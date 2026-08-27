import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { OidcService, OidcConfiguration, AuthTokens } from './oidc.service';
import { environment } from '../../../environments/environment';

const STORAGE_KEY_PREFIX = 'cba_clean_';
const TOKEN_KEY = `${STORAGE_KEY_PREFIX}access_token`;
const EXPIRES_KEY = `${STORAGE_KEY_PREFIX}expires_at`;
const REFRESH_KEY = `${STORAGE_KEY_PREFIX}refresh_token`;
const CODE_VERIFIER_KEY = `${STORAGE_KEY_PREFIX}code_verifier`;
const STATE_KEY = `${STORAGE_KEY_PREFIX}oauth_state`;

// Refresh 60s before expiry
const REFRESH_BUFFER_MS = 60_000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcService);
  private readonly config: OidcConfiguration = environment.keycloak;

  private accessToken: string | null = null;
  private expiresAt: number = 0;
  private refreshTokenValue: string | null = null;

  private readonly authenticated$ = new BehaviorSubject<boolean>(false);
  readonly isAuthenticated$ = this.authenticated$.asObservable();

  private refreshPromise: Promise<boolean> | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private initPromise: Promise<void> | null = null;
  private initialized = false;

  constructor() {
    this.loadFromStorage();
  }

  // ---- Token helpers (non-destructive) ----

  /**
   * Returns current access token if still valid, otherwise null.
   * Does NOT clear storage – use clearTokens() or tryRefresh() explicitly.
   */
  getToken(): string | null {
    if (this.accessToken && this.expiresAt > Date.now()) {
      return this.accessToken;
    }
    return null;
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  hasRole(role: string): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = this.decodePayload(token);
      const roles = (payload['roles'] as string[] | undefined) ?? [];
      return roles.includes(role);
    } catch {
      return false;
    }
  }

  getUsername(): string {
    const token = this.getToken();
    if (!token) return '';
    try {
      const payload = this.decodePayload(token);
      return (payload['preferred_username'] as string | undefined) ??
        (payload['sub'] as string | undefined) ?? '';
    } catch {
      return '';
    }
  }

  isExpiringSoon(bufferMs = REFRESH_BUFFER_MS): boolean {
    if (!this.accessToken || !this.expiresAt) return false;
    return this.expiresAt - Date.now() < bufferMs;
  }

  isTokenExpired(): boolean {
    return !this.accessToken || this.expiresAt <= Date.now();
  }

  // ---- Initialization ----

  /**
   * Called via APP_INITIALIZER. Restores storage and attempts silent refresh
   * if access token is expired but refresh token exists.
   */
  async init(): Promise<void> {
    if (this.initPromise) return this.initPromise;
    this.initPromise = (async () => {
      // loadFromStorage already called in constructor; re-load in case storage changed
      this.loadFromStorage();
      if (this.isTokenExpired() && this.getStoredRefreshToken()) {
        // Try to refresh silently – if it fails, user will be unauthenticated
        await this.tryRefresh();
      } else if (this.accessToken && this.isExpiringSoon()) {
        // Token close to expiry on boot – refresh proactively but don't block long
        // Fire and forget, but await briefly? We await to ensure guards see fresh token
        await this.tryRefresh();
      }
      this.initialized = true;
    })();
    return this.initPromise;
  }

  async whenReady(): Promise<void> {
    if (this.initPromise) {
      await this.initPromise;
    }
  }

  /**
   * Ensures we have a valid token; if expiring soon or expired and refresh token
   * exists, attempts refresh. Returns true if authenticated after the attempt.
   */
  async refreshIfNeeded(): Promise<boolean> {
    await this.whenReady();
    if (this.isAuthenticated() && !this.isExpiringSoon()) {
      return true;
    }
    if (this.getStoredRefreshToken()) {
      if (this.isTokenExpired() || this.isExpiringSoon()) {
        const ok = await this.tryRefresh();
        return ok;
      }
    }
    return this.isAuthenticated();
  }

  // ---- OIDC flows ----

  async login(): Promise<void> {
    const discovery = await this.oidc.loadDiscoveryDocument(this.config.issuer);
    const codeVerifier = this.oidc.generateCodeVerifier();
    const codeChallenge = await this.oidc.generateCodeChallenge(codeVerifier);
    const state = crypto.randomUUID();

    sessionStorage.setItem(CODE_VERIFIER_KEY, codeVerifier);
    sessionStorage.setItem(STATE_KEY, state);

    const authUrl = this.oidc.buildAuthorizationUrl(
      discovery,
      this.config,
      codeChallenge,
      state,
    );
    window.location.href = authUrl;
  }

  async handleCallback(): Promise<boolean> {
    const urlParams = new URLSearchParams(window.location.search);
    const code = urlParams.get('code');
    const state = urlParams.get('state');
    const error = urlParams.get('error');

    if (error) {
      this.cleanUrl();
      return false;
    }

    if (!code || !state) {
      return false;
    }

    const savedState = sessionStorage.getItem(STATE_KEY);
    if (state !== savedState) {
      this.cleanUrl();
      return false;
    }

    const codeVerifier = sessionStorage.getItem(CODE_VERIFIER_KEY);
    if (!codeVerifier) {
      this.cleanUrl();
      return false;
    }

    try {
      const discovery = await this.oidc.loadDiscoveryDocument(this.config.issuer);
      const tokens = await this.oidc.exchangeCodeForTokens(
        discovery,
        this.config,
        code,
        codeVerifier,
      );
      this.storeTokens(tokens);
      this.cleanUrl();
      return true;
    } catch {
      this.cleanUrl();
      return false;
    }
  }

  async logout(): Promise<void> {
    this.clearTokens();
    try {
      const discovery = await this.oidc.loadDiscoveryDocument(this.config.issuer);
      const endSessionUrl = this.oidc.getEndSessionUrl(discovery, this.config);
      window.location.href = endSessionUrl;
    } catch {
      window.location.href = this.config.redirectUri;
    }
  }

  // ---- Refresh lifecycle (single-flight) ----

  /**
   * Attempts to obtain a new access token using the stored refresh token.
   * Concurrent callers share the same underlying HTTP request.
   * Returns true on success, false on failure (and clears auth state).
   */
  async tryRefresh(): Promise<boolean> {
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    const refreshToken = this.getStoredRefreshToken();
    if (!refreshToken) {
      return false;
    }

    this.refreshPromise = (async () => {
      try {
        const discovery = await this.oidc.loadDiscoveryDocument(this.config.issuer);
        const tokens = await this.oidc.refreshTokens(discovery, this.config, refreshToken);
        this.storeTokens(tokens);
        return true;
      } catch {
        this.clearTokens();
        return false;
      } finally {
        this.refreshPromise = null;
      }
    })();

    return this.refreshPromise;
  }

  clearTokens(): void {
    this.accessToken = null;
    this.expiresAt = 0;
    this.refreshTokenValue = null;
    this.clearRefreshTimer();
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    localStorage.removeItem(REFRESH_KEY);
    this.authenticated$.next(false);
  }

  private storeTokens(tokens: AuthTokens): void {
    this.accessToken = tokens.accessToken;
    this.expiresAt = tokens.expiresAt;
    this.refreshTokenValue = tokens.refreshToken ?? this.refreshTokenValue;
    localStorage.setItem(TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(EXPIRES_KEY, String(tokens.expiresAt));
    if (tokens.refreshToken) {
      localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    }
    this.authenticated$.next(true);
    this.scheduleRefresh();
  }

  loadFromStorage(): void {
    const token = localStorage.getItem(TOKEN_KEY);
    const expires = localStorage.getItem(EXPIRES_KEY);
    const refresh = localStorage.getItem(REFRESH_KEY);

    this.refreshTokenValue = refresh;

    if (token && expires) {
      const expiresAt = Number(expires);
      // Keep token in memory even if expired if we have a refresh token –
      // tryRefresh() can still use the refresh token. But isAuthenticated()
      // will correctly report false until refresh succeeds.
      if (expiresAt > Date.now()) {
        this.accessToken = token;
        this.expiresAt = expiresAt;
        this.authenticated$.next(true);
        this.scheduleRefresh();
      } else {
        // Expired: keep accessToken null but preserve refresh token for silent renew.
        // Do NOT clear refresh_token here – it is needed for tryRefresh.
        this.accessToken = null;
        this.expiresAt = 0;
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(EXPIRES_KEY);
        this.authenticated$.next(false);
        this.clearRefreshTimer();
        // refresh token stays in storage
        if (refresh) {
          this.refreshTokenValue = refresh;
        }
      }
    } else {
      // No valid token
      this.accessToken = null;
      this.expiresAt = 0;
      if (!refresh) {
        this.authenticated$.next(false);
      } else {
        this.authenticated$.next(false);
      }
      this.clearRefreshTimer();
    }
  }

  private getStoredRefreshToken(): string | null {
    return this.refreshTokenValue ?? localStorage.getItem(REFRESH_KEY);
  }

  private scheduleRefresh(): void {
    this.clearRefreshTimer();
    if (!this.accessToken || !this.expiresAt) return;
    const delay = this.expiresAt - Date.now() - REFRESH_BUFFER_MS;
    const safeDelay = Math.max(0, delay);
    // If already expiring soon, refresh shortly (1s) to avoid tight loops
    const finalDelay = safeDelay === 0 ? 1000 : safeDelay;
    // Don't schedule if delay is huge (e.g., > 1 hour) – still schedule though
    this.refreshTimer = setTimeout(async () => {
      if (this.getStoredRefreshToken()) {
        await this.tryRefresh();
      }
    }, finalDelay);
  }

  private clearRefreshTimer(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  private decodePayload(token: string): Record<string, unknown> {
    const payload = token.split('.')[1];
    // base64url -> base64
    let base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const pad = base64.length % 4;
    if (pad) base64 += '='.repeat(4 - pad);
    const json = atob(base64);
    return JSON.parse(json);
  }

  private cleanUrl(): void {
    sessionStorage.removeItem(CODE_VERIFIER_KEY);
    sessionStorage.removeItem(STATE_KEY);
    window.history.replaceState({}, document.title, this.config.redirectUri);
  }
}
