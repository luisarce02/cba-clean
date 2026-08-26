import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorDisplayComponent } from './error-display.component';
import { ApiErrorResponse } from '../../../core/models/api-error-response.model';

describe('ErrorDisplayComponent', () => {
  let component: ErrorDisplayComponent;
  let fixture: ComponentFixture<ErrorDisplayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorDisplayComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorDisplayComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should not render error panel when error is null', () => {
    component.error = null;
    fixture.detectChanges();

    const errorPanel = fixture.nativeElement.querySelector('.error-panel');
    expect(errorPanel).toBeNull();
  });

  it('should render error message when error is provided', () => {
    const mockError: ApiErrorResponse = {
      status: 400,
      error: 'Bad Request',
      message: 'Request validation failed',
      timestamp: '2026-08-26T12:00:00Z',
    };

    component.error = mockError;
    fixture.detectChanges();
    fixture.detectChanges();

    const errorPanel = fixture.nativeElement.querySelector('.error-panel');
    expect(errorPanel).toBeTruthy();

    const title = fixture.nativeElement.querySelector('.error-title');
    expect(title.textContent).toContain('Bad Request');

    const message = fixture.nativeElement.querySelector('.error-message');
    expect(message.textContent).toContain('Request validation failed');
  });

  it('should render field errors when present', () => {
    const mockError: ApiErrorResponse = {
      status: 400,
      error: 'Bad Request',
      message: 'Request validation failed',
      fieldErrors: [
        { field: 'reportType', message: 'reportType is required' },
        { field: 'location.latitude', message: 'latitude is required' },
      ],
      timestamp: '2026-08-26T12:00:00Z',
    };

    component.error = mockError;
    fixture.detectChanges();
    fixture.detectChanges();

    const fieldErrors = fixture.nativeElement.querySelectorAll('.field-errors li');
    expect(fieldErrors.length).toBe(2);
    expect(fieldErrors[0].textContent).toContain('reportType');
    expect(fieldErrors[0].textContent).toContain('reportType is required');
    expect(fieldErrors[1].textContent).toContain('location.latitude');
  });
});
