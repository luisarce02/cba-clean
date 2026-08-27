import { Routes } from '@angular/router';
import { homeRedirectGuard, reporterGuard, operatorGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    canActivate: [homeRedirectGuard],
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'reports/new',
    canActivate: [reporterGuard],
    loadComponent: () =>
      import('./features/reports/pages/report-form-page/report-form-page.component').then(
        (m) => m.ReportFormPageComponent,
      ),
  },
  {
    path: 'reports',
    canActivate: [reporterGuard],
    loadComponent: () =>
      import('./features/reports/pages/report-form-page/report-form-page.component').then(
        (m) => m.ReportFormPageComponent,
      ),
  },
  {
    path: 'operator/dashboard',
    canActivate: [operatorGuard],
    loadComponent: () =>
      import('./features/operator/pages/operator-dashboard/operator-dashboard.component').then(
        (m) => m.OperatorDashboardComponent,
      ),
  },
  {
    path: 'operator/reports',
    canActivate: [operatorGuard],
    loadComponent: () =>
      import('./features/operator/pages/operator-reports/operator-reports.component').then(
        (m) => m.OperatorReportsComponent,
      ),
  },
  {
    path: 'operator/reports/:id',
    canActivate: [operatorGuard],
    loadComponent: () =>
      import('./features/reports/pages/report-detail-page/report-detail-page.component').then(
        (m) => m.OperatorReportDetailComponent,
      ),
  },
  {
    path: 'operator/incidents',
    canActivate: [operatorGuard],
    loadComponent: () =>
      import('./features/operator/pages/operator-incidents/operator-incidents.component').then(
        (m) => m.OperatorIncidentsComponent,
      ),
  },
  {
    path: 'operator/incidents/:id',
    canActivate: [operatorGuard],
    loadComponent: () =>
      import('./features/incidents/pages/incident-detail/incident-detail.component').then(
        (m) => m.IncidentDetailComponent,
      ),
  },
  {
    path: 'operator/metrics',
    canActivate: [operatorGuard],
    loadComponent: () =>
      import('./features/operator/pages/operator-metrics/operator-metrics.component').then(
        (m) => m.OperatorMetricsComponent,
      ),
  },
];
