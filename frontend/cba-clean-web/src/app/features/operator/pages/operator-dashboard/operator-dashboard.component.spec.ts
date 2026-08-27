import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { OperatorDashboardComponent } from './operator-dashboard.component';
import { IncidentResponse } from '../../../incidents/models/incidents.model';

describe('OperatorDashboardComponent', () => {
  let fixture: ComponentFixture<OperatorDashboardComponent>;
  let httpMock: HttpTestingController;

  const mockIncidents: IncidentResponse[] = [
    {
      id: '11111111-1111-1111-1111-111111111111',
      reportId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      type: 'ILLEGAL_DUMPING',
      status: 'NEW',
      priority: 'HIGH',
      description: 'Dumped waste',
      location: { latitude: -17.3935, longitude: -66.157, address: 'Cochabamba', zone: null },
      assignment: null,
      closingNote: null,
      createdAt: '2026-08-26T12:00:00Z',
      lastModifiedAt: '2026-08-26T12:00:00Z',
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      reportId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      type: 'LITTER',
      status: 'IN_PROGRESS',
      priority: 'NORMAL',
      description: null,
      location: { latitude: -17.4, longitude: -66.16, address: null, zone: null },
      assignment: { assigneeId: 'operator', team: null, assignedAt: '2026-08-26T13:00:00Z' },
      closingNote: null,
      createdAt: '2026-08-26T11:00:00Z',
      lastModifiedAt: '2026-08-26T13:00:00Z',
    },
    {
      id: '33333333-3333-3333-3333-333333333333',
      reportId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
      type: 'OVERFLOWING_BIN',
      status: 'RESOLVED',
      priority: 'LOW',
      description: 'Bin overflow',
      location: { latitude: -17.5, longitude: -66.2, address: 'Zone 5', zone: 'ZONE-5' },
      assignment: { assigneeId: 'worker-1', team: 'North Crew', assignedAt: '2026-08-26T10:00:00Z' },
      closingNote: 'Collected',
      createdAt: '2026-08-25T10:00:00Z',
      lastModifiedAt: '2026-08-26T14:00:00Z',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorDashboardComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorDashboardComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('http://localhost:8081/api/v1/incidents');
    req.flush([]);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display Operations Overview', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8081/api/v1/incidents').flush([]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Operations Overview');
    expect(el.textContent).toContain('Operator Dashboard');
  });

  it('should have links to operational pages', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8081/api/v1/incidents').flush([]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('View Reports');
    expect(el.textContent).toContain('View Incidents');
    expect(el.textContent).toContain('View Metrics');
  });

  it('should load incidents from backend and render them', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('http://localhost:8081/api/v1/incidents');
    expect(req.request.method).toBe('GET');
    req.flush(mockIncidents);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Recent Incidents');
    // Incidents are rendered with sliced id
    expect(el.textContent).toContain('11111111');
    expect(el.textContent).toContain('22222222');
    expect(el.textContent).toContain('Illegal Dumping');
    // Location
    expect(el.textContent).toContain('Cochabamba');
  });

  it('should render correct status indicator classes', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8081/api/v1/incidents').flush(mockIncidents);
    fixture.detectChanges();

    const indicators = fixture.nativeElement.querySelectorAll('.status-indicator');
    // Should have 3 indicators (one per incident) - sorted by lastModified descending: RESOLVED, IN_PROGRESS, NEW
    expect(indicators.length).toBe(3);
    const classes = Array.from(indicators).map((el: any) => el.className);
    expect(classes.join(' ')).toContain('status-new');
    expect(classes.join(' ')).toContain('status-in-progress');
    expect(classes.join(' ')).toContain('status-resolved');
    // Also check aria-label and text
    expect(fixture.nativeElement.textContent).toContain('NEW');
    expect(fixture.nativeElement.textContent).toContain('IN_PROGRESS');
    expect(fixture.nativeElement.textContent).toContain('RESOLVED');
  });

  it('should display summary counts', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8081/api/v1/incidents').flush(mockIncidents);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Incidents Summary');
    // Counts: NEW 1, IN_PROGRESS 1, RESOLVED 1, total 3
    expect(el.textContent).toContain('Total');
  });

  it('should show empty state when no incidents', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8081/api/v1/incidents').flush([]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No incidents yet');
  });

  it('should show error and retry on failure', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('http://localhost:8081/api/v1/incidents');
    req.flush({ message: 'Server down' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Failed to load incidents');
    expect(el.querySelector('button')?.textContent).toContain('Retry');
  });

  it('should display View Details links with correct router', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8081/api/v1/incidents').flush(mockIncidents);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('a');
    const detailLinks = Array.from(links).filter((a: any) => a.textContent.includes('View Details'));
    expect(detailLinks.length).toBe(3);
  });
});
