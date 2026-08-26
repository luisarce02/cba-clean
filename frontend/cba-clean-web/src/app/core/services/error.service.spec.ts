import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ErrorService } from './error.service';
import { HttpErrorResponse } from '@angular/common/http';

describe('ErrorService', () => {
  let service: ErrorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ErrorService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ErrorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should handle 400 error', () => {
    const error = new HttpErrorResponse({
      error: {
        status: 400,
        error: 'Bad Request',
        message: 'Request validation failed',
        fieldErrors: [{ field: 'reportType', message: 'reportType is required' }],
        timestamp: '2026-08-26T12:00:00Z',
      },
      status: 400,
    });

    const result = service.handleError(error);
    expect(result.status).toBe(400);
    expect(result.message).toBe('Request validation failed');
    expect(result.fieldErrors).toHaveLength(1);
    expect(result.fieldErrors![0].field).toBe('reportType');
  });

  it('should handle 401 error with default message', () => {
    const error = new HttpErrorResponse({ status: 401 });

    const result = service.handleError(error);
    expect(result.status).toBe(401);
    expect(result.message).toBe('You are not authorized. Please log in.');
  });

  it('should handle 403 error with default message', () => {
    const error = new HttpErrorResponse({ status: 403 });

    const result = service.handleError(error);
    expect(result.status).toBe(403);
    expect(result.message).toBe('You do not have permission to perform this action.');
  });

  it('should handle 404 error with default message', () => {
    const error = new HttpErrorResponse({ status: 404 });

    const result = service.handleError(error);
    expect(result.status).toBe(404);
    expect(result.message).toBe('The requested resource was not found.');
  });

  it('should handle 500 error with default message', () => {
    const error = new HttpErrorResponse({ status: 500 });

    const result = service.handleError(error);
    expect(result.status).toBe(500);
    expect(result.message).toBe('An unexpected error occurred. Please try again later.');
  });

  it('should handle network error', () => {
    const error = new HttpErrorResponse({ status: 0 });

    const result = service.handleError(error);
    expect(result.status).toBe(0);
    expect(result.message).toBe('Unable to connect to the server. Please check your connection.');
  });

  it('should emit error on error$ observable', () => {
    let emittedError: any = null;
    service.error$.subscribe((err) => (emittedError = err));

    const error = new HttpErrorResponse({ status: 404 });
    service.handleError(error);

    expect(emittedError).toBeTruthy();
    expect(emittedError.status).toBe(404);
  });

  it('should clear error', () => {
    service.handleError(new HttpErrorResponse({ status: 500 }));
    service.clearError();

    let emittedError: any = 'not-null';
    service.error$.subscribe((err) => (emittedError = err));
    expect(emittedError).toBeNull();
  });
});
