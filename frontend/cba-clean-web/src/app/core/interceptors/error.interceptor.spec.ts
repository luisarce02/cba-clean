import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
  HttpErrorResponse,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { errorInterceptor } from './error.interceptor';
import { ErrorService } from '../services/error.service';

describe('errorInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let errorService: ErrorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ErrorService,
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    errorService = TestBed.inject(ErrorService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should pass through successful responses', () => {
    httpClient.get('/test').subscribe((event) => {
      expect(event).toBeTruthy();
    });

    const req = httpMock.expectOne('/test');
    req.flush({ data: 'ok' });
  });

  it('should handle HTTP errors and call ErrorService', () => {
    const handleErrorSpy = vi.spyOn(errorService, 'handleError');

    httpClient.get('/test').subscribe({
      next: () => {
        throw new Error('Expected an error');
      },
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(400);
        expect(handleErrorSpy).toHaveBeenCalled();
      },
    });

    const req = httpMock.expectOne('/test');
    req.flush(
      { status: 400, error: 'Bad Request', message: 'Invalid input' },
      { status: 400, statusText: 'Bad Request' },
    );
  });
});
