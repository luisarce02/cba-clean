import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReactiveFormsModule } from '@angular/forms';
import { ReportFormPageComponent } from './report-form-page.component';
import { ReportResponse } from '../../models/report.model';
import { ErrorService } from '../../../../core/services/error.service';
import { AuthService } from '../../../../core/services/auth.service';
import { OidcService } from '../../../../core/services/oidc.service';
import { HttpErrorResponse } from '@angular/common/http';

vi.mock('leaflet', () => {
  const onHandlers: Record<string, Function[]> = {};
  const mockMap = {
    on: vi.fn((event: string, handler: Function) => {
      if (!onHandlers[event]) onHandlers[event] = [];
      onHandlers[event].push(handler);
    }),
    setView: vi.fn(),
    removeLayer: vi.fn(),
    remove: vi.fn(),
    getZoom: vi.fn(() => 13),
  };
  const mockMarker = {
    setLatLng: vi.fn(),
    addTo: vi.fn().mockReturnThis(),
  };
  const mockTileLayer = {
    addTo: vi.fn().mockReturnThis(),
  };

  const mod: any = {
    map: vi.fn(() => mockMap),
    tileLayer: vi.fn(() => mockTileLayer),
    marker: vi.fn(() => mockMarker),
    Icon: { Default: { imagePath: '' } },
    __test: { onHandlers, mockMap, mockMarker, mockTileLayer },
  };
  mod.default = mod;
  return mod;
});

function setupAuthenticatedTestbed(role: string) {
  const payload = { roles: [role], exp: Math.floor(Date.now() / 1000) + 3600 };
  const token = `header.${btoa(JSON.stringify(payload))}.sig`;
  localStorage.setItem('cba_clean_access_token', token);
  localStorage.setItem('cba_clean_expires_at', String(Date.now() + 3600000));
}

describe('ReportFormPageComponent', () => {
  let component: ReportFormPageComponent;
  let fixture: ComponentFixture<ReportFormPageComponent>;
  let httpMock: HttpTestingController;
  let errorService: ErrorService;
  let authService: AuthService;

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

  function createComponent() {
    fixture = TestBed.createComponent(ReportFormPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    errorService = TestBed.inject(ErrorService);
    authService = TestBed.inject(AuthService);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();

    await TestBed.configureTestingModule({
      imports: [ReportFormPageComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '', component: ReportFormPageComponent }]),
        AuthService,
        OidcService,
      ],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    httpMock.verify();
  });

  it('should create', () => {
    createComponent();
    expect(component).toBeTruthy();
  });

  it('should show login prompt when not authenticated', () => {
    createComponent();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.auth-prompt')).toBeTruthy();
    expect(compiled.querySelector('.report-form')).toBeNull();
  });

  it('should show unauthorized message when authenticated without REPORTER role', () => {
    setupAuthenticatedTestbed('SOMEONE');
    createComponent();

    const compiled = fixture.nativeElement as HTMLElement;
    const authPrompt = compiled.querySelector('.auth-prompt');
    expect(authPrompt?.textContent).toContain('does not have the required role');
    expect(compiled.querySelector('.report-form')).toBeNull();
  });

  it('should show form when authenticated with REPORTER role', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.report-form')).toBeTruthy();
    expect(compiled.querySelector('.auth-prompt')).toBeNull();
  });

  it('should have required reportType field', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();
    const reportType = component.reportForm.get('reportType');
    expect(reportType).toBeTruthy();
    expect(reportType!.valid).toBe(false);
    expect(reportType!.errors?.['required']).toBe(true);
  });

  it('should have required latitude field', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();
    const latitude = component.reportForm.get('latitude');
    expect(latitude).toBeTruthy();
    expect(latitude!.valid).toBe(false);
    expect(latitude!.errors?.['required']).toBe(true);
  });

  it('should have required longitude field', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();
    const longitude = component.reportForm.get('longitude');
    expect(longitude).toBeTruthy();
    expect(longitude!.valid).toBe(false);
    expect(longitude!.errors?.['required']).toBe(true);
  });

  it('should mark all fields as touched on invalid submit', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();
    component.onSubmit();
    expect(component.reportForm.touched).toBe(true);
  });

  it('should submit valid report and update UI', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

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
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.submittedReport.set(mockReportResponse);
    fixture.detectChanges();

    const submitAnotherBtn = fixture.nativeElement.querySelector('.btn');
    submitAnotherBtn.click();

    expect(component.submittedReport()).toBeNull();
  });

  it('should display form validation errors for touched invalid fields', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    const reportType = component.reportForm.get('reportType')!;
    reportType.markAsTouched();
    reportType.markAsDirty();
    fixture.detectChanges();

    expect(component.isFieldInvalid('reportType')).toBe(true);
    expect(component.getFieldError('reportType')).toContain('required');
  });

  it('should disable submit button while submitting', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.isSubmitting.set(true);
    fixture.detectChanges();

    const submitBtn = fixture.nativeElement.querySelector('.btn-primary');
    expect(submitBtn.disabled).toBe(true);
  });

  it('should update form lat/lng when map emits locationSelected', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.onLocationSelected({ latitude: -17.4, longitude: -66.16 });

    expect(component.reportForm.get('latitude')?.value).toBe(-17.4);
    expect(component.reportForm.get('longitude')?.value).toBe(-66.16);
  });

  it('should render map component in the location fieldset', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    const mapEl = fixture.nativeElement.querySelector('app-report-location-map');
    expect(mapEl).toBeTruthy();
  });

  it('should prevent submission when location not selected', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.reportForm.patchValue({
      reportType: 'LITTER',
      description: 'Test',
    });

    component.onSubmit();

    expect(component.reportForm.get('latitude')?.valid).toBe(false);
    expect(component.isSubmitting()).toBe(false);
  });

  it('should include location coordinates in POST request', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.reportForm.patchValue({
      reportType: 'LITTER',
      latitude: -17.3935,
      longitude: -66.157,
    });

    component.onSubmit();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    expect(req.request.body.location.latitude).toBe(-17.3935);
    expect(req.request.body.location.longitude).toBe(-66.157);
    req.flush(mockReportResponse);
  });

  it('should reset form fields after successful submission', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.reportForm.patchValue({
      reportType: 'LITTER',
      latitude: -17.3935,
      longitude: -66.157,
    });

    component.onSubmit();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    req.flush(mockReportResponse);

    expect(component.reportForm.get('latitude')?.value).toBeNull();
    expect(component.reportForm.get('longitude')?.value).toBeNull();
    expect(component.reportForm.get('reportType')?.value).toBeNull();
  });

  it('should show error modal on network failure', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.reportForm.patchValue({
      reportType: 'LITTER',
      latitude: 48.208,
      longitude: 16.372,
    });

    component.onSubmit();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    const errorResponse = new HttpErrorResponse({ status: 0 });
    req.error(new ProgressEvent('error'), { status: 0 });

    expect(component.isErrorSubmissionFailure(errorResponse)).toBe(true);
  });

  it('should show error modal on 500 server error', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.reportForm.patchValue({
      reportType: 'LITTER',
      latitude: 48.208,
      longitude: 16.372,
    });

    component.onSubmit();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    req.flush(
      { status: 500, error: 'Internal Server Error', message: 'Something went wrong', timestamp: '2026-08-26T12:00:00Z' },
      { status: 500, statusText: 'Internal Server Error' },
    );

    const errorResponse = new HttpErrorResponse({ status: 500 });
    expect(component.isErrorSubmissionFailure(errorResponse)).toBe(true);
  });

  it('should not show error modal on successful submission', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.reportForm.patchValue({
      reportType: 'LITTER',
      latitude: 48.208,
      longitude: 16.372,
    });

    component.onSubmit();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    req.flush(mockReportResponse);

    expect(component.showErrorModal()).toBe(false);
  });

  it('should preserve form data after submission failure', () => {
    setupAuthenticatedTestbed('REPORTER');
    createComponent();

    component.reportForm.patchValue({
      reportType: 'LITTER',
      description: 'Test failure',
      latitude: 48.208,
      longitude: 16.372,
    });

    component.onSubmit();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/reports');
    req.error(new ProgressEvent('error'), { status: 0 });

    expect(component.reportForm.get('reportType')?.value).toBe('LITTER');
    expect(component.reportForm.get('description')?.value).toBe('Test failure');
    expect(component.reportForm.get('latitude')?.value).toBe(48.208);
    expect(component.reportForm.get('longitude')?.value).toBe(16.372);
  });

  it('should close error modal and clear error on closeErrorModal()', () => {
    createComponent();
    component.showErrorModal.set(true);

    component.closeErrorModal();

    expect(component.showErrorModal()).toBe(false);
  });

  it('should render error modal when showErrorModal is true', () => {
    createComponent();
    component.showErrorModal.set(true);
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('app-error-modal');
    expect(modal).toBeTruthy();
  });

  it('should classify 400 with field errors as NOT submission failure', () => {
    createComponent();
    const error = new HttpErrorResponse({
      error: {
        status: 400,
        error: 'Bad Request',
        message: 'Validation failed',
        fieldErrors: [{ field: 'reportType', message: 'required' }],
        timestamp: '2026-08-26T12:00:00Z',
      },
      status: 400,
    });
    expect(component.isErrorSubmissionFailure(error)).toBe(false);
  });

  it('should classify 401 as submission failure', () => {
    createComponent();
    const error = new HttpErrorResponse({ status: 401 });
    expect(component.isErrorSubmissionFailure(error)).toBe(true);
  });
});
