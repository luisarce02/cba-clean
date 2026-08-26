export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
  fieldErrors?: FieldError[];
  timestamp: string;
}
