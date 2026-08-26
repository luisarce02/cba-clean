import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReportLocationMapComponent } from './report-location-map.component';
import * as L from 'leaflet';

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

vi.mock('leaflet', () => ({
  default: {
    map: vi.fn(() => mockMap),
    tileLayer: vi.fn(() => mockTileLayer),
    marker: vi.fn(() => mockMarker),
    Icon: { Default: { imagePath: '' } },
  },
  map: vi.fn(() => mockMap),
  tileLayer: vi.fn(() => mockTileLayer),
  marker: vi.fn(() => mockMarker),
  Icon: { Default: { imagePath: '' } },
}));

function fireMapClick(lat: number, lng: number) {
  (onHandlers['click'] || []).forEach((h) => h({ latlng: { lat, lng } }));
}

describe('ReportLocationMapComponent', () => {
  let component: ReportLocationMapComponent;
  let fixture: ComponentFixture<ReportLocationMapComponent>;

  beforeEach(async () => {
    Object.values(onHandlers).forEach((handlers) => (handlers.length = 0));
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [ReportLocationMapComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportLocationMapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should use Cochabamba coordinates as default center', () => {
    const mockedL = vi.mocked(L);
    expect(mockedL.map).toHaveBeenCalled();
    const options = (mockedL.map as any).mock.calls[0][1];
    expect(options.center).toEqual([-17.3935, -66.1570]);
    expect(options.zoom).toBe(13);
  });

  it('should emit locationSelected when map is clicked', () => {
    const emitSpy = vi.spyOn(component.locationSelected, 'emit');
    fireMapClick(-17.4, -66.16);
    expect(emitSpy).toHaveBeenCalledWith({ latitude: -17.4, longitude: -66.16 });
  });

  it('should update marker when location is clicked', () => {
    const mockedL = vi.mocked(L);
    fireMapClick(-17.4, -66.16);
    expect(mockedL.marker).toHaveBeenCalledWith([-17.4, -66.16]);
  });

  it('should show coordinates in template after location selected', () => {
    const freshFixture = TestBed.createComponent(ReportLocationMapComponent);
    freshFixture.componentInstance.latitude = -17.4;
    freshFixture.componentInstance.longitude = -66.16;
    freshFixture.detectChanges();

    const coordsEl = freshFixture.nativeElement.querySelector('.map-coords');
    expect(coordsEl).toBeTruthy();
    expect(coordsEl.textContent).toContain('-17.4');
    expect(coordsEl.textContent).toContain('-66.16');
  });

  it('should not show coordinates when no location selected', () => {
    const coordsEl = fixture.nativeElement.querySelector('.map-coords');
    expect(coordsEl).toBeFalsy();
  });

  it('should place marker from initial input values', () => {
    const mockedL = vi.mocked(L);
    (mockedL.marker as any).mockClear();

    const newFixture = TestBed.createComponent(ReportLocationMapComponent);
    const newComponent = newFixture.componentInstance;
    newComponent.latitude = -17.39;
    newComponent.longitude = -66.15;
    newFixture.detectChanges();

    expect(mockedL.marker).toHaveBeenCalledWith([-17.39, -66.15]);
  });

  it('should reset marker when inputs change to null', () => {
    (component as any).marker = mockMarker;

    component.ngOnChanges({
      latitude: {
        previousValue: -17.39,
        currentValue: null,
        firstChange: false,
        isFirstChange: () => false,
      },
      longitude: {
        previousValue: -66.15,
        currentValue: null,
        firstChange: false,
        isFirstChange: () => false,
      },
    });

    expect(mockMap.removeLayer).toHaveBeenCalled();
    expect(mockMap.setView).toHaveBeenCalledWith([-17.3935, -66.1570], 13);
  });

  it('should contain a use-my-location button', () => {
    const btn = fixture.nativeElement.querySelector('.map-btn');
    expect(btn).toBeTruthy();
    expect(btn.textContent).toContain('Use my location');
  });

  it('should use OpenStreetMap tiles', () => {
    const mockedL = vi.mocked(L);
    expect(mockedL.tileLayer).toHaveBeenCalledWith(
      expect.stringContaining('openstreetmap.org'),
      expect.objectContaining({
        attribution: expect.stringContaining('OpenStreetMap'),
      }),
    );
  });
});
