import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { correlationIdInterceptor } from './correlation-id.interceptor';

describe('correlationIdInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlationIdInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should add X-Correlation-ID header when not present', () => {
    httpClient.get('/test').subscribe();

    const req = httpMock.expectOne('/test');
    expect(req.request.headers.has('X-Correlation-ID')).toBe(true);
    const correlationId = req.request.headers.get('X-Correlation-ID');
    expect(correlationId).toBeTruthy();
    expect(correlationId!.length).toBeGreaterThan(0);
    req.flush({});
  });

  it('should preserve existing X-Correlation-ID header', () => {
    const existingId = 'existing-correlation-id-123';
    httpClient
      .get('/test', { headers: { 'X-Correlation-ID': existingId } })
      .subscribe();

    const req = httpMock.expectOne('/test');
    expect(req.request.headers.get('X-Correlation-ID')).toBe(existingId);
    req.flush({});
  });
});
