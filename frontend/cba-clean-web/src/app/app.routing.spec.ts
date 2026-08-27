import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { routes } from './app.routes';
import { AuthService } from './core/services/auth.service';
import { OidcService } from './core/services/oidc.service';
import { Component } from '@angular/core';

function setToken(roles: string[]) {
  const payload = { roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  const token = `header.${btoa(JSON.stringify(payload))}.sig`;
  localStorage.setItem('cba_clean_access_token', token);
  localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
}

describe('App Routing - role based', () => {
  let router: Router;
  let authService: AuthService;

  async function setup(roles: string[] | null) {
    localStorage.clear();
    sessionStorage.clear();
    if (roles) setToken(roles);
    await TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), AuthService, OidcService],
    }).compileComponents();
    authService = TestBed.inject(AuthService);
    authService.loadFromStorage();
    router = TestBed.inject(Router);
  }

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('REPORTER navigating to / should redirect to /reports/new', async () => {
    await setup(['REPORTER']);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/reports/new');
  });

  it('OPERATOR navigating to / should redirect to /operator/dashboard', async () => {
    await setup(['OPERATOR']);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/operator/dashboard');
  });

  it('REPORTER+OPERATOR navigating to / should redirect to /operator/dashboard', async () => {
    await setup(['REPORTER', 'OPERATOR']);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/operator/dashboard');
  });

  it('OPERATOR navigating to /reports/new should redirect to /operator/dashboard', async () => {
    await setup(['OPERATOR']);
    await router.navigateByUrl('/reports/new');
    expect(router.url).toBe('/operator/dashboard');
  });

  it('OPERATOR navigating to /reports should redirect to /operator/dashboard', async () => {
    await setup(['OPERATOR']);
    await router.navigateByUrl('/reports');
    expect(router.url).toBe('/operator/dashboard');
  });

  it('REPORTER navigating to /operator/dashboard should redirect to /reports/new', async () => {
    await setup(['REPORTER']);
    await router.navigateByUrl('/operator/dashboard');
    expect(router.url).toBe('/reports/new');
  });

  it('OPERATOR should access /operator/dashboard', async () => {
    await setup(['OPERATOR']);
    await router.navigateByUrl('/operator/dashboard');
    expect(router.url).toBe('/operator/dashboard');
  });

  it('REPORTER should access /reports/new', async () => {
    await setup(['REPORTER']);
    await router.navigateByUrl('/reports/new');
    expect(router.url).toBe('/reports/new');
  });
});
