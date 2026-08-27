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
    it('should allow unauthenticated user (login prompt)', async () => {
      clearToken();
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any)) as unknown;
      expect(result).toBe(true);
    });

    it('should allow REPORTER', async () => {
      setToken(['REPORTER']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any)) as unknown;
      expect(result).toBe(true);
    });

    it('should allow user with both roles (REPORTER+OPERATOR)', async () => {
      setToken(['REPORTER', 'OPERATOR']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any)) as unknown;
      expect(result).toBe(true);
    });

    it('should redirect OPERATOR-only to /operator/dashboard', async () => {
      setToken(['OPERATOR']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => reporterGuard(null as any, null as any)) as unknown;
      expect(typeof result).not.toBe('boolean');
      expect((result as any).toString()).toContain('/operator/dashboard');
    });
  });

  describe('operatorGuard', () => {
    it('should allow OPERATOR', async () => {
      setToken(['OPERATOR']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any)) as unknown;
      expect(result).toBe(true);
    });

    it('should allow user with both roles', async () => {
      setToken(['REPORTER', 'OPERATOR']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any)) as unknown;
      expect(result).toBe(true);
    });

    it('should redirect REPORTER-only to /reports/new', async () => {
      setToken(['REPORTER']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any)) as unknown;
      expect((result as any).toString()).toContain('/reports/new');
    });

    it('should redirect unauthenticated to /reports/new', async () => {
      clearToken();
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => operatorGuard(null as any, null as any)) as unknown;
      expect((result as any).toString()).toContain('/reports/new');
    });
  });

  describe('homeRedirectGuard', () => {
    it('should redirect unauthenticated to /reports/new', async () => {
      clearToken();
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any)) as unknown;
      expect((result as any).toString()).toContain('/reports/new');
    });

    it('should redirect REPORTER to /reports/new', async () => {
      setToken(['REPORTER']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any)) as unknown;
      expect((result as any).toString()).toContain('/reports/new');
    });

    it('should redirect OPERATOR to /operator/dashboard', async () => {
      setToken(['OPERATOR']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any)) as unknown;
      expect((result as any).toString()).toContain('/operator/dashboard');
    });

    it('should redirect REPORTER+OPERATOR to /operator/dashboard (prefer operator)', async () => {
      setToken(['REPORTER', 'OPERATOR']);
      authService.loadFromStorage();
      const result = await TestBed.runInInjectionContext(() => homeRedirectGuard(null as any, null as any)) as unknown;
      expect((result as any).toString()).toContain('/operator/dashboard');
    });
  });
});
