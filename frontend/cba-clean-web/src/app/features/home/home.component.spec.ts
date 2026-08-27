import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HomeComponent } from './home.component';
import { AuthService } from '../../core/services/auth.service';
import { OidcService } from '../../core/services/oidc.service';
import { routes } from '../../app.routes';

describe('HomeComponent - public landing', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), AuthService, OidcService],
    }).compileComponents();
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('should render for anonymous users', async () => {
    const fixture = await createComponent();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="home-page"]')).toBeTruthy();
    expect(el.querySelector('#hero-title')?.textContent).toContain('CBA Clean');
  });

  it('should render hero CTA links', async () => {
    const fixture = await createComponent();
    const el = fixture.nativeElement as HTMLElement;
    const reportCta = el.querySelector('[data-testid="cta-report"]') as HTMLAnchorElement;
    const exploreCta = el.querySelector('[data-testid="cta-explore"]') as HTMLAnchorElement;
    expect(reportCta).toBeTruthy();
    expect(reportCta.getAttribute('href')).toBe('/reports/new');
    expect(exploreCta).toBeTruthy();
    expect(exploreCta.getAttribute('href')).toBe('#arch');
  });

  it('should render concise sections (purpose, arch, closing)', async () => {
    const fixture = await createComponent();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#purpose-title')).toBeTruthy();
    expect(el.querySelector('#arch-title')).toBeTruthy();
    expect(el.querySelector('#demo-title')).toBeTruthy();
  });

  it('should render interactive architecture with real services', async () => {
    const fixture = await createComponent();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="arch-panel"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="flow"]')).toBeTruthy();
    // real services
    expect(el.querySelector('[data-testid="node-report-service"]')?.textContent).toContain('Report Service');
    expect(el.querySelector('[data-testid="node-postgres"]')?.textContent).toContain('PostgreSQL');
    expect(el.querySelector('[data-testid="node-rabbitmq"]')?.textContent).toContain('RabbitMQ');
    expect(el.querySelector('[data-testid="node-incident-service"]')?.textContent).toContain('Incident Service');
    expect(el.querySelector('[data-testid="node-mongo"]')?.textContent).toContain('MongoDB');
    expect(el.querySelector('[data-testid="node-nginx"]')?.textContent).toContain('Nginx');
    expect(el.querySelector('[data-testid="node-angular"]')?.textContent).toContain('Angular');
  });

  it('should show tech chips and CI strip with real technologies', async () => {
    const fixture = await createComponent();
    const el = fixture.nativeElement as HTMLElement;
    const chips = el.querySelector('[data-testid="tech-chips"]')?.textContent ?? '';
    expect(chips).toContain('Java 21');
    expect(chips).toContain('Spring Boot');
    expect(chips).toContain('PostgreSQL');
    expect(chips).toContain('MongoDB');
    expect(chips).toContain('RabbitMQ');
    expect(chips).toContain('Keycloak');
    expect(el.querySelector('[data-testid="ci-strip"]')?.textContent).toContain('Required checks');
  });

  it('should contain workflow labels (reporter to resolved)', async () => {
    const fixture = await createComponent();
    const el = fixture.nativeElement as HTMLElement;
    const text = el.textContent ?? '';
    expect(text).toContain('Reporter');
    expect(text).toContain('Operator');
    expect(text).toContain('NEW');
    expect(text).toContain('RESOLVED');
    expect(text).toContain('Outbox');
    expect(text).toContain('Idempotency');
  });

  it('should be accessible via / route for anonymous users', async () => {
    await TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), AuthService, OidcService],
    }).compileComponents();
    const router = TestBed.inject(Router);
    const auth = TestBed.inject(AuthService);
    auth.loadFromStorage();
    await router.navigateByUrl('/');
    expect(router.url).toBe('/');
  });

  it('should not break operator protected route', async () => {
    await TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), AuthService, OidcService],
    }).compileComponents();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/operator/dashboard');
    expect(router.url).toBe('/reports/new');
  });

  it('should keep reporter guard intact', async () => {
    const payload = { roles: ['REPORTER'], exp: Math.floor(Date.now() / 1000) + 3600 };
    const token = `header.${btoa(JSON.stringify(payload))}.sig`;
    localStorage.setItem('cba_clean_access_token', token);
    localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
    await TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), AuthService, OidcService],
    }).compileComponents();
    const router = TestBed.inject(Router);
    const auth = TestBed.inject(AuthService);
    auth.loadFromStorage();
    await router.navigateByUrl('/operator/metrics');
    expect(router.url).toBe('/reports/new');
  });

  it('should keep content visible when reduced-motion (static)', async () => {
    // Home renders static architecture even without animation – check a11y details present
    const fixture = await createComponent();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.a11y-details')).toBeTruthy();
    expect(el.querySelector('[data-testid="node-rabbitmq"]')).toBeTruthy();
  });
});
