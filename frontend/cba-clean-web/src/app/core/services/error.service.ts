import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApiErrorResponse, FieldError } from '../models/api-error-response.model';
import { TranslationService } from './translation.service';

@Injectable({ providedIn: 'root' })
export class ErrorService {
  private readonly translation = inject(TranslationService);
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
      0: this.translation.t('errors.network'),
      400: this.translation.t('errors.badRequest'),
      401: this.translation.t('errors.unauthorized'),
      403: this.translation.t('errors.forbidden'),
      404: this.translation.t('errors.notFound'),
      500: this.translation.t('errors.serverError'),
    };
    return map[status] ?? this.translation.t('errors.generic');
  }

  private getDefaultMessage(status: number): string {
    const map: Record<number, string> = {
      0: this.translation.t('errors.networkMessage'),
      400: this.translation.t('errors.badRequestMessage'),
      401: this.translation.t('errors.unauthorizedMessage'),
      403: this.translation.t('errors.forbiddenMessage'),
      404: this.translation.t('errors.notFoundMessage'),
      500: this.translation.t('errors.serverErrorMessage'),
    };
    return map[status] ?? this.translation.t('errors.genericMessage');
  }
}
