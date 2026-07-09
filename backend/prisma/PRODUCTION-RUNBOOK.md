# Production database bootstrap

Fresh production database setup:

```bash
cd backend
cp .env.example .env   # configure DATABASE_URL, ADMIN_SEED_*, JWT secrets, etc.
npm run prisma:generate
npm run prisma:migrate:deploy
npm run seed:base
npm run seed:full
```

## Migrations

- Single baseline: `prisma/migrations/20260622140000_baseline_production_v1/`
- Historical migrations archived under `prisma/migrations_archive/`
- Regenerate baseline from current schema: `npm run migrate:squash-baseline` (requires `DATABASE_URL`)

## Seeding rules

1. Never run `prisma:seed` / `seed:reset:full` on production — it wipes data.
2. Use `seed:base` then `seed:full` on empty DB after migrate deploy.
3. Add new exercises incrementally with `seed:missing-exercises` (partial, no clear).
4. Exercise JSON must match the mobile contract (`android-schema.ts`).
