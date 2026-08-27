import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../services/auth.service';
import { OidcService } from '../services/oidc.service';
import { homeRedirectGuard, reporterGuard, operatorGuard } from './role.guard';

function setToken(roles: string[]) {
  const payload = { roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  const token = `header.${btoa(JSON.stringify(payload))}.sig`;
  localStorage.setItem('cba_clean_access_token', token);
  localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
}

function clearToken() {
  localStorage.clear();
}

describe('Role Guards', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(async () => {
    clearToken();
    sessionStorage.clear();

    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: '', loadComponent: () => import('../../features/home/home.component').then(m => m.HomeComponent) },
          { path: 'reports/new', loadComponent: () => import('../../features/reports/pages/report-form-page/report-form-page.component').then(m => m.ReportFormPageComponent) },
          { path: 'reports', loadComponent: () => import('../../features/reports/pages/report-form-page/report-form-page.component').then(m => m.ReportFormPageComponent) },
          { path: 'operator/dashboard', loadComponent: () => import('../../features/operator/pages/operator-dashboard/operator-dashboard.component').then(m => m.OperatorDashboardComponent) },
          { path: 'operator/reports', loadComponent: () => import('../../features/operator/pages/operator-reports/operator-reports.component').then(m => m.OperatorReportsComponent) },
          { path: 'operator/incidents', loadComponent: () => import('../../features/operator/pages/operator-incidents/operator-incidents.component').then(m => m.OperatorIncidentsComponent) },
          { path: 'operator/metrics', loadComponent: () => import('../../features/operator/pages/operator-metrics/operator-metrics.component').then(m => m.OperatorMetricsComponent) },
        ]),
        AuthService,
        OidcService,
      ],
    }).compileComponents();

    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    clearToken();
    sessionStorage.clear();
  });

  describe('reporterGuard', () => {
    it('should allow unauthenticated user (login prompt)', () => {
      clearToken();
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any));
      expect(result).toBe(true);
    });

    it('should allow REPORTER', () => {
      setToken(['REPORTER']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any));
      expect(result).toBe(true);
    });

    it('should allow user with both roles (REPORTER+OPERATOR)', () => {
      setToken(['REPORTER', 'OPERATOR']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any));
      expect(result).toBe(true);
    });

    it('should redirect OPERATOR-only to /operator/dashboard', () => {
      setToken(['OPERATOR']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any));
      expect(typeof result).not.toBe('boolean');
      // UrlTree has toString containing the redirect
      expect((result as any).toString()).toContain('/operator/dashboard');
    });
  });

  describe('operatorGuard', () => {
    it('should allow OPERATOR', () => {
      setToken(['OPERATOR']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any));
      expect(result).toBe(true);
    });

    it('should allow user with both roles', () => {
      setToken(['REPORTER', 'OPERATOR']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any));
      expect(result).toBe(true);
    });

    it('should redirect REPORTER-only to /reports/new', () => {
      setToken(['REPORTER']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any));
      expect((result as any).toString()).toContain('/reports/new');
    });

    it('should redirect unauthenticated to /reports/new', () => {
      clearToken();
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any));
      expect((result as any).toString()).toContain('/reports/new');
    });
  });

  describe('homeRedirectGuard', () => {
    it('should redirect unauthenticated to /reports/new', () => {
      clearToken();
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any));
      expect((result as any).toString()).toContain('/reports/new');
    });

    it('should redirect REPORTER to /reports/new', () => {
      setToken(['REPORTER']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any));
      expect((result as any).toString()).toContain('/reports/new');
    });

    it('should redirect OPERATOR to /operator/dashboard', () => {
      setToken(['OPERATOR']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any));
      expect((result as any).toString()).toContain('/operator/dashboard');
    });

    it('should redirect REPORTER+OPERATOR to /operator/dashboard (prefer operator)', () => {
      setToken(['REPORTER', 'OPERATOR']);
      authService.loadFromStorage();
      const result = TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any));
      expect((result as any).toString()).toContain('/operator/dashboard');
    });
  });
});
