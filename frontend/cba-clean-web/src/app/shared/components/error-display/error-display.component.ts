import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiErrorResponse } from '../../../core/models/api-error-response.model';

@Component({
  selector: 'app-error-display',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (error) {
      <div class="error-panel" role="alert">
        <strong class="error-title">{{ error.error }}</strong>
        <p class="error-message">{{ error.message }}</p>
        @if (error.fieldErrors && error.fieldErrors.length > 0) {
          <ul class="field-errors">
            @for (fe of error.fieldErrors; track fe.field) {
              <li><strong>{{ fe.field }}:</strong> {{ fe.message }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: `
    .error-panel {
      background: #fef2f2;
      border: 1px solid #fca5a5;
      border-radius: 6px;
      padding: 1rem 1.25rem;
      margin-bottom: 1.5rem;
    }

    .error-title {
      color: #b91c1c;
      display: block;
      margin-bottom: 0.25rem;
    }

    .error-message {
      color: #991b1b;
      margin: 0 0 0.5rem;
    }

    .field-errors {
      margin: 0;
      padding-left: 1.25rem;
      color: #991b1b;
      font-size: 0.875rem;

      li {
        margin-bottom: 0.125rem;
      }
    }
  `,
})
export class ErrorDisplayComponent {
  @Input({ required: true }) error: ApiErrorResponse | null = null;
}
