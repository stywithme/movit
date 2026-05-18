import type { Prisma } from '@prisma/client';

const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const INSENSITIVE = { mode: 'insensitive' as const };

function slugVariants(term: string): string[] {
  const variants = new Set<string>([term]);
  variants.add(term.replace(/\s+/g, '_'));
  variants.add(term.replace(/\s+/g, '-'));
  variants.add(term.replace(/[_\s-]+/g, '_'));
  variants.add(term.replace(/[_\s-]+/g, '-'));
  return [...variants].filter((v) => v.length > 0);
}

function localizedJsonContains(
  field: string,
  path: 'en' | 'ar',
  value: string,
): Record<string, unknown> {
  return {
    [field]: {
      path: [path],
      string_contains: value,
      ...INSENSITIVE,
    },
  };
}

function attributeValueTextOr(token: string): Prisma.AttributeValueWhereInput[] {
  return [
    { code: { contains: token, ...INSENSITIVE } },
    localizedJsonContains('name', 'en', token) as Prisma.AttributeValueWhereInput,
    localizedJsonContains('name', 'ar', token) as Prisma.AttributeValueWhereInput,
  ];
}

function orConditionsForToken(token: string): Prisma.ExerciseWhereInput[] {
  const conditions: Prisma.ExerciseWhereInput[] = [
    localizedJsonContains('name', 'en', token) as Prisma.ExerciseWhereInput,
    localizedJsonContains('name', 'ar', token) as Prisma.ExerciseWhereInput,
    localizedJsonContains('description', 'en', token) as Prisma.ExerciseWhereInput,
    localizedJsonContains('description', 'ar', token) as Prisma.ExerciseWhereInput,
    localizedJsonContains('instructions', 'en', token) as Prisma.ExerciseWhereInput,
    localizedJsonContains('instructions', 'ar', token) as Prisma.ExerciseWhereInput,
    { slug: { contains: token, ...INSENSITIVE } },
    { familyKey: { contains: token, ...INSENSITIVE } },
    {
      category: {
        OR: attributeValueTextOr(token),
      },
    },
    {
      attributes: {
        some: {
          attributeValue: {
            OR: attributeValueTextOr(token),
          },
        },
      },
    },
  ];

  for (const variant of slugVariants(token)) {
    if (variant !== token) {
      conditions.push({ slug: { contains: variant, ...INSENSITIVE } });
    }
  }

  if (UUID_REGEX.test(token)) {
    conditions.push({ id: token });
  }

  return conditions;
}

/**
 * Builds a Prisma where clause for exercise text search (case-insensitive).
 * Multi-word queries require every token to match at least one field.
 */
export function buildExerciseSearchWhere(search?: string): Prisma.ExerciseWhereInput | undefined {
  const term = search?.trim();
  if (!term) return undefined;

  const tokens = term.split(/\s+/).filter(Boolean);
  if (tokens.length === 0) return undefined;

  if (tokens.length === 1) {
    return { OR: orConditionsForToken(tokens[0]) };
  }

  return {
    AND: tokens.map((token) => ({
      OR: orConditionsForToken(token),
    })),
  };
}
