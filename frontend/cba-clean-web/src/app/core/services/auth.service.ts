import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private accessToken: string | null = null;

  getToken(): string | null {
    return this.accessToken;
  }

  setToken(token: string): void {
    this.accessToken = token;
  }

  clearToken(): void {
    this.accessToken = null;
  }

  isAuthenticated(): boolean {
    return this.accessToken !== null;
  }
}
