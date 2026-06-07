/** Expand a short per-set list; last value repeats for remaining sets. */
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

/** Allow only digits, comma, and decimal point in per-set list inputs. */
export function sanitizePerSetNumberInput(input: string): string {
  return input.replace(/[^0-9,.]/g, '');
}

/**
 * Parse comma-separated numbers. Trailing commas with no value are ignored
 * (e.g. "30, 55," → [30, 55], not [30, 55, 0]).
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

/** Seconds in UI → milliseconds for storage. */
export function parsePerSetSecondsInput(input: string): number[] | undefined {
  const seconds = parseCommaSeparatedNumbers(input);
  if (!seconds) return undefined;
  return seconds.map((value) => Math.max(0, Math.round(value * 1000)));
}

export function formatPerSetSecondsFromMs(values: number[] | undefined | null): string {
  if (!values || values.length === 0) return '';
  return values.map((ms) => String(Math.round(ms / 1000))).join(', ');
}
