import { IncidentResponse } from '../../../incidents/models/incidents.model';

/**
 * Static sample incidents shown to unauthenticated visitors on the Operator
 * dashboard. GET /api/v1/incidents requires ROLE_OPERATOR server-side, so a
 * visitor session (which never holds a token) cannot fetch real data; this
 * fixture lets the dashboard demonstrate its layout without calling — or
 * weakening — that protected endpoint.
 */
export const DEMO_INCIDENTS: IncidentResponse[] = [
  {
    id: 'demo-0000-0000-0000-000000000001',
    reportId: 'demo-report-0001',
    type: 'ILLEGAL_DUMPING',
    status: 'NEW',
    priority: 'HIGH',
    description: 'Sample incident — illegal dumping reported near a residential block.',
    location: { latitude: -17.3935, longitude: -66.157, address: 'Av. Heroínas, Cochabamba', zone: 'ZONE-CENTRO' },
    assignment: null,
    closingNote: null,
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    lastModifiedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
  },
  {
    id: 'demo-0000-0000-0000-000000000002',
    reportId: 'demo-report-0002',
    type: 'OVERFLOWING_BIN',
    status: 'ASSIGNED',
    priority: 'NORMAL',
    description: 'Sample incident — overflowing public bin.',
    location: { latitude: -17.3895, longitude: -66.1568, address: 'Plaza Colón, Cochabamba', zone: 'ZONE-COLON' },
    assignment: { assigneeId: 'demo-crew', team: 'North Crew', assignedAt: new Date(Date.now() - 60 * 60 * 1000).toISOString() },
    closingNote: null,
    createdAt: new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString(),
    lastModifiedAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
  },
  {
    id: 'demo-0000-0000-0000-000000000003',
    reportId: 'demo-report-0003',
    type: 'LITTER',
    status: 'IN_PROGRESS',
    priority: 'LOW',
    description: 'Sample incident — scattered litter along a walkway.',
    location: { latitude: -17.4013, longitude: -66.1497, address: null, zone: 'ZONE-SUR' },
    assignment: { assigneeId: 'demo-crew-2', team: 'South Crew', assignedAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString() },
    closingNote: null,
    createdAt: new Date(Date.now() - 8 * 60 * 60 * 1000).toISOString(),
    lastModifiedAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
  },
  {
    id: 'demo-0000-0000-0000-000000000004',
    reportId: 'demo-report-0004',
    type: 'BULKY_WASTE',
    status: 'RESOLVED',
    priority: 'NORMAL',
    description: 'Sample incident — bulky waste collected after resident report.',
    location: { latitude: -17.3852, longitude: -66.1653, address: 'Zona Queru Queru, Cochabamba', zone: 'ZONE-NORTE' },
    assignment: { assigneeId: 'demo-crew', team: 'North Crew', assignedAt: new Date(Date.now() - 26 * 60 * 60 * 1000).toISOString() },
    closingNote: 'Collected and disposed at municipal facility.',
    createdAt: new Date(Date.now() - 30 * 60 * 60 * 1000).toISOString(),
    lastModifiedAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
  },
];
