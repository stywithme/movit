/**
 * B5: request logger treats 2xx/3xx (incl. 304) as OK.
 */

import { CallHandler, ExecutionContext } from '@nestjs/common';
import { of, throwError } from 'rxjs';
import { RequestLoggingInterceptor } from '../request-logging.interceptor';

function mockHttpContext(statusCode: number) {
  const response = { statusCode };
  const request = {
    method: 'GET',
    originalUrl: '/api/mobile/home',
    url: '/api/mobile/home',
    headers: {},
    socket: { remoteAddress: '127.0.0.1' },
  };
  return {
    switchToHttp: () => ({
      getRequest: () => request,
      getResponse: () => response,
    }),
  } as unknown as ExecutionContext;
}

describe('RequestLoggingInterceptor (B5)', () => {
  const interceptor = new RequestLoggingInterceptor();
  let logSpy: jest.SpyInstance;

  beforeEach(() => {
    logSpy = jest.spyOn(console, 'log').mockImplementation(() => undefined);
  });

  afterEach(() => {
    logSpy.mockRestore();
  });

  it('logs 304 as OK', (done) => {
    const ctx = mockHttpContext(304);
    const next: CallHandler = { handle: () => of(null) };

    interceptor.intercept(ctx, next).subscribe({
      complete: () => {
        expect(logSpy).toHaveBeenCalledWith(expect.stringContaining('304 OK'));
        done();
      },
    });
  });

  it('logs 200 as OK and 404 as FAIL', (done) => {
    const ctx200 = mockHttpContext(200);
    const next200: CallHandler = { handle: () => of(null) };

    interceptor.intercept(ctx200, next200).subscribe({
      complete: () => {
        expect(logSpy).toHaveBeenCalledWith(expect.stringContaining('200 OK'));

        const ctx404 = mockHttpContext(404);
        const next404: CallHandler = { handle: () => of(null) };
        interceptor.intercept(ctx404, next404).subscribe({
          complete: () => {
            expect(logSpy).toHaveBeenCalledWith(expect.stringContaining('404 FAIL'));
            done();
          },
        });
      },
    });
  });

  it('logs thrown errors as ERROR', (done) => {
    const ctx = mockHttpContext(200);
    const next: CallHandler = {
      handle: () => throwError(() => Object.assign(new Error('boom'), { status: 500 })),
    };

    interceptor.intercept(ctx, next).subscribe({
      error: () => {
        expect(logSpy).toHaveBeenCalledWith(expect.stringContaining('500 ERROR'));
        done();
      },
    });
  });
});
