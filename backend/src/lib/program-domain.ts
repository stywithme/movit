import type { ProgramDomain } from '@prisma/client';

/** Lowercase domain string on program payloads (`type`). */
export type ProgramTypeApiString = 'training' | 'mobility' | 'therapeutic';

export function typeStringFromProgramDomain(domain: ProgramDomain): ProgramTypeApiString {
  switch (domain) {
    case 'MOBILITY':
      return 'mobility';
    case 'THERAPEUTIC':
      return 'therapeutic';
    default:
      return 'training';
  }
}
