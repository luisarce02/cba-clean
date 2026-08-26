import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorModalComponent } from './error-modal.component';
import { ApiErrorResponse } from '../../../core/models/api-error-response.model';

describe('ErrorModalComponent', () => {
  let component: ErrorModalComponent;
  let fixture: ComponentFixture<ErrorModalComponent>;

  const networkError: ApiErrorResponse = {
    status: 0,
    error: 'Network Error',
    message: 'Unable to connect to the server.',
    timestamp: '2026-08-26T12:00:00Z',
  };

  const serverError: ApiErrorResponse = {
    status: 500,
    error: 'Internal Server Error',
    message: 'An unexpected error occurred.',
    timestamp: '2026-08-26T12:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorModalComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorModalComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    component.error = networkError;
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should render modal with error message', () => {
    component.error = networkError;
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('.modal-container');
    expect(modal).toBeTruthy();

    const title = fixture.nativeElement.querySelector('.modal-title');
    expect(title.textContent).toContain('Report Not Submitted');

    const message = fixture.nativeElement.querySelector('.modal-message');
    expect(message.textContent).toContain('could not be submitted');
  });

  it('should show connection hint for network errors', () => {
    component.error = networkError;
    fixture.detectChanges();

    const hint = fixture.nativeElement.querySelector('.modal-hint');
    expect(hint.textContent).toContain('internet connection');
  });

  it('should show server hint for 500 errors', () => {
    component.error = serverError;
    fixture.detectChanges();

    const hint = fixture.nativeElement.querySelector('.modal-hint');
    expect(hint.textContent).toContain('server issue');
  });

  it('should emit close when close button is clicked', () => {
    component.error = networkError;
    fixture.detectChanges();

    const closeSpy = vi.spyOn(component.close, 'emit');

    const closeBtn = fixture.nativeElement.querySelector('.btn-primary');
    closeBtn.click();

    expect(closeSpy).toHaveBeenCalled();
  });

  it('should emit close when backdrop is clicked', () => {
    component.error = networkError;
    fixture.detectChanges();

    const closeSpy = vi.spyOn(component.close, 'emit');

    const backdrop = fixture.nativeElement.querySelector('.modal-backdrop');
    backdrop.click();

    expect(closeSpy).toHaveBeenCalled();
  });

  it('should not emit close when modal content is clicked', () => {
    component.error = networkError;
    fixture.detectChanges();

    const closeSpy = vi.spyOn(component.close, 'emit');

    const modalContainer = fixture.nativeElement.querySelector('.modal-container');
    modalContainer.click();

    expect(closeSpy).not.toHaveBeenCalled();
  });

  it('should not render when error is null', () => {
    component.error = null;
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('.modal-backdrop');
    expect(modal).toBeNull();
  });

  it('should have accessible dialog role', () => {
    component.error = networkError;
    fixture.detectChanges();

    const backdrop = fixture.nativeElement.querySelector('.modal-backdrop');
    expect(backdrop.getAttribute('role')).toBe('dialog');
    expect(backdrop.getAttribute('aria-modal')).toBe('true');
  });
});
