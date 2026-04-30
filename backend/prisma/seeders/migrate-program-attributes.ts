import type { PrismaClient } from '@prisma/client';
import { ProgramAttributeMode } from '@prisma/client';
import {
  PROGRAM_DOMAIN_VALUE_CODE,
  TRAINING_GOAL_VALUE_CODE,
  equipmentValueCodeFromProfileString,
  bodyRegionValueCodeFromLabel,
  focusValueCodeFromTargetHint,
} from '../../src/lib/program-attribute-codes';

/**
 * For programs with no ProgramAttribute rows yet, copy legacy scalar fields into ProgramAttribute.
 * Idempotent per program (skips if any programAttribute exists).
 */
export async function migrateProgramAttributesFromLegacy(prisma: PrismaClient): Promise<{ programsUpdated: number }> {
  const programs = await prisma.program.findMany({
    where: { deletedAt: null },
    include: { programAttributes: { take: 1 } },
  });

  let programsUpdated = 0;

  for (const p of programs) {
    if (p.programAttributes.length > 0) continue;

    const rows: { attributeValueId: string; mode: ProgramAttributeMode }[] = [];

    const domainCode = PROGRAM_DOMAIN_VALUE_CODE[p.programDomain];
    const domainVal = await prisma.attributeValue.findUnique({ where: { code: domainCode } });
    if (domainVal) {
      rows.push({ attributeValueId: domainVal.id, mode: ProgramAttributeMode.REQUIRED });
    }

    if (p.trainingGoal) {
      const goalCode = TRAINING_GOAL_VALUE_CODE[p.trainingGoal];
      const goalVal = await prisma.attributeValue.findUnique({ where: { code: goalCode } });
      if (goalVal) {
        rows.push({ attributeValueId: goalVal.id, mode: ProgramAttributeMode.REQUIRED });
      }
    }

    const te = p.targetEquipment;
    if (Array.isArray(te)) {
      for (const raw of te) {
        const code = typeof raw === 'string' ? equipmentValueCodeFromProfileString(raw) : null;
        if (!code) continue;
        const ev = await prisma.attributeValue.findUnique({ where: { code } });
        if (ev?.attributeId) {
          const attr = await prisma.attribute.findUnique({ where: { id: ev.attributeId } });
          if (attr?.code === 'equipment') {
            rows.push({ attributeValueId: ev.id, mode: ProgramAttributeMode.REQUIRED });
          }
        }
      }
    }

    for (const region of p.targetRegions || []) {
      const br = bodyRegionValueCodeFromLabel(region);
      if (!br) continue;
      const v = await prisma.attributeValue.findUnique({ where: { code: br } });
      if (v) rows.push({ attributeValueId: v.id, mode: ProgramAttributeMode.REQUIRED });
    }

    for (const region of p.contraindications || []) {
      const br = bodyRegionValueCodeFromLabel(region);
      if (!br) continue;
      const v = await prisma.attributeValue.findUnique({ where: { code: br } });
      if (v) rows.push({ attributeValueId: v.id, mode: ProgramAttributeMode.EXCLUDED });
    }

    if (p.targetDomain) {
      const fc = focusValueCodeFromTargetHint(p.targetDomain);
      if (fc) {
        const v = await prisma.attributeValue.findUnique({ where: { code: fc } });
        if (v) rows.push({ attributeValueId: v.id, mode: ProgramAttributeMode.REQUIRED });
      }
    }

    if (rows.length === 0) continue;

    await prisma.programAttribute.createMany({
      data: rows.map((r) => ({
        programId: p.id,
        attributeValueId: r.attributeValueId,
        mode: r.mode,
      })),
      skipDuplicates: true,
    });
    programsUpdated += 1;
  }

  return { programsUpdated };
}
