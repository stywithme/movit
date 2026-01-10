import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from '../../generated/prisma';

/**
 * Prisma Client Singleton
 * =======================
 * 
 * Ensures a single Prisma Client instance is used across the app.
 * Uses PrismaPg adapter for Prisma 7+.
 */

declare global {
  // eslint-disable-next-line no-var
  var prisma: PrismaClient | undefined;
}

function createPrismaClient(): PrismaClient {
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL is not configured');
  }
  
  const adapter = new PrismaPg({ connectionString: process.env.DATABASE_URL });
  
  return new PrismaClient({
    adapter,
    log: process.env.NODE_ENV === 'development' ? ['error', 'warn'] : ['error'],
  });
}

// Use global variable in development to prevent hot-reload issues
export const prisma = globalThis.prisma ?? createPrismaClient();

if (process.env.NODE_ENV !== 'production') {
  globalThis.prisma = prisma;
}

// Async getter for compatibility
export async function getPrisma(): Promise<PrismaClient> {
  return prisma;
}

export default prisma;
