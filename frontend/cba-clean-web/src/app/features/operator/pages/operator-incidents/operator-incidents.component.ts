import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IncidentService } from '../../../incidents/services/incidents.service';
import { IncidentResponse } from '../../../incidents/models/incidents.model';

@Component({
  selector: 'app-operator-incidents',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="operator-incidents">
      <h1>Incidents</h1>
      @if (loading()) {
        <p class="state-message">Loading incidents…</p>
      } @else if (error()) {
        <p class="state-error">{{ error() }}</p>
      } @else if (incidents().length === 0) {
        <p class="state-message">No incidents yet.</p>
      } @else {
        <div class="incident-list">
          @for (inc of incidents(); track inc.id) {
            <div class="incident-row">
              <span class="mono">{{ inc.id | slice:0:8 }}</span>
              <span class="status">{{ inc.status }}</span>
              <span class="priority">{{ inc.priority }}</span>
              <span class="type">{{ inc.type }}</span>
              <a [routerLink]="['/operator/incidents', inc.id]" class="btn">View</a>
            </div>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    .operator-incidents { padding: 1.5rem; max-width: 960px; margin: 0 auto; }
    .incident-row { display:flex; gap:1rem; align-items:center; padding:0.5rem 0; border-bottom:1px solid #eee; font-size:0.85rem; }
    .mono { font-family: monospace; font-weight:600; }
    .btn { margin-left:auto; color:#1976d2; text-decoration:none; border:1px solid #1976d2; padding:0.25rem 0.6rem; border-radius:4px; }
    .btn:hover { background:#e3f2fd; }
    .state-message { font-style:italic; color:#616161; }
    .state-error { color:#611a15; background:#fdecea; padding:0.75rem; border-radius:6px; }
  `],
})
export class OperatorIncidentsComponent implements OnInit {
  private readonly incidentService = inject(IncidentService);
  readonly incidents = signal<IncidentResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.incidentService.getIncidents().subscribe({
      next: (data) => { this.incidents.set(data); this.loading.set(false); },
      error: (err) => { this.error.set(err?.error?.message ?? 'Failed to load'); this.loading.set(false); },
    });
  }
}
