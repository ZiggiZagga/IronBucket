import { randomUUID } from 'node:crypto';
import { NextResponse } from 'next/server';

const REQUEST_ID_HEADER = 'x-request-id';
const CORRELATION_ID_HEADER = 'x-correlation-id';

export function resolveCorrelationId(headers: Headers): string {
  const incomingCorrelation = headers.get(CORRELATION_ID_HEADER);
  if (incomingCorrelation && incomingCorrelation.trim().length > 0) {
    return incomingCorrelation;
  }

  const incomingRequestId = headers.get(REQUEST_ID_HEADER);
  if (incomingRequestId && incomingRequestId.trim().length > 0) {
    return incomingRequestId;
  }

  return randomUUID();
}

export function withCorrelationHeaders(response: NextResponse, correlationId: string): NextResponse {
  response.headers.set(CORRELATION_ID_HEADER, correlationId);
  response.headers.set(REQUEST_ID_HEADER, correlationId);
  return response;
}