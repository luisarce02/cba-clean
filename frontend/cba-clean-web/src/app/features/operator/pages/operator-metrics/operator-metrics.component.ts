import { Component } from '@angular/core';

@Component({
  selector: 'app-operator-metrics',
  standalone: true,
  template: `
    <section class="operator-metrics">
      <h1>Operational Metrics</h1>
      <p>System metrics and health overview.</p>
      <p class="hint">Access to /actuator/metrics and /actuator/prometheus is restricted to OPERATOR role.</p>
    </section>
  `,
  styles: [`
    .operator-metrics { padding: 1.5rem; max-width: 960px; margin: 0 auto; }
    .hint { font-size: 0.8rem; color: #9e9e9e; }
  `],
})
export class OperatorMetricsComponent {}
