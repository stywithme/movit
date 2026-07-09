# Archived Prisma migrations

These folders are the **pre-squash** migration history (51 incremental migrations, Feb–Jun 2026).

Production uses a single baseline instead:

- `prisma/migrations/20260622140000_baseline_production_v1/migration.sql`

To regenerate the baseline from `schema.prisma`:

```bash
DATABASE_URL=postgresql://... npm run migrate:squash-baseline
```

Do not apply archived migrations on a fresh database.
