import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NavigationComponent } from './navigation.component';
import { AuthService } from '../../../core/services/auth.service';
import { OidcService } from '../../../core/services/oidc.service';

function setToken(roles: string[]) {
  const payload = { roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  const token = `header.${btoa(JSON.stringify(payload))}.sig`;
  localStorage.setItem('cba_clean_access_token', token);
  localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
}

describe('NavigationComponent', () => {
  let fixture: ComponentFixture<NavigationComponent>;
  let authService: AuthService;

  async function setup(roles: string[] | null) {
    localStorage.clear();
    sessionStorage.clear();
    if (roles) setToken(roles);
    await TestBed.configureTestingModule({
      imports: [NavigationComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), AuthService, OidcService],
    }).compileComponents();
    authService = TestBed.inject(AuthService);
    authService.loadFromStorage();
    fixture = TestBed.createComponent(NavigationComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should show Reporter nav for REPORTER', async () => {
    await setup(['REPORTER']);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Reporter');
    expect(el.textContent).toContain('Submit Report');
    expect(el.textContent).not.toContain('Operations');
  });

  it('should show Operations nav for OPERATOR', async () => {
    await setup(['OPERATOR']);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Operations');
    expect(el.textContent).toContain('Dashboard');
    expect(el.textContent).toContain('Reports');
    expect(el.textContent).toContain('Incidents');
    expect(el.textContent).toContain('Metrics');
    // Operator should NOT see Submit Report
    expect(el.querySelectorAll('a').length).toBeGreaterThan(0);
    const links = Array.from(el.querySelectorAll('a')).map(a => a.textContent?.trim());
    expect(links).not.toContain('Submit Report');
  });

  it('should show both navs for REPORTER+OPERATOR', async () => {
    await setup(['REPORTER', 'OPERATOR']);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Operations');
    expect(el.textContent).toContain('Reporter');
    expect(el.textContent).toContain('Submit Report');
    expect(el.textContent).toContain('Dashboard');
  });

  it('should not show privileged nav when unauthenticated', async () => {
    await setup(null);
    const el = fixture.nativeElement as HTMLElement;
    // Unauthenticated shows only Submit Report link (public)
    expect(el.textContent).toContain('Submit Report');
    expect(el.textContent).not.toContain('Operations');
  });
});
