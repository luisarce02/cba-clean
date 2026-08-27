import { Component } from '@angular/core';

@Component({
  selector: 'app-operator-reports',
  standalone: true,
  template: `
    <section class="operator-reports">
      <h1>Reports</h1>
      <p>Operational view of submitted reports.</p>
      <p class="hint">Use GET /api/v1/reports/&#123;id&#125; to view details. List endpoint integration pending.</p>
    </section>
  `,
  styles: [`
    .operator-reports { padding: 1.5rem; max-width: 960px; margin: 0 auto; }
    .hint { font-size: 0.8rem; color: #9e9e9e; }
  `],
})
export class OperatorReportsComponent {}
