import path from 'node:path';
import fs from 'node:fs';
import { defineConfig } from 'prisma/config';
import dotenv from 'dotenv';

// Load environment variables from .env file
dotenv.config();

// Sync schema from backend (single source of truth)
const backendSchema = path.join(__dirname, '..', 'backend', 'prisma', 'schema.prisma');
const localSchema = path.join(__dirname, 'prisma', 'schema.prisma');

if (fs.existsSync(backendSchema)) {
  fs.copyFileSync(backendSchema, localSchema);
}

export default defineConfig({
  schema: localSchema,
  datasource: {
    url: process.env.DATABASE_URL!,
  },
});

