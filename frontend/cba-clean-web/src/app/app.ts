import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from './core/services/auth.service';
import { NavigationComponent } from './shared/components/navigation/navigation.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, NavigationComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isAuthenticated = toSignal(this.authService.isAuthenticated$, { initialValue: false });
  readonly isDemoVisitor = computed(() => !this.isAuthenticated());
  readonly username = signal('');

  async ngOnInit(): Promise<void> {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('code') && urlParams.has('state')) {
      const success = await this.authService.handleCallback();
      this.username.set(this.authService.getUsername());
      if (success && this.authService.isAuthenticated()) {
        this.navigateByRole();
        return;
      }
    }
    this.username.set(this.authService.getUsername());
  }

  private navigateByRole(): void {
    if (this.authService.hasRole('OPERATOR')) {
      this.router.navigateByUrl('/operator/dashboard');
    } else if (this.authService.hasRole('REPORTER')) {
      this.router.navigateByUrl('/reports/new');
    } else {
      // Authenticated but no role: stay on reports page which will show appropriate message
      this.router.navigateByUrl('/reports/new');
    }
  }

  onLogin(): void {
    this.authService.login();
  }

  async onLogout(): Promise<void> {
    await this.authService.logout();
  }
}
