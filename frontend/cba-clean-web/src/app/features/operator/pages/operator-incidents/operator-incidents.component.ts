import { Component, OnInit, inject, signal, computed } from '@angular/core';
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
        <div class="pagination">
          <span class="page-info">Page {{ currentPage() + 1 }} of {{ totalPages() }} ({{ totalElements() }} total)</span>
          <div class="page-controls">
            <button type="button" class="btn btn-page" [disabled]="!hasPreviousPage()" (click)="previousPage()">Previous</button>
            <button type="button" class="btn btn-page" [disabled]="!hasNextPage()" (click)="nextPage()">Next</button>
          </div>
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
    .btn-page { margin-left:0; background:#fff; cursor:pointer; }
    .btn-page:disabled { opacity:0.5; cursor:not-allowed; }
    .state-message { font-style:italic; color:#616161; }
    .state-error { color:#611a15; background:#fdecea; padding:0.75rem; border-radius:6px; }
    .pagination { display:flex; justify-content:space-between; align-items:center; margin-top:1.5rem; padding:1rem; background:#fff; border:1px solid #e0e0e0; border-radius:8px; }
    .page-info { font-size:0.875rem; color:#616161; }
    .page-controls { display:flex; gap:0.5rem; }
  `],
})
export class OperatorIncidentsComponent implements OnInit {
  private readonly incidentService = inject(IncidentService);
  readonly incidents = signal<IncidentResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly pageSize = signal(20);
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
    this.incidentService.getIncidents(this.currentPage(), this.pageSize()).subscribe({
      next: (data) => {
        this.incidents.set(data.content);
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.loading.set(false);
      },
      error: (err) => { this.error.set(err?.error?.message ?? 'Failed to load'); this.loading.set(false); },
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
