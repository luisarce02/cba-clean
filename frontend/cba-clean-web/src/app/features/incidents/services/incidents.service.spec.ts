import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { IncidentService } from './incidents.service';
import { IncidentResponse } from '../models/incidents.model';

describe('IncidentService', () => {
  let service: IncidentService;
  let httpMock: HttpTestingController;

  const mockIncident: IncidentResponse = {
    id: '11111111-1111-1111-1111-111111111111',
    reportId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    type: 'LITTER',
    status: 'NEW',
    priority: 'NORMAL',
    description: 'Test',
    location: { latitude: -17.3935, longitude: -66.157, address: null, zone: null },
    assignment: null,
    closingNote: null,
    createdAt: '2026-08-26T12:00:00Z',
    lastModifiedAt: '2026-08-26T12:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), IncidentService],
    });
    service = TestBed.inject(IncidentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should fetch incidents', () => {
    service.getIncidents().subscribe(data => {
      expect(data.content.length).toBe(1);
      expect(data.page).toBe(0);
      expect(data.size).toBe(20);
      expect(data.totalElements).toBe(1);
    });
    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [mockIncident], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should fetch incidents with date range params', () => {
    service.getIncidents(0, 20, '2026-08-01T00:00:00Z', '2026-08-31T23:59:59Z').subscribe(data => {
      expect(data.content.length).toBe(1);
    });
    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    expect(req.request.params.get('from')).toBe('2026-08-01T00:00:00Z');
    expect(req.request.params.get('to')).toBe('2026-08-31T23:59:59Z');
    req.flush({ content: [mockIncident], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should fetch incident by id', () => {
    service.getIncident(mockIncident.id).subscribe(data => expect(data.id).toBe(mockIncident.id));
    const req = httpMock.expectOne(`http://localhost:8081/api/v1/incidents/${mockIncident.id}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockIncident);
  });

  it('should update incident status', () => {
    const updated = { ...mockIncident, status: 'ASSIGNED' as const };
    service.updateIncidentStatus(mockIncident.id, { status: 'ASSIGNED' }).subscribe(data => expect(data.status).toBe('ASSIGNED'));
    const req = httpMock.expectOne(`http://localhost:8081/api/v1/incidents/${mockIncident.id}/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.status).toBe('ASSIGNED');
    req.flush(updated);
  });

  it('should propagate error on failed fetch', () => {
    service.getIncidents().subscribe({
      next: () => { throw new Error('should have failed'); },
      error: err => expect(err.status).toBe(500),
    });
    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8081/api/v1/incidents');
    req.flush({}, { status: 500, statusText: 'Server Error' });
  });
});
