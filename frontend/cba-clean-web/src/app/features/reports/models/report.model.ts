export type ReportType =
  | 'LITTER'
  | 'ILLEGAL_DUMPING'
  | 'OVERFLOWING_BIN'
  | 'BULKY_WASTE'
  | 'OTHER';

export type ReportStatus =
  | 'NEW'
  | 'ACKNOWLEDGED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'CANCELLED';

export type ReportPriority =
  | 'LOW'
  | 'NORMAL'
  | 'HIGH'
  | 'CRITICAL';

export interface GeoLocationRequest {
  latitude: number;
  longitude: number;
  address?: string;
}

export interface ReporterRequest {
  name?: string;
  email?: string;
  phone?: string;
}

export interface SubmitReportRequest {
  reportType: ReportType;
  description?: string;
  location: GeoLocationRequest;
  reporter?: ReporterRequest;
  photoIds?: string[];
}

export interface GeoLocationResponse {
  latitude: number;
  longitude: number;
  address?: string;
}

export interface ReporterResponse {
  name?: string;
  email?: string;
  phone?: string;
}

export interface ReportResponse {
  id: string;
  type: ReportType;
  status: ReportStatus;
  priority: ReportPriority;
  description?: string;
  location: GeoLocationResponse;
  reporter?: ReporterResponse;
  photoIds: string[];
  createdAt: string;
  lastModifiedAt: string;
}

export const REPORT_TYPE_LABELS: Record<ReportType, string> = {
  LITTER: 'Litter',
  ILLEGAL_DUMPING: 'Illegal Dumping',
  OVERFLOWING_BIN: 'Overflowing Bin',
  BULKY_WASTE: 'Bulky Waste',
  OTHER: 'Other',
};

export const REPORT_TYPE_VALUES: ReportType[] = [
  'LITTER',
  'ILLEGAL_DUMPING',
  'OVERFLOWING_BIN',
  'BULKY_WASTE',
  'OTHER',
];
