import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService } from './core/services/auth.service';
import { OidcService } from './core/services/oidc.service';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthService,
        OidcService,
      ],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the app title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.app-title')?.textContent).toContain('CBA Clean');
  });

  it('should show login button when not authenticated', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const loginBtn = compiled.querySelector('.btn-login');
    expect(loginBtn).toBeTruthy();
    expect(loginBtn?.textContent?.trim()).toBe('Login');
  });

  it('should not show logout button when not authenticated', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const logoutBtn = compiled.querySelector('.btn-logout');
    expect(logoutBtn).toBeNull();
  });
});
