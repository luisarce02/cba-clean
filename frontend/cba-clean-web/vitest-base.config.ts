import { defineConfig } from 'vitest/config';

// Layered on top of the Angular CLI's own generated Vitest config via the
// `test.runnerConfig` option in angular.json (`ng test` merges this in, it
// does not replace the generated config).
//
// Without an explicit `isolate: true`, Vitest does not guarantee a fresh
// module registry per spec file once several spec files share a worker
// process — which happens whenever the runner's available parallelism is
// low relative to the number of spec files (typically on CI runners with
// fewer CPU cores than a developer machine, not on a full local run).
//
// This repo has two spec files that independently `vi.mock('leaflet', ...)`
// with their own mock instances (report-location-map.component.spec.ts and
// report-form-page.component.spec.ts), plus other spec files
// (app.routing.spec.ts, home.component.spec.ts) that perform real Angular
// Router navigations to routes that lazy-load `ReportLocationMapComponent`.
// Without per-file isolation, whichever spec file's copy of
// `report-location-map.component.ts` gets evaluated first "wins" its
// `import * as L from 'leaflet'` binding for the rest of that worker's
// lifetime, so a later spec file's own mock instance is registered
// correctly for its own `import * as L from 'leaflet'` but the component
// under test keeps calling a *different* (or the real) module's functions —
// producing exactly the "expected vi.fn() to have been called" failures
// seen when CI happened to bundle these files into the same worker, while a
// higher-parallelism run (e.g. a developer machine, or a CI run that
// happened to get more effective workers) never bundled them together and
// so never observed it. `isolate: true` forces a fresh module registry per
// spec file regardless of worker/file bundling, making this deterministic.
export default defineConfig({
  test: {
    isolate: true,
  },
});
