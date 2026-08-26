import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApiErrorResponse, FieldError } from '../models/api-error-response.model';

@Injectable({ providedIn: 'root' })
export class ErrorService {
  private readonly currentError$ = new BehaviorSubject<ApiErrorResponse | null>(null);

  readonly error$ = this.currentError$.asObservable();

  handleError(error: HttpErrorResponse): ApiErrorResponse {
    const apiError = this.mapToApiError(error);
    this.currentError$.next(apiError);
    return apiError;
  }

  clearError(): void {
    this.currentError$.next(null);
  }

  get currentError(): ApiErrorResponse | null {
    return this.currentError$.value;
  }

  isSubmissionFailure(error: ApiErrorResponse): boolean {
    if (error.status === 400 && error.fieldErrors && error.fieldErrors.length > 0) {
      return false;
    }
    return true;
  }

  private mapToApiError(error: HttpErrorResponse): ApiErrorResponse {
    const body = error.error;

    if (this.isApiErrorResponse(body)) {
      return body;
    }

    return {
      status: error.status,
      error: this.getStatusText(error.status),
      message: this.getDefaultMessage(error.status),
      timestamp: new Date().toISOString(),
    };
  }

  private isApiErrorResponse(obj: unknown): obj is ApiErrorResponse {
    return (
      typeof obj === 'object' &&
      obj !== null &&
      'status' in obj &&
      'error' in obj &&
      'message' in obj
    );
  }

  private getStatusText(status: number): string {
    const map: Record<number, string> = {
      0: 'Network Error',
      400: 'Bad Request',
      401: 'Unauthorized',
      403: 'Forbidden',
      404: 'Not Found',
      500: 'Internal Server Error',
    };
    return map[status] ?? 'Error';
  }

  private getDefaultMessage(status: number): string {
    const map: Record<number, string> = {
      0: 'Unable to connect to the server. Please check your connection.',
      400: 'The request was invalid. Please check your input.',
      401: 'You are not authorized. Please log in.',
      403: 'You do not have permission to perform this action.',
      404: 'The requested resource was not found.',
      500: 'An unexpected error occurred. Please try again later.',
    };
    return map[status] ?? 'An unexpected error occurred.';
  }
}
