import { Component, inject, computed } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../core/services/auth.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './navigation.component.html',
  styleUrl: './navigation.component.scss',
})
export class NavigationComponent {
  private readonly authService = inject(AuthService);

  readonly isAuthenticated = toSignal(this.authService.isAuthenticated$, { initialValue: false });

  // computed based on current token; re-evaluated on change detection.
  // Using getters would also work but computed signals keep template reactive.
  readonly hasReporterRole = computed(() => this.authService.hasRole('REPORTER'));
  readonly hasOperatorRole = computed(() => this.authService.hasRole('OPERATOR'));
}
