import { HttpInterceptorFn } from '@angular/common/http';
import { isKeycloakRequest } from '../utils/keycloak-url.util';

const CORRELATION_ID_HEADER = 'X-Correlation-ID';

function generateCorrelationId(): string {
  return crypto.randomUUID();
}

export const correlationIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (isKeycloakRequest(req.url)) {
    return next(req);
  }

  const correlationId = req.headers.get(CORRELATION_ID_HEADER) ?? generateCorrelationId();

  const cloned = req.clone({
    setHeaders: {
      [CORRELATION_ID_HEADER]: correlationId,
    },
  });

  return next(cloned);
};
