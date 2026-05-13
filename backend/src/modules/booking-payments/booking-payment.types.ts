import { z } from 'zod';

export const createCheckoutSchema = z.object({
  bookingIds: z
    .array(z.string().uuid())
    .min(1)
    .max(20)
    .refine((ids) => new Set(ids).size === ids.length, {
      message: 'bookingIds must be unique',
    }),
  idempotencyKey: z.string().min(1).max(128).optional(),
});

export type CreateCheckoutInput = z.infer<typeof createCheckoutSchema>;
