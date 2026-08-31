import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReportService } from './report.service';
import { SubmitReportRequest, ReportResponse } from '../models/report.model';

describe('ReportService', () => {
  let service: ReportService;
  let httpMock: HttpTestingController;

  const mockReportResponse: ReportResponse = {
    id: '7f9c24e8-0b5a-4d1e-9f2a-3c6b8d7e1a45',
    type: 'LITTER',
    status: 'NEW',
    priority: 'NORMAL',
    description: 'Test description',
    location: {
      latitude: 48.208,
      longitude: 16.372,
      address: 'Test Address',
    },
    reporter: undefined,
    photoIds: [],
    createdAt: '2026-08-26T12:00:00Z',
    lastModifiedAt: '2026-08-26T12:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ReportService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should submit a report successfully', () => {
    const request: SubmitReportRequest = {
      reportType: 'LITTER',
      description: 'Test description',
      location: {
        latitude: 48.208,
        longitude: 16.372,
        address: 'Test Address',
      },
    };

    service.submitReport(request).subscribe((response) => {
      expect(response).toEqual(mockReportResponse);
      expect(response.id).toBe('7f9c24e8-0b5a-4d1e-9f2a-3c6b8d7e1a45');
      expect(response.type).toBe('LITTER');
      expect(response.status).toBe('NEW');
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockReportResponse);
  });

  it('should retrieve a report by id', () => {
    const reportId = '7f9c24e8-0b5a-4d1e-9f2a-3c6b8d7e1a45';

    service.getReport(reportId).subscribe((response) => {
      expect(response).toEqual(mockReportResponse);
      expect(response.id).toBe(reportId);
    });

    const req = httpMock.expectOne(
      `http://localhost:8080/api/v1/reports/${reportId}`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockReportResponse);
  });

  it('should handle 400 validation error on submit', () => {
    const request: SubmitReportRequest = {
      reportType: 'LITTER',
      location: { latitude: 48.208, longitude: 16.372 },
    };

    const errorResponse = {
      status: 400,
      error: 'Bad Request',
      message: 'Request validation failed',
      fieldErrors: [{ field: 'reportType', message: 'reportType is required' }],
      timestamp: '2026-08-26T12:00:00Z',
    };

    service.submitReport(request).subscribe({
      next: () => {
        throw new Error('Expected an error');
      },
      error: (error) => {
        expect(error.status).toBe(400);
      },
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    req.flush(errorResponse, { status: 400, statusText: 'Bad Request' });
  });

  it('should handle 404 error on getReport', () => {
    const reportId = 'non-existent-id';

    service.getReport(reportId).subscribe({
      next: () => {
        throw new Error('Expected an error');
      },
      error: (error) => {
        expect(error.status).toBe(404);
      },
    });

    const req = httpMock.expectOne(
      `http://localhost:8080/api/v1/reports/${reportId}`,
    );
    req.flush(
      { status: 404, error: 'Not Found', message: 'Report not found' },
      { status: 404, statusText: 'Not Found' },
    );
  });

  it('should retrieve reports list', () => {
    service.getReports().subscribe((response) => {
      expect(response.content.length).toBe(1);
      expect(response.content[0].id).toBe('7f9c24e8-0b5a-4d1e-9f2a-3c6b8d7e1a45');
      expect(response.page).toBe(0);
      expect(response.size).toBe(10);
      expect(response.totalElements).toBe(1);
      expect(response.totalPages).toBe(1);
    });

    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8080/api/v1/reports');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [mockReportResponse], page: 0, size: 10, totalElements: 1, totalPages: 1 });
  });

  it('should handle empty reports list', () => {
    service.getReports().subscribe((response) => {
      expect(response.content.length).toBe(0);
      expect(response.totalElements).toBe(0);
      expect(response.totalPages).toBe(0);
    });

    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8080/api/v1/reports');
    req.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });
});
