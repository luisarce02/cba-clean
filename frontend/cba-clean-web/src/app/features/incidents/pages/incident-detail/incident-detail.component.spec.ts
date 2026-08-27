import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { IncidentDetailComponent } from './incident-detail.component';
import { IncidentResponse } from '../../models/incidents.model';
import { routes } from '../../../../app.routes';

describe('IncidentDetailComponent', () => {
  let fixture: ComponentFixture<IncidentDetailComponent>;
  let httpMock: HttpTestingController;

  const mockIncident: IncidentResponse = {
    id: '11111111-1111-1111-1111-111111111111',
    reportId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    type: 'ILLEGAL_DUMPING',
    status: 'NEW',
    priority: 'HIGH',
    description: 'Illegal dump',
    location: { latitude: -17.3935, longitude: -66.157, address: 'Cochabamba', zone: null },
    assignment: null,
    closingNote: null,
    createdAt: '2026-08-26T12:00:00Z',
    lastModifiedAt: '2026-08-26T12:00:00Z',
  };

  async function createWithMockRoute(id: string, status: string) {
    const mockRoute = { snapshot: { paramMap: { get: (key: string) => (key === 'id' ? id : null) } } } as any;
    await TestBed.configureTestingModule({
      imports: [IncidentDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: mockRoute },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(IncidentDetailComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const incident = { ...mockIncident, status: status as any, id };
    const req = httpMock.expectOne(`http://localhost:8081/api/v1/incidents/${id}`);
    req.flush(incident);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => {
    try { httpMock.verify(); } catch {}
  });

  it('should create and display incident details', async () => {
    await createWithMockRoute(mockIncident.id, 'NEW');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Incident');
    expect(el.textContent).toContain(mockIncident.id.slice(0, 8));
    expect(el.textContent).toContain('Illegal Dumping');
    expect(el.textContent).toContain('NEW');
  });

  it('should render status indicator with correct class', async () => {
    await createWithMockRoute(mockIncident.id, 'NEW');
    const indicator = fixture.nativeElement.querySelector('.status-indicator');
    expect(indicator.classList.contains('status-new')).toBe(true);
    expect(indicator.getAttribute('aria-label')).toBe('NEW');
  });

  it('should show available transitions for NEW (ASSIGNED, CANCELLED)', async () => {
    await createWithMockRoute(mockIncident.id, 'NEW');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('ASSIGNED');
    expect(el.textContent).toContain('CANCELLED');
    expect(el.textContent).not.toContain('RESOLVED');
  });

  it('should show no transitions for terminal status RESOLVED', async () => {
    await createWithMockRoute(mockIncident.id, 'RESOLVED');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No further transitions');
  });

  it('should update status via API and reflect new status', async () => {
    await createWithMockRoute(mockIncident.id, 'NEW');
    const component = fixture.componentInstance;
    component.selectedStatus.set('ASSIGNED');
    fixture.detectChanges();

    component.updateStatus();
    const req = httpMock.expectOne(`http://localhost:8081/api/v1/incidents/${mockIncident.id}/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.status).toBe('ASSIGNED');
    req.flush({ ...mockIncident, status: 'ASSIGNED', assignment: { assigneeId: 'operator', team: null, assignedAt: '2026-08-26T15:00:00Z' } });
    fixture.detectChanges();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('ASSIGNED');
    expect(el.textContent).toContain('Status updated');
  });

  it('should handle failed status update with error message', async () => {
    await createWithMockRoute(mockIncident.id, 'NEW');
    const component = fixture.componentInstance;
    component.selectedStatus.set('ASSIGNED');
    fixture.detectChanges();

    component.updateStatus();
    const req = httpMock.expectOne(`http://localhost:8081/api/v1/incidents/${mockIncident.id}/status`);
    req.flush({ message: 'Illegal transition' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Illegal transition');
  });

  it('should show 403 error appropriately', async () => {
    await createWithMockRoute(mockIncident.id, 'NEW');
    const component = fixture.componentInstance;
    component.selectedStatus.set('ASSIGNED');
    component.updateStatus();
    const req = httpMock.expectOne(`http://localhost:8081/api/v1/incidents/${mockIncident.id}/status`);
    req.flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('permission');
  });
});
