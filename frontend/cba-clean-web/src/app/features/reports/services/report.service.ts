import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { SubmitReportRequest, ReportResponse } from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/reports`;

  submitReport(request: SubmitReportRequest): Observable<ReportResponse> {
    return this.http.post<ReportResponse>(this.baseUrl, request);
  }

  getReport(id: string): Observable<ReportResponse> {
    return this.http.get<ReportResponse>(`${this.baseUrl}/${id}`);
  }

  getReports(): Observable<ReportResponse[]> {
    return this.http.get<ReportResponse[]>(this.baseUrl);
  }
}
