import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly authService = inject(AuthService);

  readonly isAuthenticated = toSignal(this.authService.isAuthenticated$, { initialValue: false });
  readonly username = signal('');

  async ngOnInit(): Promise<void> {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('code') && urlParams.has('state')) {
      await this.authService.handleCallback();
    }
    this.username.set(this.authService.getUsername());
  }

  onLogin(): void {
    this.authService.login();
  }

  async onLogout(): Promise<void> {
    await this.authService.logout();
  }
}
