import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MetricsService } from './metrics.service';

describe('MetricsService', () => {
  let service: MetricsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MetricsService],
    });
    service = TestBed.inject(MetricsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should fetch report metric', () => {
    const mockResp = { name: 'cbaclean.reports.created', measurements: [{ statistic: 'COUNT', value: 5 }] } as any;
    service.getMetric('report', 'cbaclean.reports.created').subscribe((data) => expect(data.name).toBe('cbaclean.reports.created'));
    const req = httpMock.expectOne('http://localhost:8080/actuator/metrics/cbaclean.reports.created');
    expect(req.request.method).toBe('GET');
    req.flush(mockResp);
  });

  it('should fetch incident metric', () => {
    const mockResp = { name: 'cbaclean.incidents.created', measurements: [{ statistic: 'COUNT', value: 3 }] } as any;
    service.getMetric('incident', 'cbaclean.incidents.created').subscribe((data) => expect(data.name).toBe('cbaclean.incidents.created'));
    const req = httpMock.expectOne('http://localhost:8081/actuator/metrics/cbaclean.incidents.created');
    expect(req.request.method).toBe('GET');
    req.flush(mockResp);
  });

  it('should format uptime correctly', () => {
    expect(service.formatUptime(3661)).toBe('1h 1m');
    expect(service.formatUptime(90)).toBe('1m');
    expect(service.formatUptime(30)).toBe('30s');
  });

  it('should format counter value', () => {
    const metric = { name: 'cbaclean.reports.created', measurements: [{ statistic: 'COUNT', value: 42 }] } as any;
    const card = service.formatValue(metric, 'Reports Created');
    expect(card.value).toBe('42');
  });
});
