import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IncidentService } from '../../../incidents/services/incidents.service';
import { IncidentResponse, INCIDENT_STATUS_LABELS, INCIDENT_PRIORITY_LABELS, INCIDENT_TYPE_LABELS } from '../../../incidents/models/incidents.model';

type TimeRangePreset = 'today' | 'last24h' | 'last5d' | 'last2w' | 'lastMonth' | 'custom';

@Component({
  selector: 'app-operator-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './operator-dashboard.component.html',
  styleUrl: './operator-dashboard.component.scss',
})
export class OperatorDashboardComponent implements OnInit {
  private readonly incidentService = inject(IncidentService);

  readonly incidents = signal<IncidentResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly pageSize = signal(10);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);

  readonly selectedPreset = signal<TimeRangePreset>('today');
  readonly customFrom = signal('');
  readonly customTo = signal('');

  readonly statusLabels = INCIDENT_STATUS_LABELS;
  readonly priorityLabels = INCIDENT_PRIORITY_LABELS;
  readonly typeLabels = INCIDENT_TYPE_LABELS;

  readonly summary = computed(() => {
    const list = this.incidents();
    return {
      total: this.totalElements(),
      newCount: list.filter(i => i.status === 'NEW').length,
      assignedCount: list.filter(i => i.status === 'ASSIGNED').length,
      inProgressCount: list.filter(i => i.status === 'IN_PROGRESS').length,
      resolvedCount: list.filter(i => i.status === 'RESOLVED').length,
      cancelledCount: list.filter(i => i.status === 'CANCELLED').length,
    };
  });

  readonly hasNextPage = computed(() => this.currentPage() < this.totalPages() - 1);
  readonly hasPreviousPage = computed(() => this.currentPage() > 0);
  readonly isCustomRange = computed(() => this.selectedPreset() === 'custom');
  readonly isCustomRangeValid = computed(() => {
    const from = this.customFrom();
    const to = this.customTo();
    if (!from || !to) return false;
    return from <= to;
  });

  ngOnInit(): void {
    this.loadIncidents();
  }

  loadIncidents(): void {
    this.loading.set(true);
    this.error.set(null);
    const { from, to } = this.computeDateRange();
    this.incidentService.getIncidents(this.currentPage(), this.pageSize(), from, to).subscribe({
      next: (data) => {
        this.incidents.set(data.content);
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        const msg = err?.error?.message ?? err?.message ?? 'Failed to load incidents';
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }

  selectPreset(preset: TimeRangePreset): void {
    this.selectedPreset.set(preset);
    this.currentPage.set(0);
    if (preset !== 'custom') {
      this.loadIncidents();
    }
  }

  applyCustomRange(): void {
    if (this.isCustomRangeValid()) {
      this.currentPage.set(0);
      this.loadIncidents();
    }
  }

  onCustomFromChange(value: string): void {
    this.customFrom.set(value);
  }

  onCustomToChange(value: string): void {
    this.customTo.set(value);
  }

  private computeDateRange(): { from?: string; to?: string } {
    const preset = this.selectedPreset();
    const now = new Date();

    switch (preset) {
      case 'today': {
        const startOfDay = this.toUtcMidnight(now);
        const endOfDay = new Date(startOfDay);
        endOfDay.setUTCDate(endOfDay.getUTCDate() + 1);
        endOfDay.setUTCMilliseconds(endOfDay.getUTCMilliseconds() - 1);
        return { from: startOfDay.toISOString(), to: endOfDay.toISOString() };
      }
      case 'last24h': {
        const from = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        return { from: from.toISOString(), to: now.toISOString() };
      }
      case 'last5d': {
        const from = new Date(now.getTime() - 5 * 24 * 60 * 60 * 1000);
        return { from: from.toISOString(), to: now.toISOString() };
      }
      case 'last2w': {
        const from = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000);
        return { from: from.toISOString(), to: now.toISOString() };
      }
      case 'lastMonth': {
        const from = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        return { from: from.toISOString(), to: now.toISOString() };
      }
      case 'custom': {
        const from = this.customFrom();
        const to = this.customTo();
        if (!from || !to) return {};
        return { from: this.toUtcStartOfDay(from), to: this.toUtcEndOfDay(to) };
      }
      default:
        return {};
    }
  }

  private toUtcMidnight(date: Date): Date {
    return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  }

  private toUtcStartOfDay(dateStr: string): string {
    const d = new Date(dateStr);
    return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())).toISOString();
  }

  private toUtcEndOfDay(dateStr: string): string {
    const d = new Date(dateStr);
    const end = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1));
    end.setUTCMilliseconds(end.getUTCMilliseconds() - 1);
    return end.toISOString();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) {
      this.currentPage.set(page);
      this.loadIncidents();
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

  statusClass(status: string): string {
    switch (status) {
      case 'NEW': return 'status-new';
      case 'ASSIGNED': return 'status-assigned';
      case 'IN_PROGRESS': return 'status-in-progress';
      case 'RESOLVED': return 'status-resolved';
      case 'CANCELLED': return 'status-cancelled';
      default: return '';
    }
  }

  trackById(index: number, item: IncidentResponse) {
    return item.id;
  }
}
