import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReportService } from '../../../reports/services/report.service';
import { ReportResponse } from '../../../reports/models/report.model';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../shared/pipes/translate.pipe';

@Component({
  selector: 'app-operator-reports',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe],
  templateUrl: './operator-reports.component.html',
  styleUrl: './operator-reports.component.scss',
})
export class OperatorReportsComponent implements OnInit {
  private readonly reportService = inject(ReportService);
  private readonly translation = inject(TranslationService);

  readonly reports = signal<ReportResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly pageSize = signal(10);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);

  readonly hasNextPage = computed(() => this.currentPage() < this.totalPages() - 1);
  readonly hasPreviousPage = computed(() => this.currentPage() > 0);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportService.getReports(this.currentPage(), this.pageSize()).subscribe({
      next: (data) => {
        this.reports.set(data.content);
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        const msg = err?.error?.message ?? err?.message ?? this.translation.t('operatorReports.loadFailed');
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) {
      this.currentPage.set(page);
      this.load();
    }
  }

  nextPage(): void {
    if (this.hasNextPage()) {
      this.goToPage(this.currentPage() + 1);
    }
  }

  previousPage(): void {
    if (this.hasPreviousPage()) {
      this.goToPage(this.currentPage() - 1);
    }
  }
}
