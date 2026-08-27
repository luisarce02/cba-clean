import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReportService } from '../../../reports/services/report.service';
import { ReportResponse } from '../../../reports/models/report.model';

@Component({
  selector: 'app-operator-reports',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './operator-reports.component.html',
  styleUrl: './operator-reports.component.scss',
})
export class OperatorReportsComponent implements OnInit {
  private readonly reportService = inject(ReportService);

  readonly reports = signal<ReportResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportService.getReports().subscribe({
      next: (data) => {
        const sorted = [...data].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        this.reports.set(sorted);
        this.loading.set(false);
      },
      error: (err) => {
        const msg = err?.error?.message ?? err?.message ?? 'Failed to load reports';
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }
}
