import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { isKeycloakRequest } from '../utils/keycloak-url.util';
import { TranslationService } from '../services/translation.service';

/** Keeps backend error messages in the same language as the UI. */
export const localeInterceptor: HttpInterceptorFn = (req, next) => {
  if (isKeycloakRequest(req.url)) {
    return next(req);
  }

  const locale = inject(TranslationService).locale();

  const cloned = req.clone({
    setHeaders: {
      'Accept-Language': locale,
    },
  });

  return next(cloned);
};
