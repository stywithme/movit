# Missing Exercises JSON

Generated from `New-Exercises/` spreadsheet CSVs (linked by `Exercise_ID`).

This folder is separate from `Exercise-json/exercises-from-db` so production can import only this group without running the full database seed.

**25 exercises** (6 skipped because they already exist in the canonical library: EX011, EX014, EX015, EX016, EX017, EX018).

## Regenerate JSON from CSV

```bash
npm run build:missing-exercises-json
```

Source CSVs default to `../New-Exercises/`. Use `--include-existing` to also emit rows that already exist in the canonical library.

## Seed commands (safe partial seed — does not clear the database)

```bash
npm run seed:missing-exercises:dry
npm run seed:missing-exercises
```

Do **not** use `npm run prisma:seed` on production for this group — it clears data first.
