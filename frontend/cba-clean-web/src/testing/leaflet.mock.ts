import { vi } from 'vitest';

/**
 * Canonical Leaflet test double, shared by every spec file that can reach
 * `ReportLocationMapComponent` — either through a direct import or through
 * Angular Router lazy-loading `ReportFormPageComponent` during a real
 * navigation (e.g. app.routing.spec.ts, home.component.spec.ts).
 *
 * Route/guard specs that never inspect map internals just need this so the
 * real `leaflet` package — which touches the DOM — is never evaluated as a
 * side effect of an unrelated navigation. Specs that do assert on map
 * behavior (report-location-map.component.spec.ts,
 * report-form-page.component.spec.ts) call this from inside their own
 * `vi.mock('leaflet', ...)` factory and keep the returned handles for
 * assertions. Each call returns a fresh, independent instance.
 */
export function createLeafletMockModule() {
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
}
