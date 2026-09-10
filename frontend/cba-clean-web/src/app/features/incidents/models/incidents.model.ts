export type IncidentStatus = 'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'CANCELLED';
export type IncidentPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
export type IncidentType = 'LITTER' | 'ILLEGAL_DUMPING' | 'OVERFLOWING_BIN' | 'BULKY_WASTE' | 'MISSED_COLLECTION' | 'OTHER';

export interface IncidentLocationResponse {
  latitude: number;
  longitude: number;
  address?: string | null;
  zone?: string | null;
}

export interface AssignmentResponse {
  assigneeId: string;
  team?: string | null;
  assignedAt: string;
}

export interface IncidentResponse {
  id: string;
  reportId: string;
  type: IncidentType;
  status: IncidentStatus;
  priority: IncidentPriority;
  description?: string | null;
  location: IncidentLocationResponse;
  assignment?: AssignmentResponse | null;
  closingNote?: string | null;
  createdAt: string;
  lastModifiedAt: string;
}

export interface UpdateIncidentStatusRequest {
  status: IncidentStatus;
  closingNote?: string;
  assigneeId?: string;
}

export const INCIDENT_STATUS_LABELS: Record<IncidentStatus, string> = {
  NEW: 'New',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CANCELLED: 'Cancelled',
};

export const INCIDENT_STATUS_LABELS_ES: Record<IncidentStatus, string> = {
  NEW: 'Nuevo',
  ASSIGNED: 'Asignado',
  IN_PROGRESS: 'En Progreso',
  RESOLVED: 'Resuelto',
  CANCELLED: 'Cancelado',
};

export const INCIDENT_PRIORITY_LABELS: Record<IncidentPriority, string> = {
  LOW: 'Low',
  NORMAL: 'Normal',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

export const INCIDENT_PRIORITY_LABELS_ES: Record<IncidentPriority, string> = {
  LOW: 'Baja',
  NORMAL: 'Normal',
  HIGH: 'Alta',
  CRITICAL: 'Crítica',
};

export const INCIDENT_TYPE_LABELS: Record<IncidentType, string> = {
  LITTER: 'Litter',
  ILLEGAL_DUMPING: 'Illegal Dumping',
  OVERFLOWING_BIN: 'Overflowing Bin',
  BULKY_WASTE: 'Bulky Waste',
  MISSED_COLLECTION: 'Missed Collection',
  OTHER: 'Other',
};

export const INCIDENT_TYPE_LABELS_ES: Record<IncidentType, string> = {
  LITTER: 'Basura',
  ILLEGAL_DUMPING: 'Vertido Ilegal',
  OVERFLOWING_BIN: 'Contenedor Desbordado',
  BULKY_WASTE: 'Residuo Voluminoso',
  MISSED_COLLECTION: 'Recolección Perdida',
  OTHER: 'Otro',
};
