import 'server-only';
import { PrismaClient } from '../../generated/prisma/client';

// Dynamically import PrismaPg to avoid Turbopack junction point issues on Windows
let prismaInstance: PrismaClient | null = null;

async function createPrismaClient(): Promise<PrismaClient> {
  // Check if DATABASE_URL is set
  if (!process.env.DATABASE_URL) {
    console.error('❌ DATABASE_URL is not set in environment variables');
    console.error('Please create a .env file with:');
    console.error('DATABASE_URL="postgresql://user:password@localhost:5432/dbname"');
    throw new Error('DATABASE_URL is not configured');
  }

  // Dynamic import to work around Turbopack issues
  const { PrismaPg } = await import('@prisma/adapter-pg');
  
  // Create Prisma adapter for PostgreSQL
  const adapter = new PrismaPg({
    connectionString: process.env.DATABASE_URL,
  });

  return new PrismaClient({ adapter });
}

// Singleton promise for async initialization
let prismaPromise: Promise<PrismaClient> | null = null;

export async function getPrisma(): Promise<PrismaClient> {
  if (prismaInstance) {
    return prismaInstance;
  }
  
  if (!prismaPromise) {
    prismaPromise = createPrismaClient().then(client => {
      prismaInstance = client;
      return client;
    });
  }
  
  return prismaPromise;
}

// For backward compatibility - but this will throw if not initialized
// Use getPrisma() instead for async access
export const prisma = new Proxy({} as PrismaClient, {
  get(target, prop) {
    if (!prismaInstance) {
      throw new Error('Prisma client not initialized. Use getPrisma() for async access.');
    }
    return (prismaInstance as any)[prop];
  }
});

export default prisma;
