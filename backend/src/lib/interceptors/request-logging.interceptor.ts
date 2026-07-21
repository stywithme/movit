import {
  Injectable,
  NestInterceptor,
  ExecutionContext,
  CallHandler,
} from '@nestjs/common';
import { Observable } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { Request } from 'express';

@Injectable()
export class RequestLoggingInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const httpContext = context.switchToHttp();
    const request = httpContext.getRequest<Request>();
    const startTime = Date.now();

    const method = request.method;
    const path = request.originalUrl || request.url;
    const ip =
      (request.headers['x-forwarded-for'] as string)?.split(',')[0]?.trim() ||
      request.socket?.remoteAddress ||
      'unknown';
    const userAgent = request.headers['user-agent'] || '-';

    return next.handle().pipe(
      tap(() => {
        const statusCode = httpContext.getResponse().statusCode;
        const duration = Date.now() - startTime;
        // 2xx + 3xx (incl. 304 NOT_MODIFIED) are success; failure from 400+.
        const result = statusCode >= 200 && statusCode < 400 ? 'OK' : 'FAIL';
        console.log(
          `[${new Date().toISOString()}] ${method} ${path} | ${statusCode} ${result} | ${duration}ms | ${ip} | ${userAgent}`,
        );
      }),
      catchError((err) => {
        const duration = Date.now() - startTime;
        const statusCode = err.status || err.statusCode || 500;
        console.log(
          `[${new Date().toISOString()}] ${method} ${path} | ${statusCode} ERROR | ${duration}ms | ${ip} | ${userAgent} | ${err.message || err}`,
        );
        throw err;
      }),
    );
  }
}
