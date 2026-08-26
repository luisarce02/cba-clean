import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'reports',
    pathMatch: 'full',
  },
  {
    path: 'reports',
    loadComponent: () =>
      import('./features/reports/pages/report-form-page/report-form-page.component').then(
        (m) => m.ReportFormPageComponent,
      ),
  },
];
