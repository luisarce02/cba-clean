import { Component, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReportService } from '../../services/report.service';
import { AuthService } from '../../../../core/services/auth.service';
import { ErrorService } from '../../../../core/services/error.service';
import {
  SubmitReportRequest,
  ReportResponse,
  ReportType,
  REPORT_TYPE_LABELS,
  REPORT_TYPE_LABELS_ES,
  REPORT_TYPE_VALUES,
} from '../../models/report.model';
import { ErrorDisplayComponent } from '../../../../shared/components/error-display/error-display.component';
import { ErrorModalComponent } from '../../../../shared/components/error-modal/error-modal.component';
import { ReportLocationMapComponent } from '../../components/report-location-map/report-location-map.component';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../shared/pipes/translate.pipe';

@Component({
  selector: 'app-report-form-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ErrorDisplayComponent, ErrorModalComponent, ReportLocationMapComponent, TranslatePipe],
  templateUrl: './report-form-page.component.html',
  styleUrl: './report-form-page.component.scss',
})
export class ReportFormPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly reportService = inject(ReportService);
  private readonly authService = inject(AuthService);
  readonly errorService = inject(ErrorService);
  private readonly translation = inject(TranslationService);

  readonly reportTypes = REPORT_TYPE_VALUES;
  readonly reportTypeLabels = computed(() =>
    this.translation.locale() === 'es' ? REPORT_TYPE_LABELS_ES : REPORT_TYPE_LABELS,
  );
  readonly submittedReport = signal<ReportResponse | null>(null);
  readonly isSubmitting = signal(false);
  readonly showErrorModal = signal(false);

  readonly isAuthenticated = toSignal(this.authService.isAuthenticated$, { initialValue: false });
  readonly hasReporterRole = computed(() => this.authService.hasRole('REPORTER'));

  /** Absence of authentication only — never a role. Drives the read-only demo experience. */
  readonly isDemoVisitor = computed(() => !this.isAuthenticated());
  /** Demo visitors preview the form read-only; authenticated non-reporters see it hidden. */
  readonly canViewForm = computed(() => this.isDemoVisitor() || this.hasReporterRole());

  readonly currentError = toSignal(this.errorService.error$, { initialValue: null });
  readonly isSubmissionFailure = computed(() => {
    const error = this.currentError();
    return error ? this.errorService.isSubmissionFailure(error) : false;
  });

  readonly reportForm: FormGroup = this.fb.group({
    reportType: ['', Validators.required],
    description: ['', Validators.maxLength(2000)],
    latitude: ['', [Validators.required, Validators.min(-90), Validators.max(90)]],
    longitude: ['', [Validators.required, Validators.min(-180), Validators.max(180)]],
    address: ['', Validators.maxLength(300)],
    reporterName: ['', Validators.maxLength(100)],
    reporterEmail: ['', [Validators.email, Validators.maxLength(200)]],
    reporterPhone: ['', Validators.pattern(/^(\+)?[0-9 ]{6,20}$/)],
  });

  constructor() {
    // Keep the form disabled for the whole duration of a read-only demo
    // session, and re-enable it the moment a real REPORTER session exists.
    effect(() => {
      if (this.isDemoVisitor()) {
        this.reportForm.disable({ emitEvent: false });
      } else {
        this.reportForm.enable({ emitEvent: false });
      }
    });
  }

  onLogin(): void {
    this.authService.login();
  }

  onLocationSelected(event: { latitude: number; longitude: number }): void {
    if (this.isDemoVisitor()) return;
    this.reportForm.patchValue({
      latitude: event.latitude,
      longitude: event.longitude,
    });
  }

  onSubmit(): void {
    if (this.isDemoVisitor()) return;
    if (this.reportForm.invalid) {
      this.reportForm.markAllAsTouched();
      return;
    }

    this.errorService.clearError();
    this.isSubmitting.set(true);

    const formValue = this.reportForm.value;

    const request: SubmitReportRequest = {
      reportType: formValue.reportType as ReportType,
      location: {
        latitude: Number(formValue.latitude),
        longitude: Number(formValue.longitude),
        address: formValue.address || undefined,
      },
    };

    if (formValue.description) {
      request.description = formValue.description;
    }

    const hasReporterInfo =
      formValue.reporterName || formValue.reporterEmail || formValue.reporterPhone;

    if (hasReporterInfo) {
      request.reporter = {
        name: formValue.reporterName || undefined,
        email: formValue.reporterEmail || undefined,
        phone: formValue.reporterPhone || undefined,
      };
    }

    this.reportService.submitReport(request).subscribe({
      next: (response) => {
        this.submittedReport.set(response);
        this.isSubmitting.set(false);
        this.reportForm.reset();
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (this.isErrorSubmissionFailure(error)) {
          this.showErrorModal.set(true);
        }
      },
    });
  }

  closeErrorModal(): void {
    this.showErrorModal.set(false);
    this.errorService.clearError();
  }

  isErrorSubmissionFailure(error: HttpErrorResponse): boolean {
    if (error.status === 0) {
      return true;
    }
    if (error.status === 400) {
      const body = error.error;
      if (typeof body === 'object' && body !== null
          && Array.isArray(body.fieldErrors) && body.fieldErrors.length > 0) {
        return false;
      }
      return true;
    }
    if (error.status >= 401) {
      return true;
    }
    return false;
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.reportForm.get(fieldName);
    return !!(field && field.invalid && field.touched);
  }

  getFieldError(fieldName: string): string {
    const field = this.reportForm.get(fieldName);
    if (!field || !field.errors || !field.touched) return '';

    if (field.errors['required']) return this.translation.t('reportForm.fieldRequired', { field: this.getFieldLabel(fieldName) });
    if (field.errors['maxlength']) {
      const max = field.errors['maxlength'].requiredLength;
      return this.translation.t('reportForm.fieldMaxLength', { field: this.getFieldLabel(fieldName), max });
    }
    if (field.errors['min']) return this.translation.t('reportForm.fieldTooSmall', { field: this.getFieldLabel(fieldName) });
    if (field.errors['max']) return this.translation.t('reportForm.fieldTooLarge', { field: this.getFieldLabel(fieldName) });
    if (field.errors['email']) return this.translation.t('reportForm.invalidEmail');
    if (field.errors['pattern']) return this.translation.t('reportForm.invalidPhone');

    return '';
  }

  private getFieldLabel(fieldName: string): string {
    const keys: Record<string, string> = {
      reportType: 'reportForm.fieldLabelReportType',
      description: 'reportForm.fieldLabelDescription',
      latitude: 'reportForm.fieldLabelLatitude',
      longitude: 'reportForm.fieldLabelLongitude',
      address: 'reportForm.fieldLabelAddress',
      reporterName: 'reportForm.fieldLabelName',
      reporterEmail: 'reportForm.fieldLabelEmail',
      reporterPhone: 'reportForm.fieldLabelPhone',
    };
    const key = keys[fieldName];
    return key ? this.translation.t(key) : fieldName;
  }
}
