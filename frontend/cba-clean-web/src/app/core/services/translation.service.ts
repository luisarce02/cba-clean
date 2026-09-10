import { Injectable, signal } from '@angular/core';
import { en, Translations } from '../i18n/en';
import { es } from '../i18n/es';

export type Locale = 'en' | 'es';

const DICTIONARIES: Record<Locale, Translations> = { en, es };
const STORAGE_KEY = 'cba_clean_locale';

function detectLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'es') return stored;
  } catch {
    // localStorage unavailable (SSR, privacy mode) — fall through to browser detection.
  }
  if (typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('es')) {
    return 'es';
  }
  return 'en';
}

@Injectable({ providedIn: 'root' })
export class TranslationService {
  readonly locale = signal<Locale>(detectLocale());

  setLocale(locale: Locale): void {
    this.locale.set(locale);
    try {
      localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      // best-effort persistence only
    }
  }

  /** Looks up a dot-path key (e.g. "nav.login") and interpolates {param} placeholders. */
  t(key: string, params?: Record<string, string | number>): string {
    const dict = DICTIONARIES[this.locale()];
    const value = key
      .split('.')
      .reduce<unknown>((acc, part) => (acc && typeof acc === 'object' ? (acc as Record<string, unknown>)[part] : undefined), dict);

    if (typeof value !== 'string') return key;
    if (!params) return value;

    return Object.keys(params).reduce((text, param) => text.replaceAll(`{${param}}`, String(params[param])), value);
  }
}
