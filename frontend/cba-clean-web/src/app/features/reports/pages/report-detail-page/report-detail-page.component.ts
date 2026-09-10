import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ReportService } from '../../services/report.service';
import { IncidentService } from '../../../incidents/services/incidents.service';
import { ReportResponse } from '../../models/report.model';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../shared/pipes/translate.pipe';

@Component({
  selector: 'app-operator-report-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe],
  templateUrl: './report-detail-page.component.html',
  styleUrl: './report-detail-page.component.scss',
})
export class OperatorReportDetailComponent implements OnInit {
  private readonly reportService = inject(ReportService);
  private readonly incidentService = inject(IncidentService);
  private readonly route = inject(ActivatedRoute);
  private readonly translation = inject(TranslationService);

  readonly report = signal<ReportResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly relatedIncidentId = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set(this.translation.t('reportDetail.missingId'));
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportService.getReport(id).subscribe({
      next: (data) => {
        this.report.set(data);
        this.loading.set(false);
        this.findRelatedIncident(data.id);
      },
      error: (err) => {
        const msg = err?.error?.message ?? err?.message ?? this.translation.t('reportDetail.loadFailed');
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }

  private findRelatedIncident(reportId: string): void {
    this.incidentService.getIncidents(0, 100).subscribe({
      next: (result) => {
        const match = result.content.find((i) => i.reportId === reportId);
        if (match) this.relatedIncidentId.set(match.id);
      },
      error: () => {
        // silently ignore; incident fetch failure should not block report view
      },
    });
  }
}
