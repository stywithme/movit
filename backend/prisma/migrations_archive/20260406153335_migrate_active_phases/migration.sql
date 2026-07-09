-- Migrate activePhases values: start->top, hold->all, count->all, idle->all
-- Also deduplicate after mapping (e.g. both "hold" and "count" map to "all")
UPDATE position_checks
SET "activePhases" = (
  SELECT jsonb_agg(DISTINCT mapped)
  FROM (
    SELECT
      CASE elem
        WHEN 'start' THEN 'top'
        WHEN 'hold' THEN 'all'
        WHEN 'count' THEN 'all'
        WHEN 'idle' THEN 'all'
        ELSE elem
      END AS mapped
    FROM jsonb_array_elements_text("activePhases") AS elem
  ) sub
)
WHERE "activePhases"::text ~ '"(start|hold|count|idle)"';
