import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { SubmitReportRequest, ReportResponse } from '../models/report.model';
import { PaginatedResponse } from '../../../core/models/paginated-response.model';

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

  getReports(page: number = 0, size: number = 10): Observable<PaginatedResponse<ReportResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PaginatedResponse<ReportResponse>>(this.baseUrl, { params });
  }
}
