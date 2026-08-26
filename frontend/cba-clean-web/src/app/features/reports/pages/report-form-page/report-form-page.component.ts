import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ReportService } from '../../services/report.service';
import { ErrorService } from '../../../../core/services/error.service';
import {
  SubmitReportRequest,
  ReportResponse,
  ReportType,
  REPORT_TYPE_LABELS,
  REPORT_TYPE_VALUES,
} from '../../models/report.model';
import { ErrorDisplayComponent } from '../../../../shared/components/error-display/error-display.component';
import { ReportLocationMapComponent } from '../../components/report-location-map/report-location-map.component';

@Component({
  selector: 'app-report-form-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ErrorDisplayComponent, ReportLocationMapComponent],
  templateUrl: './report-form-page.component.html',
  styleUrl: './report-form-page.component.scss',
})
export class ReportFormPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly reportService = inject(ReportService);
  private readonly errorService = inject(ErrorService);

  readonly reportTypes = REPORT_TYPE_VALUES;
  readonly reportTypeLabels = REPORT_TYPE_LABELS;
  readonly submittedReport = signal<ReportResponse | null>(null);
  readonly isSubmitting = signal(false);
  readonly error$ = this.errorService.error$;

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

  onLocationSelected(event: { latitude: number; longitude: number }): void {
    this.reportForm.patchValue({
      latitude: event.latitude,
      longitude: event.longitude,
    });
  }

  onSubmit(): void {
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
      error: () => {
        this.isSubmitting.set(false);
      },
    });
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.reportForm.get(fieldName);
    return !!(field && field.invalid && field.touched);
  }

  getFieldError(fieldName: string): string {
    const field = this.reportForm.get(fieldName);
    if (!field || !field.errors || !field.touched) return '';

    if (field.errors['required']) return `${this.getFieldLabel(fieldName)} is required.`;
    if (field.errors['maxlength']) {
      const max = field.errors['maxlength'].requiredLength;
      return `${this.getFieldLabel(fieldName)} must not exceed ${max} characters.`;
    }
    if (field.errors['min']) return `${this.getFieldLabel(fieldName)} is too small.`;
    if (field.errors['max']) return `${this.getFieldLabel(fieldName)} is too large.`;
    if (field.errors['email']) return 'Please enter a valid email address.';
    if (field.errors['pattern']) return 'Please enter a valid phone number.';

    return '';
  }

  private getFieldLabel(fieldName: string): string {
    const labels: Record<string, string> = {
      reportType: 'Report type',
      description: 'Description',
      latitude: 'Latitude',
      longitude: 'Longitude',
      address: 'Address',
      reporterName: 'Name',
      reporterEmail: 'Email',
      reporterPhone: 'Phone',
    };
    return labels[fieldName] ?? fieldName;
  }
}
