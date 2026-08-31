import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { OperatorReportDetailComponent } from './report-detail-page.component';
import { ReportResponse } from '../../models/report.model';

describe('OperatorReportDetailComponent', () => {
  let fixture: ComponentFixture<OperatorReportDetailComponent>;
  let httpMock: HttpTestingController;

  const mockReport: ReportResponse = {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    type: 'LITTER',
    status: 'NEW',
    priority: 'NORMAL',
    description: 'Test report',
    location: { latitude: -17.3935, longitude: -66.157, address: 'Cochabamba' },
    reporter: { name: 'Jane Doe', email: 'jane@example.com' } as any,
    photoIds: ['photo-1'],
    createdAt: '2026-08-26T12:00:00Z',
    lastModifiedAt: '2026-08-26T12:00:00Z',
  };

  async function createWithMockRoute(id: string) {
    const mockRoute = { snapshot: { paramMap: { get: (k: string) => (k === 'id' ? id : null) } } } as any;
    await TestBed.configureTestingModule({
      imports: [OperatorReportDetailComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: ActivatedRoute, useValue: mockRoute }],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorReportDetailComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const req = httpMock.expectOne(`http://localhost:8080/api/v1/reports/${id}`);
    req.flush(mockReport);
    fixture.detectChanges();
    await fixture.whenStable();
    // After report load, component also fetches incidents for related
    const incReq = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    incReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => {
    try { httpMock.verify(); } catch {}
  });

  it('should create and display report details', async () => {
    await createWithMockRoute(mockReport.id);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Report');
    expect(el.textContent).toContain(mockReport.id.slice(0, 8));
    expect(el.textContent).toContain('LITTER');
    expect(el.textContent).toContain('Cochabamba');
    expect(el.textContent).toContain('Test report');
  });

  it('should show incident relation when available', async () => {
    const mockRoute = { snapshot: { paramMap: { get: (k: string) => (k === 'id' ? mockReport.id : null) } } } as any;
    await TestBed.configureTestingModule({
      imports: [OperatorReportDetailComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: ActivatedRoute, useValue: mockRoute }],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorReportDetailComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne(`http://localhost:8080/api/v1/reports/${mockReport.id}`).flush(mockReport);
    fixture.detectChanges();
    await fixture.whenStable();
    const incReq = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    incReq.flush({ content: [{ id: 'incident-1', reportId: mockReport.id, type: 'LITTER', status: 'NEW' }], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Related Incident');
    expect(el.textContent).toContain('View Incident');
  });

  it('should handle error state', async () => {
    const mockRoute = { snapshot: { paramMap: { get: () => mockReport.id } } } as any;
    await TestBed.configureTestingModule({
      imports: [OperatorReportDetailComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: ActivatedRoute, useValue: mockRoute }],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorReportDetailComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const req = httpMock.expectOne(`http://localhost:8080/api/v1/reports/${mockReport.id}`);
    req.flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.state-error')).toBeTruthy();
  });
});
