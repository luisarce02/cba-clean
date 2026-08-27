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

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcService);
  private readonly config: OidcConfiguration = environment.keycloak;

  private accessToken: string | null = null;
  private expiresAt: number = 0;

  private readonly authenticated$ = new BehaviorSubject<boolean>(false);
  readonly isAuthenticated$ = this.authenticated$.asObservable();

  constructor() {
    this.loadFromStorage();
  }

  getToken(): string | null {
    if (this.accessToken && this.expiresAt > Date.now()) {
      return this.accessToken;
    }
    if (this.accessToken) {
      this.clearTokens();
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
      const payload = JSON.parse(atob(token.split('.')[1]));
      const roles: string[] = payload['roles'] ?? [];
      return roles.includes(role);
    } catch {
      return false;
    }
  }

  getUsername(): string {
    const token = this.getToken();
    if (!token) return '';
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload['preferred_username'] ?? payload['sub'] ?? '';
    } catch {
      return '';
    }
  }

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

  clearTokens(): void {
    this.accessToken = null;
    this.expiresAt = 0;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    localStorage.removeItem(REFRESH_KEY);
    this.authenticated$.next(false);
  }

  private storeTokens(tokens: AuthTokens): void {
    this.accessToken = tokens.accessToken;
    this.expiresAt = tokens.expiresAt;
    localStorage.setItem(TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(EXPIRES_KEY, String(tokens.expiresAt));
    if (tokens.refreshToken) {
      localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    }
    this.authenticated$.next(true);
  }

  loadFromStorage(): void {
    const token = localStorage.getItem(TOKEN_KEY);
    const expires = localStorage.getItem(EXPIRES_KEY);
    if (token && expires) {
      const expiresAt = Number(expires);
      if (expiresAt > Date.now()) {
        this.accessToken = token;
        this.expiresAt = expiresAt;
        this.authenticated$.next(true);
      } else {
        this.clearTokens();
      }
    }
  }

  private cleanUrl(): void {
    sessionStorage.removeItem(CODE_VERIFIER_KEY);
    sessionStorage.removeItem(STATE_KEY);
    window.history.replaceState({}, document.title, this.config.redirectUri);
  }
}
