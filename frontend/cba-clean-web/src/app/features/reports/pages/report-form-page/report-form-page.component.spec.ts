import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReactiveFormsModule } from '@angular/forms';
import { ReportFormPageComponent } from './report-form-page.component';
import { ReportResponse } from '../../models/report.model';

describe('ReportFormPageComponent', () => {
  let component: ReportFormPageComponent;
  let fixture: ComponentFixture<ReportFormPageComponent>;
  let httpMock: HttpTestingController;

  const mockReportResponse: ReportResponse = {
    id: '7f9c24e8-0b5a-4d1e-9f2a-3c6b8d7e1a45',
    type: 'LITTER',
    status: 'NEW',
    priority: 'NORMAL',
    description: 'Test description',
    location: { latitude: 48.208, longitude: 16.372, address: 'Test Address' },
    reporter: undefined,
    photoIds: [],
    createdAt: '2026-08-26T12:00:00Z',
    lastModifiedAt: '2026-08-26T12:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportFormPageComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '', component: ReportFormPageComponent }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportFormPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have required reportType field', () => {
    const reportType = component.reportForm.get('reportType');
    expect(reportType).toBeTruthy();
    expect(reportType!.valid).toBe(false);
    expect(reportType!.errors?.['required']).toBe(true);
  });

  it('should have required latitude field', () => {
    const latitude = component.reportForm.get('latitude');
    expect(latitude).toBeTruthy();
    expect(latitude!.valid).toBe(false);
    expect(latitude!.errors?.['required']).toBe(true);
  });

  it('should have required longitude field', () => {
    const longitude = component.reportForm.get('longitude');
    expect(longitude).toBeTruthy();
    expect(longitude!.valid).toBe(false);
    expect(longitude!.errors?.['required']).toBe(true);
  });

  it('should mark all fields as touched on invalid submit', () => {
    component.onSubmit();
    expect(component.reportForm.touched).toBe(true);
  });

  it('should submit valid report and update UI', () => {
    component.reportForm.patchValue({
      reportType: 'LITTER',
      description: 'Test description',
      latitude: 48.208,
      longitude: 16.372,
      address: 'Test Address',
    });

    component.onSubmit();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    expect(req.request.method).toBe('POST');
    req.flush(mockReportResponse);

    expect(component.submittedReport()).toEqual(mockReportResponse);
  });

  it('should reset form and clear submitted report after submission', () => {
    component.submittedReport.set(mockReportResponse);
    fixture.detectChanges();

    const submitAnotherBtn = fixture.nativeElement.querySelector('.btn');
    submitAnotherBtn.click();

    expect(component.submittedReport()).toBeNull();
  });

  it('should display form validation errors for touched invalid fields', () => {
    const reportType = component.reportForm.get('reportType')!;
    reportType.markAsTouched();
    reportType.markAsDirty();
    fixture.detectChanges();

    expect(component.isFieldInvalid('reportType')).toBe(true);
    expect(component.getFieldError('reportType')).toContain('required');
  });

  it('should disable submit button while submitting', () => {
    component.isSubmitting.set(true);
    fixture.detectChanges();

    const submitBtn = fixture.nativeElement.querySelector('.btn-primary');
    expect(submitBtn.disabled).toBe(true);
  });
});
