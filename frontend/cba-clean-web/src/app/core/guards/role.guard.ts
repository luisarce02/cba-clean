import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Redirects unauthenticated or role-mismatched navigation to the appropriate
 * landing page. Keeps the Reporter page Reporter-only without showing the
 * misleading "required role (REPORTER)" message to OPERATOR users.
 */

export const reporterGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Not authenticated -> allow through so the page can show the login prompt.
  // The component itself handles the unauthenticated UI.
  if (!auth.isAuthenticated()) {
    return true;
  }

  if (auth.hasRole('REPORTER')) {
    return true;
  }

  // Authenticated but without REPORTER -> OPERATOR-only user trying to access
  // reporter-only flow. Redirect to operator dashboard instead of showing error.
  if (auth.hasRole('OPERATOR')) {
    return router.createUrlTree(['/operator/dashboard']);
  }

  // Authenticated with no known role -> redirect to home which will resolve.
  return router.createUrlTree(['/']);
};

export const operatorGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    // Let authGuard or the target page handle unauthenticated; redirect to
    // reporter landing which shows login prompt.
    return router.createUrlTree(['/reports/new']);
  }

  if (auth.hasRole('OPERATOR')) {
    return true;
  }

  // Reporter-only user trying to access operator area -> redirect to reporter landing
  if (auth.hasRole('REPORTER')) {
    return router.createUrlTree(['/reports/new']);
  }

  return router.createUrlTree(['/']);
};

export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/reports/new']);
  }

  // Prefer Operator dashboard when user has OPERATOR (covers OPERATOR and BOTH)
  if (auth.hasRole('OPERATOR')) {
    return router.createUrlTree(['/operator/dashboard']);
  }

  if (auth.hasRole('REPORTER')) {
    return router.createUrlTree(['/reports/new']);
  }

  // Authenticated but no recognized role -> send to reports page which will show
  // the role error in a controlled way, or to dashboard if we later generalize.
  return router.createUrlTree(['/reports/new']);
};

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/reports/new']);
};
