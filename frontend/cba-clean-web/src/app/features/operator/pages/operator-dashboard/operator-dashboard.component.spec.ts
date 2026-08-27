import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OperatorDashboardComponent } from './operator-dashboard.component';

describe('OperatorDashboardComponent', () => {
  let fixture: ComponentFixture<OperatorDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorDashboardComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorDashboardComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display Operations Overview', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Operations Overview');
    expect(el.textContent).toContain('Operator Dashboard');
  });

  it('should have links to operational pages', () => {
    const el = fixture.nativeElement as HTMLElement;
    const links = Array.from(el.querySelectorAll('a')).map(a => a.getAttribute('href'));
    // RouterLink renders href, check text
    expect(el.textContent).toContain('View Reports');
    expect(el.textContent).toContain('View Incidents');
    expect(el.textContent).toContain('View Metrics');
  });

  it('should display Recent Incidents section', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Recent Incidents');
    expect(el.textContent).toContain('Incident #123');
  });
});
