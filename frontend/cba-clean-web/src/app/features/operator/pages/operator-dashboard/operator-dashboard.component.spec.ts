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

  const paginatedResponse = (incidents: IncidentResponse[]) => ({
    content: incidents,
    page: 0,
    size: 20,
    totalElements: incidents.length,
    totalPages: incidents.length > 0 ? 1 : 0,
  });

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
    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    req.flush(paginatedResponse([]));
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display Operations Overview', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Operations Overview');
    expect(el.textContent).toContain('Operator Dashboard');
  });

  it('should have links to operational pages', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('View Reports');
    expect(el.textContent).toContain('View Incidents');
    expect(el.textContent).toContain('View Metrics');
  });

  it('should load incidents from backend and render them', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    expect(req.request.method).toBe('GET');
    req.flush(paginatedResponse(mockIncidents));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Recent Incidents');
    expect(el.textContent).toContain('11111111');
    expect(el.textContent).toContain('22222222');
    expect(el.textContent).toContain('Illegal Dumping');
    expect(el.textContent).toContain('Cochabamba');
  });

  it('should render correct status indicator classes', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse(mockIncidents));
    fixture.detectChanges();

    const indicators = fixture.nativeElement.querySelectorAll('.status-indicator');
    expect(indicators.length).toBe(3);
    const classes = Array.from(indicators).map((el: any) => el.className);
    expect(classes.join(' ')).toContain('status-new');
    expect(classes.join(' ')).toContain('status-in-progress');
    expect(classes.join(' ')).toContain('status-resolved');
    expect(fixture.nativeElement.textContent).toContain('NEW');
    expect(fixture.nativeElement.textContent).toContain('IN_PROGRESS');
    expect(fixture.nativeElement.textContent).toContain('RESOLVED');
  });

  it('should display summary counts', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse(mockIncidents));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Incidents Summary');
    expect(el.textContent).toContain('Total');
  });

  it('should show empty state when no incidents', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No incidents found for the selected time range.');
  });

  it('should show error and retry on failure', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    req.flush({ message: 'Server down' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Failed to load incidents');
    expect(el.querySelector('.state-error button')?.textContent).toContain('Retry');
  });

  it('should display View Details links with correct router', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse(mockIncidents));
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('a');
    const detailLinks = Array.from(links).filter((a: any) => a.textContent.includes('View Details'));
    expect(detailLinks.length).toBe(3);
  });

  it('should render time range filter presets', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Incidents by Time Range');
    expect(el.textContent).toContain('Today');
    expect(el.textContent).toContain('Last 24 hours');
    expect(el.textContent).toContain('Last 5 days');
    expect(el.textContent).toContain('Last 2 weeks');
    expect(el.textContent).toContain('Last month');
    expect(el.textContent).toContain('Custom range');
  });

  it('should default to Today preset and show it active', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();

    const presetBtns = fixture.nativeElement.querySelectorAll('.btn-preset');
    expect(presetBtns.length).toBe(6);
    expect(presetBtns[0].classList).toContain('active');
  });

  it('should call loadIncidents with from/to when switching preset', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();

    const presetBtns = fixture.nativeElement.querySelectorAll('.btn-preset');
    presetBtns[2].click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    expect(req.request.params.has('from')).toBe(true);
    expect(req.request.params.has('to')).toBe(true);
    req.flush(paginatedResponse([]));
  });

  it('should show custom range inputs when Custom range selected', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();

    const presetBtns = fixture.nativeElement.querySelectorAll('.btn-preset');
    presetBtns[5].click();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.custom-range')).toBeTruthy();
    expect(el.querySelector('.date-input')).toBeTruthy();
  });

  it('should disable Apply button when custom range is invalid', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents').flush(paginatedResponse([]));
    fixture.detectChanges();

    const presetBtns = fixture.nativeElement.querySelectorAll('.btn-preset');
    presetBtns[5].click();
    fixture.detectChanges();

    const applyBtn = fixture.nativeElement.querySelector('.custom-range .btn-primary') as HTMLButtonElement;
    expect(applyBtn.disabled).toBe(true);
  });
});
