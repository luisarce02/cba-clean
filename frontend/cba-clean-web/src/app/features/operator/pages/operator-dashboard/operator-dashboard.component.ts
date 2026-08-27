import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-operator-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './operator-dashboard.component.html',
  styleUrl: './operator-dashboard.component.scss',
})
export class OperatorDashboardComponent {}
