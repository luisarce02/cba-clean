import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

export interface ActuatorMetricResponse {
  name: string;
  description: string;
  baseUnit: string;
  measurements: { statistic: string; value: number }[];
  availableTags: { tag: string; values: string[] }[];
}

export interface MetricCard {
  label: string;
  value: string;
  raw: number | null;
  unit?: string;
  source: string;
  error?: string;
}

@Injectable({ providedIn: 'root' })
export class MetricsService {
  private readonly http = inject(HttpClient);

  private get reportActuatorBase(): string {
    return environment.apiBaseUrl.replace(/\/api\/v1\/?$/, '');
  }

  private get incidentActuatorBase(): string {
    const base = (environment as any).incidentApiBaseUrl as string | undefined;
    const url = base ?? environment.apiBaseUrl;
    return url.replace(/\/api\/v1\/?$/, '');
  }

  getMetric(service: 'report' | 'incident', name: string): Observable<ActuatorMetricResponse> {
    const base = service === 'report' ? this.reportActuatorBase : this.incidentActuatorBase;
    return this.http.get<ActuatorMetricResponse>(`${base}/actuator/metrics/${name}`);
  }

  getUptime(service: 'report' | 'incident'): Observable<ActuatorMetricResponse> {
    return this.getMetric(service, 'process.uptime');
  }

  // Convenience: fetch value of a metric (COUNT statistic) or first measurement
  metricValue(metric: ActuatorMetricResponse | null): number | null {
    if (!metric || !metric.measurements?.length) return null;
    // Prefer COUNT, else first
    const count = metric.measurements.find(m => m.statistic === 'COUNT');
    if (count) return count.value;
    const value = metric.measurements.find(m => m.statistic === 'VALUE');
    if (value) return value.value;
    return metric.measurements[0].value;
  }

  formatUptime(seconds: number | null): string {
    if (seconds == null || isNaN(seconds)) return '—';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    if (m > 0) return `${m}m`;
    return `${Math.floor(seconds)}s`;
  }

  formatValue(metric: ActuatorMetricResponse | null, label: string): MetricCard {
    if (!metric) return { label, value: '—', raw: null, source: '' };
    const raw = this.metricValue(metric);
    if (raw == null) return { label, value: '—', raw: null, source: metric.name };
    // Handle uptime special formatting
    if (metric.name === 'process.uptime') {
      return { label, value: this.formatUptime(raw), raw, unit: 's', source: metric.name };
    }
    if (metric.name.includes('duration')) {
      // Timer: value is seconds, format as ms or s
      const ms = raw * 1000;
      if (ms < 1000) return { label, value: `${ms.toFixed(1)} ms`, raw, unit: 's', source: metric.name };
      return { label, value: `${raw.toFixed(2)} s`, raw, unit: 's', source: metric.name };
    }
    if (metric.name.includes('jvm.memory')) {
      const mb = raw / (1024 * 1024);
      return { label, value: `${mb.toFixed(1)} MB`, raw, source: metric.name };
    }
    // Counter: integer
    return { label, value: Math.round(raw).toLocaleString(), raw, source: metric.name };
  }
}
