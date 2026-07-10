jest.mock('@/lib/prisma/client', () => ({
  getPrisma: async () => ({
    feedbackMessageTemplate: { findMany: jest.fn(async () => []) },
    exercise: { findFirst: jest.fn(), findMany: jest.fn() },
    workoutTemplate: { findFirst: jest.fn() },
  }),
}));

jest.mock('@/lib/storage', () => ({
  getGcsBucket: jest.fn(),
  parseObjectNameFromUrl: jest.fn(() => null),
}));

import { buildMessageLibrary } from '../mobile-audio-manifest.service';

describe('mobile-audio-manifest.service', () => {
  it('buildMessageLibrary returns empty list for no exercises', () => {
    expect(buildMessageLibrary([])).toEqual([]);
  });
});
