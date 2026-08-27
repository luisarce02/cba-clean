import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IncidentResponse, UpdateIncidentStatusRequest } from '../models/incidents.model';

@Injectable({ providedIn: 'root' })
export class IncidentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.incidentApiBaseUrl}/incidents`;

  getIncidents(): Observable<IncidentResponse[]> {
    return this.http.get<IncidentResponse[]>(this.baseUrl);
  }

  getIncident(id: string): Observable<IncidentResponse> {
    return this.http.get<IncidentResponse>(`${this.baseUrl}/${id}`);
  }

  updateIncidentStatus(id: string, request: UpdateIncidentStatusRequest): Observable<IncidentResponse> {
    return this.http.patch<IncidentResponse>(`${this.baseUrl}/${id}/status`, request);
  }
}
