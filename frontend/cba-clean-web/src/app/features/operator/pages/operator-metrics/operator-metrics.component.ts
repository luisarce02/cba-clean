import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MetricsService, MetricCard, ActuatorMetricResponse } from '../../../metrics/services/metrics.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../shared/pipes/translate.pipe';

@Component({
  selector: 'app-operator-metrics',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './operator-metrics.component.html',
  styleUrl: './operator-metrics.component.scss',
})
export class OperatorMetricsComponent implements OnInit {
  private readonly metricsService = inject(MetricsService);
  private readonly translation = inject(TranslationService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly lastUpdated = signal<string | null>(null);
  readonly cards = signal<MetricCard[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    const metricsToFetch: Array<{ service: 'report' | 'incident'; name: string; label: string }> = [
      { service: 'report', name: 'cbaclean.reports.created', label: this.translation.t('operatorMetrics.reportsCreated') },
      { service: 'report', name: 'cbaclean.outbox.events.pending', label: this.translation.t('operatorMetrics.outboxPending') },
      { service: 'incident', name: 'cbaclean.incidents.created', label: this.translation.t('operatorMetrics.incidentsCreated') },
      { service: 'incident', name: 'cbaclean.incident.events.processed', label: this.translation.t('operatorMetrics.incidentEventsProcessed') },
      { service: 'report', name: 'process.uptime', label: this.translation.t('operatorMetrics.reportServiceUptime') },
      { service: 'incident', name: 'process.uptime', label: this.translation.t('operatorMetrics.incidentServiceUptime') },
      { service: 'report', name: 'http.server.requests', label: this.translation.t('operatorMetrics.reportHttpRequests') },
      { service: 'incident', name: 'http.server.requests', label: this.translation.t('operatorMetrics.incidentHttpRequests') },
    ];

    const observables = metricsToFetch.map((m) =>
      this.metricsService.getMetric(m.service, m.name).pipe(
        catchError((err) => {
          const status = err?.status;
          if (status === 401) return of(null as any);
          if (status === 403) return of(null as any);
          return of(null as any);
        }),
      ),
    );

    forkJoin(observables).subscribe({
      next: (results: (ActuatorMetricResponse | null)[]) => {
        const cards: MetricCard[] = results.map((res, idx) => {
          const def = metricsToFetch[idx];
          if (!res) {
            return { label: def.label, value: '—', raw: null, source: def.name, error: this.translation.t('operatorMetrics.unavailable') };
          }
          const card = this.metricsService.formatValue(res, def.label);
          card.source = def.name;
          return card;
        });
        this.cards.set(cards);
        this.loading.set(false);
        this.lastUpdated.set(new Date().toLocaleString());
        // If all failed, show error
        if (cards.every((c) => c.raw == null)) {
          this.error.set(this.translation.t('operatorMetrics.unableToLoad'));
        }
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? this.translation.t('operatorMetrics.loadFailed'));
        this.loading.set(false);
      },
    });
  }
}
