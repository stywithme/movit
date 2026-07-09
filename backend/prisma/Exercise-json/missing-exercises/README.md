# Missing Exercises JSON

Generated from `New-Exercises/` spreadsheet CSVs (linked by `Exercise_ID`).

This folder is separate from `Exercise-json/exercises-from-db` so production can import only this group without a full reseed.

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

On a fresh production database, prefer the unified pipeline:

```bash
npm run seed:base
npm run seed:full
```

`seed:full` already imports both canonical and missing-exercises directories.

Do **not** use `npm run seed:reset:full` on production — it clears data first.
