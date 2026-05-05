/**
 * Exercise Family Management Controller
 * Provides admin endpoints to list, reorder, and rename exercise families.
 * Families are the primary grouping for automatic substitutions and progression consistency.
 */

import { Controller, Get, Patch, Param, Body, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { getPrisma } from '@/lib/prisma/client';

@UseGuards(CaslGuard)
@Controller('admin/exercise-families')
export class ExerciseFamilyController {
  @Get()
  @CheckPermission('read', 'ProgressionRule')
  async listFamilies(@Res({ passthrough: true }) res: Response) {
    try {
      const prisma = await getPrisma();
      const families = await prisma.$queryRaw<
        Array<{ familyKey: string; exerciseCount: number; dominantArchetype: string | null }>
      >`
        SELECT 
          "familyKey",
          COUNT(*)::int as "exerciseCount",
          MODE() WITHIN GROUP (ORDER BY "archetype") as "dominantArchetype"
        FROM "exercises"
        WHERE "familyKey" IS NOT NULL AND "deletedAt" IS NULL
        GROUP BY "familyKey"
        ORDER BY "familyKey" ASC
      `;
      return { success: true, data: families };
    } catch (error) {
      console.error('[ExerciseFamily] List error:', error);
      res.status(500);
      return { success: false, error: 'Failed to list families' };
    }
  }

  @Get(':familyKey')
  @CheckPermission('read', 'ProgressionRule')
  async getFamilyDetails(@Param('familyKey') familyKey: string, @Res({ passthrough: true }) res: Response) {
    try {
      const prisma = await getPrisma();
      const exercises = await prisma.exercise.findMany({
        where: { familyKey, deletedAt: null },
        orderBy: { familyOrder: 'asc' },
        select: {
          id: true,
          slug: true,
          name: true,
          archetype: true,
          movementPattern: true,
          familyOrder: true,
        },
      });
      return { success: true, data: { familyKey, exercises } };
    } catch (error) {
      console.error('[ExerciseFamily] Details error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch family details' };
    }
  }

  @Patch(':familyKey/order')
  @CheckPermission('update', 'ProgressionRule')
  async reorderFamily(
    @Param('familyKey') familyKey: string,
    @Body() body: { orderedExerciseIds: string[] },
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const prisma = await getPrisma();
      const { orderedExerciseIds } = body;

      // Update familyOrder sequentially
      await prisma.$transaction(
        orderedExerciseIds.map((exerciseId, index) =>
          prisma.exercise.update({
            where: { id: exerciseId, familyKey },
            data: { familyOrder: index + 1 },
          }),
        ),
      );

      return { success: true, message: 'Family order updated' };
    } catch (error) {
      console.error('[ExerciseFamily] Reorder error:', error);
      res.status(500);
      return { success: false, error: 'Failed to reorder family' };
    }
  }

  @Patch(':familyKey/rename')
  @CheckPermission('update', 'ProgressionRule')
  async renameFamily(
    @Param('familyKey') oldKey: string,
    @Body() body: { newKey: string },
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const prisma = await getPrisma();
      const { newKey } = body;

      if (!newKey || newKey === oldKey) {
        res.status(400);
        return { success: false, error: 'Invalid new family key' };
      }

      await prisma.exercise.updateMany({
        where: { familyKey: oldKey },
        data: { familyKey: newKey },
      });

      return { success: true, message: `Family renamed from ${oldKey} to ${newKey}` };
    } catch (error) {
      console.error('[ExerciseFamily] Rename error:', error);
      res.status(500);
      return { success: false, error: 'Failed to rename family' };
    }
  }
}