import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { OperatorMetricsComponent } from './operator-metrics.component';

describe('OperatorMetricsComponent', () => {
  let fixture: ComponentFixture<OperatorMetricsComponent>;
  let httpMock: HttpTestingController;

  function flushAllMetrics(count = 1) {
    const urls = [
      'http://localhost:8080/actuator/metrics/cbaclean.reports.created',
      'http://localhost:8080/actuator/metrics/cbaclean.outbox.events.pending',
      'http://localhost:8081/actuator/metrics/cbaclean.incidents.created',
      'http://localhost:8081/actuator/metrics/cbaclean.incident.events.processed',
      'http://localhost:8080/actuator/metrics/process.uptime',
      'http://localhost:8081/actuator/metrics/process.uptime',
      'http://localhost:8080/actuator/metrics/http.server.requests',
      'http://localhost:8081/actuator/metrics/http.server.requests',
    ];
    urls.forEach((url) => {
      const req = httpMock.expectOne(url);
      req.flush({ name: url.split('/').pop(), measurements: [{ statistic: 'COUNT', value: count }] } as any);
    });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorMetricsComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorMetricsComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    fixture.detectChanges();
    flushAllMetrics();
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should load metrics and render cards', () => {
    fixture.detectChanges();
    flushAllMetrics(5);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Operational Metrics');
    expect(el.textContent).toContain('Reports Created');
    expect(el.textContent).toContain('Incidents Created');
    expect(el.textContent).toContain('Outbox Pending');
  });

  it('should show last updated', () => {
    fixture.detectChanges();
    flushAllMetrics(2);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Last updated');
  });

  it('should handle error state when all metrics unavailable', () => {
    fixture.detectChanges();
    const urls = [
      'http://localhost:8080/actuator/metrics/cbaclean.reports.created',
      'http://localhost:8080/actuator/metrics/cbaclean.outbox.events.pending',
      'http://localhost:8081/actuator/metrics/cbaclean.incidents.created',
      'http://localhost:8081/actuator/metrics/cbaclean.incident.events.processed',
      'http://localhost:8080/actuator/metrics/process.uptime',
      'http://localhost:8081/actuator/metrics/process.uptime',
      'http://localhost:8080/actuator/metrics/http.server.requests',
      'http://localhost:8081/actuator/metrics/http.server.requests',
    ];
    urls.forEach((url) => {
      const req = httpMock.expectOne(url);
      req.flush({}, { status: 403, statusText: 'Forbidden' });
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Unable to load metrics');
  });

  it('should have refresh button', () => {
    fixture.detectChanges();
    flushAllMetrics();
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button');
    expect(btn?.textContent).toContain('Refresh');
  });
});
