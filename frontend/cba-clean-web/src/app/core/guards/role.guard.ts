import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Guards wait for auth initialization and attempt silent refresh before
 * deciding. This prevents the race where "not initialized yet" is mistaken
 * for "unauthenticated" and avoids the intermittent logout where navigation
 * would redirect to login while a valid refresh token still exists.
 */

export const reporterGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  await auth.refreshIfNeeded();

  // Not authenticated -> allow through so the page can show the login prompt.
  if (!auth.isAuthenticated()) {
    return true;
  }

  if (auth.hasRole('REPORTER')) {
    return true;
  }

  // Authenticated but without REPORTER -> OPERATOR-only user
  if (auth.hasRole('OPERATOR')) {
    return router.createUrlTree(['/operator/dashboard']);
  }

  return router.createUrlTree(['/']);
};

export const operatorGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  await auth.refreshIfNeeded();

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/reports/new']);
  }

  if (auth.hasRole('OPERATOR')) {
    return true;
  }

  if (auth.hasRole('REPORTER')) {
    return router.createUrlTree(['/reports/new']);
  }

  return router.createUrlTree(['/']);
};

/**
 * Guards the Operator dashboard route only. Unlike operatorGuard (used by
 * every other operator/** route), this lets an unauthenticated visitor
 * through so the dashboard can render in its read-only demo mode. Any
 * authenticated user is still routed exactly as operatorGuard would:
 * OPERATOR gets in, REPORTER is sent to their own page, anyone else is sent
 * home. No Keycloak role is introduced — the demo branch triggers purely on
 * the absence of authentication.
 */
export const operatorDemoGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  await auth.refreshIfNeeded();

  // Not authenticated -> allow through so the dashboard renders in read-only demo mode.
  if (!auth.isAuthenticated()) {
    return true;
  }

  if (auth.hasRole('OPERATOR')) {
    return true;
  }

  if (auth.hasRole('REPORTER')) {
    return router.createUrlTree(['/reports/new']);
  }

  return router.createUrlTree(['/']);
};

export const homeRedirectGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  await auth.refreshIfNeeded();

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/reports/new']);
  }

  if (auth.hasRole('OPERATOR')) {
    return router.createUrlTree(['/operator/dashboard']);
  }

  if (auth.hasRole('REPORTER')) {
    return router.createUrlTree(['/reports/new']);
  }

  return router.createUrlTree(['/reports/new']);
};

export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.refreshIfNeeded();
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/reports/new']);
};
