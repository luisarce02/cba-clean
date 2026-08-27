import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { isKeycloakRequest } from '../utils/keycloak-url.util';
import { catchError, switchMap, throwError, from } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const isKeycloak = isKeycloakRequest(req.url);
  if (isKeycloak) {
    return next(req);
  }

  const authService = inject(AuthService);
  const token = authService.getToken();

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Only handle 401 for non-Keycloak requests.
      // 403 must NOT trigger refresh/logout – user is authenticated but forbidden.
      if (error.status !== 401) {
        return throwError(() => error);
      }
      if (isKeycloak) {
        return throwError(() => error);
      }
      // Prevent infinite loop: already retried
      if (req.headers.has('X-Auth-Retry')) {
        return throwError(() => error);
      }

      // Attempt single-flight refresh via AuthService
      return from(authService.tryRefresh()).pipe(
        switchMap((success) => {
          if (!success) {
            return throwError(() => error);
          }
          const newToken = authService.getToken();
          if (!newToken) {
            return throwError(() => error);
          }
          const retryReq = req.clone({
            setHeaders: { Authorization: `Bearer ${newToken}` },
            headers: req.headers.set('X-Auth-Retry', '1'),
          });
          return next(retryReq);
        }),
        catchError(() => throwError(() => error)),
      );
    }),
  );
};
