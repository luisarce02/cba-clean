import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface OidcConfiguration {
  issuer: string;
  clientId: string;
  redirectUri: string;
  scope: string;
}

export interface OidcDiscoveryDocument {
  authorization_endpoint: string;
  token_endpoint: string;
  end_session_endpoint: string;
  jwks_uri: string;
  issuer: string;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token?: string;
  scope?: string;
}

export interface AuthTokens {
  accessToken: string;
  expiresAt: number;
  refreshToken?: string;
}

@Injectable({ providedIn: 'root' })
export class OidcService {
  private discoveryDoc: OidcDiscoveryDocument | null = null;

  constructor(private http: HttpClient) {}

  async loadDiscoveryDocument(issuer: string): Promise<OidcDiscoveryDocument> {
    if (this.discoveryDoc && this.discoveryDoc.issuer === issuer) {
      return this.discoveryDoc;
    }
    const url = `${issuer}/.well-known/openid-configuration`;
    this.discoveryDoc = await firstValueFrom(
      this.http.get<OidcDiscoveryDocument>(url),
    );
    return this.discoveryDoc;
  }

  generateCodeVerifier(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return this.base64UrlEncode(array);
  }

  async generateCodeChallenge(codeVerifier: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return this.base64UrlEncode(new Uint8Array(digest));
  }

  buildAuthorizationUrl(
    discovery: OidcDiscoveryDocument,
    config: OidcConfiguration,
    codeChallenge: string,
    state: string,
  ): string {
    const params = new URLSearchParams({
      client_id: config.clientId,
      response_type: 'code',
      redirect_uri: config.redirectUri,
      scope: config.scope,
      state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
    });
    return `${discovery.authorization_endpoint}?${params.toString()}`;
  }

  async exchangeCodeForTokens(
    discovery: OidcDiscoveryDocument,
    config: OidcConfiguration,
    code: string,
    codeVerifier: string,
  ): Promise<AuthTokens> {
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      code,
      redirect_uri: config.redirectUri,
      code_verifier: codeVerifier,
    });

    const response = await firstValueFrom(
      this.http.post<TokenResponse>(discovery.token_endpoint, body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      }),
    );

    return {
      accessToken: response.access_token,
      expiresAt: Date.now() + response.expires_in * 1000,
      refreshToken: response.refresh_token,
    };
  }

  async refreshTokens(
    discovery: OidcDiscoveryDocument,
    config: OidcConfiguration,
    refreshToken: string,
  ): Promise<AuthTokens> {
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: config.clientId,
      refresh_token: refreshToken,
    });

    const response = await firstValueFrom(
      this.http.post<TokenResponse>(discovery.token_endpoint, body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      }),
    );

    return {
      accessToken: response.access_token,
      expiresAt: Date.now() + response.expires_in * 1000,
      refreshToken: response.refresh_token ?? refreshToken,
    };
  }

  getEndSessionUrl(
    discovery: OidcDiscoveryDocument,
    config: OidcConfiguration,
    idTokenHint?: string,
  ): string {
    const params = new URLSearchParams({
      client_id: config.clientId,
      post_logout_redirect_uri: config.redirectUri,
    });
    if (idTokenHint) {
      params.set('id_token_hint', idTokenHint);
    }
    return `${discovery.end_session_endpoint}?${params.toString()}`;
  }

  private base64UrlEncode(buffer: Uint8Array): string {
    let binary = '';
    for (const byte of buffer) {
      binary += String.fromCharCode(byte);
    }
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
}
