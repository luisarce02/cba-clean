import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { IncidentService } from '../../services/incidents.service';
import { IncidentResponse, IncidentStatus, INCIDENT_TYPE_LABELS, INCIDENT_PRIORITY_LABELS } from '../../models/incidents.model';

@Component({
  selector: 'app-incident-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './incident-detail.component.html',
  styleUrl: './incident-detail.component.scss',
})
export class IncidentDetailComponent implements OnInit {
  private readonly incidentService = inject(IncidentService);
  private readonly route = inject(ActivatedRoute);

  readonly incident = signal<IncidentResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly updating = signal(false);
  readonly updateError = signal<string | null>(null);
  readonly updateSuccess = signal<string | null>(null);

  readonly selectedStatus = signal<IncidentStatus | ''>('');
  readonly closingNote = signal('');

  readonly typeLabels = INCIDENT_TYPE_LABELS;
  readonly priorityLabels = INCIDENT_PRIORITY_LABELS;

  readonly availableTransitions = computed<IncidentStatus[]>(() => {
    const status = this.incident()?.status;
    if (!status) return [];
    switch (status) {
      case 'NEW': return ['ASSIGNED', 'CANCELLED'];
      case 'ASSIGNED': return ['IN_PROGRESS', 'CANCELLED'];
      case 'IN_PROGRESS': return ['RESOLVED', 'CANCELLED'];
      default: return [];
    }
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Missing incident id');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.incidentService.getIncident(id).subscribe({
      next: (data) => {
        this.incident.set(data);
        this.loading.set(false);
        this.selectedStatus.set('');
        this.closingNote.set('');
        this.updateError.set(null);
        this.updateSuccess.set(null);
      },
      error: (err) => {
        const msg = err?.error?.message ?? err?.message ?? 'Failed to load incident';
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

  requiresNote(status: string): boolean {
    return status === 'RESOLVED' || status === 'CANCELLED';
  }

  updateStatus(): void {
    const incident = this.incident();
    const target = this.selectedStatus();
    if (!incident || !target) return;

    this.updating.set(true);
    this.updateError.set(null);
    this.updateSuccess.set(null);

    this.incidentService.updateIncidentStatus(incident.id, {
      status: target,
      closingNote: this.closingNote() || undefined,
    }).subscribe({
      next: (updated) => {
        this.incident.set(updated);
        this.updating.set(false);
        this.updateSuccess.set(`Status updated to ${updated.status}`);
        this.selectedStatus.set('');
        this.closingNote.set('');
      },
      error: (err) => {
        let msg = err?.error?.message ?? err?.message ?? 'Failed to update status';
        if (err?.status === 403) msg = 'You do not have permission to update incidents';
        if (err?.status === 401) msg = 'Authentication required';
        this.updateError.set(msg);
        this.updating.set(false);
      },
    });
  }
}
