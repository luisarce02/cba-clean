import { inject, InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export const KEYCLOAK_ISSUER = new InjectionToken<string>('KEYCLOAK_ISSUER', {
  providedIn: 'root',
  factory: () => environment.keycloak?.issuer ?? '',
});

export function isKeycloakRequest(url: string): boolean {
  const issuer = inject(KEYCLOAK_ISSUER);
  if (!issuer) {
    return false;
  }
  try {
    const requestOrigin = new URL(url).origin;
    const keycloakOrigin = new URL(issuer).origin;
    return requestOrigin === keycloakOrigin;
  } catch {
    return false;
  }
}
