import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { OperatorReportsComponent } from './operator-reports.component';
import { ReportResponse } from '../../../reports/models/report.model';

describe('OperatorReportsComponent', () => {
  let fixture: ComponentFixture<OperatorReportsComponent>;
  let httpMock: HttpTestingController;

  const mockReports: ReportResponse[] = [
    {
      id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      type: 'ILLEGAL_DUMPING',
      status: 'NEW',
      priority: 'HIGH',
      description: 'Illegal dump near river',
      location: { latitude: -17.3935, longitude: -66.157, address: 'Cochabamba' },
      reporter: undefined,
      photoIds: [],
      createdAt: '2026-08-26T12:00:00Z',
      lastModifiedAt: '2026-08-26T12:00:00Z',
    },
    {
      id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      type: 'LITTER',
      status: 'NEW',
      priority: 'NORMAL',
      description: undefined,
      location: { latitude: -17.4, longitude: -66.16 },
      reporter: { name: 'Jane' } as any,
      photoIds: [],
      createdAt: '2026-08-25T10:00:00Z',
      lastModifiedAt: '2026-08-25T10:00:00Z',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorReportsComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorReportsComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8080/api/v1/reports').flush([]);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should load reports from backend and render them', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    expect(req.request.method).toBe('GET');
    req.flush(mockReports);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Reports');
    expect(el.textContent).toContain('aaaaaaaa');
    expect(el.textContent).toContain('ILLEGAL_DUMPING');
    expect(el.textContent).toContain('Illegal dump near river');
    expect(el.textContent).toContain('Cochabamba');
  });

  it('should show empty state', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8080/api/v1/reports').flush([]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No reports have been submitted yet');
  });

  it('should show error and allow retry', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Unable to load reports');
    const btn = el.querySelector('button');
    expect(btn?.textContent).toContain('Retry');
  });

  it('should have View Details links', () => {
    fixture.detectChanges();
    httpMock.expectOne('http://localhost:8080/api/v1/reports').flush(mockReports);
    fixture.detectChanges();
    const links = fixture.nativeElement.querySelectorAll('a');
    const detailLinks = Array.from(links).filter((a: any) => a.textContent.includes('View Details'));
    expect(detailLinks.length).toBe(2);
  });
});
