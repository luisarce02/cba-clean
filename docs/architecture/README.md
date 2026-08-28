# CBA Clean — Architecture Diagrams

Two [D2](https://d2lang.com) diagrams generated from the real repository structure
(queried via the project's `graphify` knowledge graph, then cross-checked against
`docker-compose.yml`, `docs/azure-deployment.md`, `infra/containerapps*.bicep|json`,
and `.github/workflows/{ci,cd}.yml`). No invented components or relationships.

## 1. Static system architecture

| File | Description |
|---|---|
| [`system-architecture.d2`](system-architecture.d2) | Source (edit this) |
| [`system-architecture.svg`](system-architecture.svg) | Rendered vector — embed this in the README |
| [`system-architecture.png`](system-architecture.png) | Rendered raster (4168×6904) |

Shows the full Azure target architecture (`docs/azure-deployment.md` §2), which the
local `docker-compose.yml` stack maps onto 1:1:

- **CI/CD**: GitHub → GitHub Actions (CI test matrix, then CD build/push/deploy on
  main) → Docker → Azure Container Registry, authenticated via Azure OIDC federated
  credentials (no stored secrets).
- **Application**: Angular frontend (Nginx, external ingress) → Report Service /
  Incident Service (Spring Boot, internal ingress) inside an Azure Container Apps
  Environment.
- **Data & messaging**: PostgreSQL (reports + outbox_events), MongoDB
  (incidents + processed_events), RabbitMQ (`cba-clean.events` topic exchange +
  retry/DLQ).
- **Auth**: Keycloak (realm `cba-clean`), OIDC PKCE (S256) from the browser, JWT
  Bearer / OAuth2 resource server validation in both backend services.
- **Event flow**: Report Service writes `reports` + `outbox_events` in one
  transaction → `OutboxPublisher` polls `PENDING` every 5s and publishes
  `report.created` → Incident Service's `@RabbitListener` consumes it (retry
  2s/4s/8s → DLQ) → idempotent write to MongoDB (`processed_events` dedup).
- Color legend (top-right of the diagram): blue = application, dark navy =
  infrastructure/CI-CD, green = database, orange = messaging, purple =
  authentication.

### Render / re-render

```bash
# Install D2 once: https://d2lang.com/tour/install
d2 system-architecture.d2 system-architecture.svg --layout elk --theme 0 --pad 40
d2 system-architecture.d2 system-architecture.png --layout elk --theme 0 --pad 40   # first PNG render auto-installs a headless Chromium
```

### Embed in the root README

```markdown
![CBA Clean architecture](docs/architecture/system-architecture.svg)
```

## 2. Animated architecture flow

| File | Description |
|---|---|
| [`flow-animation.d2`](flow-animation.d2) | Source (edit this) |
| [`flow-animation-animated.svg`](flow-animation-animated.svg) | Self-contained looping animated SVG — open directly in a browser |
| [`flow-animation.gif`](flow-animation.gif) | Looping GIF (900×1648), portfolio/Slack/PowerPoint-friendly |
| [`flow-animation/`](flow-animation) | The 11 individual step PNGs the GIF is assembled from (regenerable) |
| [`render.py`](render.py) | Assembles the step PNGs into `flow-animation.gif` |

Built as 11 cumulative D2 "steps" over **one fixed ELK layout** — all nodes and
edges for both flows are declared once (muted/gray), and each step only
re-colors one edge from muted to highlighted. This keeps every frame
pixel-stable (nothing jumps position), so the animation reads as a clean
build-up rather than a re-layout flicker.

1. **Runtime request flow** (steps 1–7): User → Angular → Report Service →
   PostgreSQL (outbox write) → RabbitMQ (`OutboxPublisher` poll/publish) →
   Incident Service (`@RabbitListener` consume) → MongoDB (idempotent write).
2. **Deployment flow** (steps 8–11): GitHub → GitHub Actions (CI, then CD) →
   Azure Container Registry → Azure Container Apps (`az containerapp update`).

### Render / re-render

```bash
# 1. Animated SVG (primary artifact — self-contained, loops forever, no extra deps)
d2 --animate-interval 1600 flow-animation.d2 flow-animation-animated.svg --layout elk --theme 0

# 2. Per-step PNGs -> flow-animation/1.png .. flow-animation/11.png
#    (first PNG render auto-installs a headless Chromium via D2)
d2 flow-animation.d2 flow-animation.png --layout elk --theme 0

# 3. Assemble the PNGs into a looping GIF (requires: pip install Pillow)
python render.py
```

## Tools used

- **[D2](https://d2lang.com) v0.8.2** — diagram-as-code compiler. Not preinstalled
  in this environment; a portable Windows binary was downloaded from the
  [official GitHub releases](https://github.com/d2lang/d2/releases) into a scratch
  directory for this session (no system-wide/admin install). Install it yourself
  for future edits: https://d2lang.com/tour/install
- **ELK layout engine** (`--layout elk`, bundled with D2) — used for both diagrams
  for its cleaner handling of nested containers/boundaries vs. the default `dagre`.
- **Headless Chromium** — auto-installed by D2 on first PNG/animated-SVG render
  (D2 prompts `y/N`; answer `y`). Only needed for raster (PNG) output — SVG output
  never requires it.
- **Python + Pillow** (`pip install Pillow`) — assembles the per-step PNG frames
  into the looping GIF (`render.py`). Not required if you only want the animated
  SVG.

## Known limitations

- The diagrams represent the **Azure target architecture** documented in
  `docs/azure-deployment.md` (Phase 1 foundation). As of this writing that doc
  states no Azure resources are provisioned yet beyond the Phase 1 foundation —
  the local `docker-compose.yml` stack is the architecture actually running
  today, and it maps directly onto every box shown here (see the port/service
  table in `docs/azure-deployment.md` §1 for the exact local equivalents).
- RabbitMQ and MongoDB's *managed* Azure hosting (CloudAMQP vs. self-hosted on
  ACA; MongoDB Atlas vs. Cosmos DB Mongo API) is stated in
  `docs/azure-deployment.md` as an evaluated choice, not yet a provisioned
  resource — shown here per that doc's recommendation.
- GitHub-flavored Markdown sanitizes embedded SVGs, so the CSS-animated
  `flow-animation-animated.svg` may not visibly animate when embedded directly
  in a rendered GitHub README (it will animate correctly when opened directly in
  a browser tab, or via `<img src="...">` in most other renderers). Use
  `flow-animation.gif` for guaranteed animation inside GitHub/Slack/PowerPoint.
- Diagram content reflects the repository at commit `2541950d` (the graphify
  graph's freshness marker). Re-run `graphify update .` and regenerate the
  diagrams after significant architecture changes.
