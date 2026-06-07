/**
 * Expand a short per-set list to cover all sets.
 * Last value repeats for any remaining sets (e.g. [10, 12] + 4 sets → [10, 12, 12, 12]).
 */
export function expandPerSetValues<T>(
  values: T[] | undefined | null,
  sets: number,
  fallback?: T
): T[] | undefined {
  const count = Math.max(sets, 1);
  if (!values || values.length === 0) {
    if (fallback !== undefined) {
      return Array.from({ length: count }, () => fallback);
    }
    return undefined;
  }
  return Array.from({ length: count }, (_, index) => values[Math.min(index, values.length - 1)]!);
}

export function parseNumberList(value: unknown): number[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const numbers = value.filter((item): item is number => typeof item === 'number' && !Number.isNaN(item));
  return numbers.length > 0 ? numbers : undefined;
}

export function sanitizePerSetNumberInput(input: string): string {
  return input.replace(/[^0-9,.]/g, '');
}

/**
 * Parse comma-separated numbers. Trailing commas with no value are ignored.
 */
export function parseCommaSeparatedNumbers(input: string): number[] | undefined {
  const trimmed = sanitizePerSetNumberInput(input).trim();
  if (!trimmed) return undefined;

  const parts = trimmed.split(',').map((part) => part.trim());
  while (parts.length > 0 && parts[parts.length - 1] === '') {
    parts.pop();
  }
  if (parts.length === 0) return undefined;

  const numbers = parts
    .map((part) => Number(part))
    .filter((value) => !Number.isNaN(value));
  return numbers.length > 0 ? numbers : undefined;
}

export function formatPerSetNumbers(values: number[] | undefined | null): string {
  if (!values || values.length === 0) return '';
  return values.join(', ');
}
