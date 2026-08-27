import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IncidentService } from '../../../incidents/services/incidents.service';
import { IncidentResponse, INCIDENT_STATUS_LABELS, INCIDENT_PRIORITY_LABELS, INCIDENT_TYPE_LABELS } from '../../../incidents/models/incidents.model';

@Component({
  selector: 'app-operator-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './operator-dashboard.component.html',
  styleUrl: './operator-dashboard.component.scss',
})
export class OperatorDashboardComponent implements OnInit {
  private readonly incidentService = inject(IncidentService);

  readonly incidents = signal<IncidentResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly statusLabels = INCIDENT_STATUS_LABELS;
  readonly priorityLabels = INCIDENT_PRIORITY_LABELS;
  readonly typeLabels = INCIDENT_TYPE_LABELS;

  readonly summary = computed(() => {
    const list = this.incidents();
    return {
      total: list.length,
      newCount: list.filter(i => i.status === 'NEW').length,
      assignedCount: list.filter(i => i.status === 'ASSIGNED').length,
      inProgressCount: list.filter(i => i.status === 'IN_PROGRESS').length,
      resolvedCount: list.filter(i => i.status === 'RESOLVED').length,
      cancelledCount: list.filter(i => i.status === 'CANCELLED').length,
    };
  });

  ngOnInit(): void {
    this.loadIncidents();
  }

  loadIncidents(): void {
    this.loading.set(true);
    this.error.set(null);
    this.incidentService.getIncidents().subscribe({
      next: (data) => {
        // Sort by lastModifiedAt descending
        const sorted = [...data].sort((a, b) => new Date(b.lastModifiedAt).getTime() - new Date(a.lastModifiedAt).getTime());
        this.incidents.set(sorted);
        this.loading.set(false);
      },
      error: (err) => {
        const msg = err?.error?.message ?? err?.message ?? 'Failed to load incidents';
        this.error.set(msg);
        this.loading.set(false);
      },
    });
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
