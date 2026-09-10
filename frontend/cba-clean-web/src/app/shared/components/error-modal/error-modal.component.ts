import { Component, EventEmitter, Input, Output, AfterViewInit, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiErrorResponse } from '../../../core/models/api-error-response.model';
import { TranslationService } from '../../../core/services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-error-modal',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `
    @if (error) {
      <div class="modal-backdrop" (click)="onBackdropClick($event)" (keydown.escape)="close.emit()" role="dialog" aria-modal="true" [attr.aria-label]="title">
        <div class="modal-container" #modalContainer tabindex="-1">
          <div class="modal-header">
            <h2 class="modal-title">{{ title }}</h2>
          </div>
          <div class="modal-body">
            <p class="modal-message">{{ message }}</p>
            @if (error.status === 0) {
              <p class="modal-hint">{{ 'errorModal.hintNetwork' | translate }}</p>
            } @else if (error.status >= 500) {
              <p class="modal-hint">{{ 'errorModal.hintServer' | translate }}</p>
            }
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-primary" (click)="close.emit()" autofocus>{{ 'errorModal.close' | translate }}</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .modal-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-container {
      background: #fff;
      border-radius: 8px;
      box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
      max-width: 480px;
      width: 90%;
      padding: 1.5rem;
      outline: none;
    }

    .modal-header {
      margin-bottom: 1rem;
    }

    .modal-title {
      margin: 0;
      font-size: 1.25rem;
      color: #b91c1c;
    }

    .modal-body {
      margin-bottom: 1.5rem;
    }

    .modal-message {
      margin: 0 0 0.5rem;
      color: #444;
      line-height: 1.5;
    }

    .modal-hint {
      margin: 0;
      font-size: 0.875rem;
      color: #666;
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
    }

    .btn {
      padding: 0.5rem 1.25rem;
      border: 1px solid #ccc;
      border-radius: 4px;
      font-size: 0.9375rem;
      cursor: pointer;
    }

    .btn-primary {
      background: #1976d2;
      color: #fff;
      border-color: #1976d2;

      &:hover {
        background: #1565c0;
      }
    }
  `,
})
export class ErrorModalComponent implements AfterViewInit {
  private readonly translation = inject(TranslationService);

  @Input({ required: true }) error: ApiErrorResponse | null = null;
  @Input() title = this.translation.t('errorModal.defaultTitle');
  @Input() message = this.translation.t('errorModal.defaultMessage');
  @Output() close = new EventEmitter<void>();

  @ViewChild('modalContainer') modalContainer!: ElementRef<HTMLElement>;

  ngAfterViewInit(): void {
    this.modalContainer?.nativeElement?.focus();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close.emit();
    }
  }
}
