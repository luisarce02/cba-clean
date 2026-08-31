import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IncidentResponse, UpdateIncidentStatusRequest } from '../models/incidents.model';
import { PaginatedResponse } from '../../../core/models/paginated-response.model';

@Injectable({ providedIn: 'root' })
export class IncidentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.incidentApiBaseUrl}/incidents`;

  getIncidents(page: number = 0, size: number = 20, from?: string, to?: string): Observable<PaginatedResponse<IncidentResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<PaginatedResponse<IncidentResponse>>(this.baseUrl, { params });
  }

  getIncident(id: string): Observable<IncidentResponse> {
    return this.http.get<IncidentResponse>(`${this.baseUrl}/${id}`);
  }

  updateIncidentStatus(id: string, request: UpdateIncidentStatusRequest): Observable<IncidentResponse> {
    return this.http.patch<IncidentResponse>(`${this.baseUrl}/${id}/status`, request);
  }
}
