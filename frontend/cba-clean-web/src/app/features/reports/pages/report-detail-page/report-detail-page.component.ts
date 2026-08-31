import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ReportService } from '../../services/report.service';
import { IncidentService } from '../../../incidents/services/incidents.service';
import { ReportResponse } from '../../models/report.model';

@Component({
  selector: 'app-operator-report-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './report-detail-page.component.html',
  styleUrl: './report-detail-page.component.scss',
})
export class OperatorReportDetailComponent implements OnInit {
  private readonly reportService = inject(ReportService);
  private readonly incidentService = inject(IncidentService);
  private readonly route = inject(ActivatedRoute);

  readonly report = signal<ReportResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly relatedIncidentId = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Missing report id');
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
        const msg = err?.error?.message ?? err?.message ?? 'Failed to load report';
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
