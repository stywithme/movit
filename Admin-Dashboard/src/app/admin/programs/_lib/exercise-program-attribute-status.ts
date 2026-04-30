import type { ProgramAttributeFormRow } from './program-prescription-attributes';

export type ExerciseProgramAttrStatus = 'ok' | 'yellow' | 'red';

/**
 * Compare exercise attribute value IDs to program ProgramAttribute rows.
 * RED: exercise uses a value explicitly EXCLUDED on the program.
 * YELLOW: equipment (or place) REQUIRED on program does not overlap exercise tags.
 */
export function exerciseProgramAttributeStatus(
  exerciseValueIds: string[],
  programRows: ProgramAttributeFormRow[],
  idToMeta: Map<string, { valueCode: string; attributeCode: string }>,
): { status: ExerciseProgramAttrStatus; messages: string[] } {
  const programByValueId = new Map(programRows.map((row) => [row.attributeValueId, row.mode]));
  const messages: string[] = [];
  let status: ExerciseProgramAttrStatus = 'ok';

  for (const vid of exerciseValueIds) {
    const mode = programByValueId.get(vid);
    if (mode === 'EXCLUDED') {
      status = 'red';
      const code = idToMeta.get(vid)?.valueCode ?? vid;
      messages.push(`Exercise uses ${code}, which is excluded for this program`);
    }
  }

  const requiredByType = new Map<string, Set<string>>();
  for (const row of programRows) {
    if (row.mode !== 'REQUIRED') continue;
    const meta = idToMeta.get(row.attributeValueId);
    if (!meta) continue;
    if (!requiredByType.has(meta.attributeCode)) {
      requiredByType.set(meta.attributeCode, new Set());
    }
    requiredByType.get(meta.attributeCode)!.add(row.attributeValueId);
  }

  const bumpYellow = (msg: string) => {
    if (status === 'red') return;
    status = 'yellow';
    messages.push(msg);
  };

  const reqEquip = requiredByType.get('equipment');
  if (reqEquip && reqEquip.size > 0) {
    const exEquipIds = exerciseValueIds.filter((id) => idToMeta.get(id)?.attributeCode === 'equipment');
    const overlaps = exEquipIds.some((id) => reqEquip.has(id));
    if (exEquipIds.length > 0 && !overlaps) {
      bumpYellow('Exercise equipment does not match required program equipment');
    }
  }

  const reqPlace = requiredByType.get('place');
  if (reqPlace && reqPlace.size > 0) {
    const exPlaceIds = exerciseValueIds.filter((id) => idToMeta.get(id)?.attributeCode === 'place');
    const overlaps = exPlaceIds.some((id) => reqPlace.has(id));
    if (exPlaceIds.length > 0 && !overlaps) {
      bumpYellow('Exercise training place does not match program requirements');
    }
  }

  return { status, messages };
}
