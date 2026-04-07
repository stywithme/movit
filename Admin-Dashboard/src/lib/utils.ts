import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge Tailwind CSS classes with proper precedence
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Dense list of pose position UUIDs for API payloads.
 * Sparse arrays (holes) make Array.prototype.map skip indices → JSON becomes [null,null]
 * and Nest Zod rejects poseVariants. Array.from densifies holes to undefined, then we filter.
 */
export function normalizeCameraPositionIds(ids: unknown): string[] {
  if (!Array.isArray(ids)) return [];
  return Array.from(ids, (id) => id).filter(
    (id): id is string => typeof id === 'string' && id.length > 0
  );
}
