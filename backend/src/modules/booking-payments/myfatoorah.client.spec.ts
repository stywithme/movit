import { updatePayment } from './myfatoorah.client';

describe('myfatoorah.client', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = {
      ...originalEnv,
      MYFATOORAH_API_KEY: 'test-token',
      MYFATOORAH_API_BASE_URL: 'https://example.test',
    };
    jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ IsSuccess: true, Data: {} }),
    } as Response);
  });

  afterEach(() => {
    jest.restoreAllMocks();
    process.env = originalEnv;
  });

  it('maps OperationType to the v2 UpdatePaymentStatus Operation field', async () => {
    await updatePayment('pay-1', { OperationType: 'CAPTURE', Amount: 50 });

    expect(global.fetch).toHaveBeenCalledWith(
      'https://example.test/v2/UpdatePaymentStatus',
      expect.objectContaining({
        body: JSON.stringify({
          Key: 'pay-1',
          KeyType: 'PaymentId',
          Operation: 'Capture',
          Amount: 50,
        }),
      }),
    );
  });
});
