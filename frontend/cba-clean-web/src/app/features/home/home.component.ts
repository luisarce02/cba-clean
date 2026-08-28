import { Component, inject, computed, signal, OnInit, OnDestroy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);

  /** API docs link is a local development convenience only — hidden in production. */
  readonly isProduction = environment.production;

  readonly isAuthenticated = computed(() => this.auth.isAuthenticated());
  readonly hasReporter = computed(() => this.auth.hasRole('REPORTER'));
  readonly hasOperator = computed(() => this.auth.hasRole('OPERATOR'));

  /** Left → right flow: reporter creation + operator resolution loop */
  readonly steps = [
    'reporter',
    'angular',
    'nginx',
    'report-service',
    'postgres',
    'outbox',
    'rabbitmq',
    'incident-service',
    'mongo',
    'operator',
    'status',
  ] as const;

  readonly stepLabels: Record<string, string> = {
    reporter: 'Reporter',
    angular: 'Angular',
    nginx: 'Nginx',
    'report-service': 'Report Service',
    postgres: 'PostgreSQL',
    outbox: 'Outbox',
    rabbitmq: 'RabbitMQ',
    'incident-service': 'Incident Service',
    mongo: 'MongoDB',
    operator: 'Operator',
    status: 'Status · Resolved',
  };

  active = signal(0);
  playing = signal(true);
  reducedMotion = false;
  private timer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    if (typeof window !== 'undefined' && window.matchMedia) {
      this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (this.reducedMotion) {
        this.playing.set(false);
        return;
      }
    }
    this.start();
  }

  ngOnDestroy(): void {
    this.stop();
  }

  start(): void {
    if (this.reducedMotion || this.timer) return;
    this.playing.set(true);
    this.timer = setInterval(() => {
      this.active.update((v) => (v + 1) % this.steps.length);
    }, 1300);
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  toggle(): void {
    if (this.playing()) {
      this.stop();
      this.playing.set(false);
    } else {
      if (this.reducedMotion) return;
      this.playing.set(true);
      this.start();
    }
  }

  goTo(index: number): void {
    this.active.set(index);
    this.stop();
    this.playing.set(false);
  }

  scrollTo(targetId: string, event?: Event): void {
    event?.preventDefault();
    document.getElementById(targetId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  isActive(id: string): boolean {
    const idx = this.steps.indexOf(id as never);
    if (idx === -1) return false;
    if (this.reducedMotion) return false;
    return this.active() === idx;
  }

  isPast(id: string): boolean {
    if (this.reducedMotion) return false;
    const idx = this.steps.indexOf(id as never);
    return idx !== -1 && idx < this.active();
  }

  isConnActive(afterIndex: number): boolean {
    if (this.reducedMotion) return false;
    return this.active() === afterIndex + 1;
  }
}
