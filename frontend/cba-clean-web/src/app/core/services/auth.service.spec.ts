import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService],
    });
    service = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return null when no token is set', () => {
    expect(service.getToken()).toBeNull();
  });

  it('should set and get token', () => {
    service.setToken('test-token');
    expect(service.getToken()).toBe('test-token');
  });

  it('should clear token', () => {
    service.setToken('test-token');
    service.clearToken();
    expect(service.getToken()).toBeNull();
  });

  it('should report not authenticated when no token', () => {
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should report authenticated when token is set', () => {
    service.setToken('test-token');
    expect(service.isAuthenticated()).toBe(true);
  });
});
