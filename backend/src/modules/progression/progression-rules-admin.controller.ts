/**
 * Progression Rules Admin Controller
 * ====================================
 *
 *   GET    /admin/progression-rules          — List all rules
 *   POST   /admin/progression-rules          — Create new rule
 *   GET    /admin/progression-rules/:id      — Get rule detail with history stats
 *   PUT    /admin/progression-rules/:id      — Update rule
 *   DELETE /admin/progression-rules/:id      — Delete rule (or deactivate if has history)
 *   PUT    /admin/progression-rules/:id/toggle — Toggle isActive
 *
 * All endpoints are protected by admin cookie-based auth.
 */

import { Controller, Get, Post, Put, Delete, Req, Res, Param, Body, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { getPrisma } from '@/lib/prisma/client';

interface CreateRuleBody {
  name: string;
  scope: string;
  programId?: string;
  exerciseId?: string;
  trigger: string;
  conditions: unknown;
  action: unknown;
  priority: number;
  isActive: boolean;
}

@UseGuards(CaslGuard)
@Controller('admin/progression-rules')
export class ProgressionRulesAdminController {
  @Get()
  @CheckPermission('read', 'ProgressionRule')
  async listRules(@Req() req: Request, @Res({ passthrough: true }) res: Response) {

    try {
      const prisma = await getPrisma();

      const rules = await prisma.progressionRule.findMany({
        include: {
          program: { select: { name: true } },
          _count: { select: { history: true } },
        },
        orderBy: [{ priority: 'desc' }, { createdAt: 'desc' }],
      });

      const data = rules.map((rule) => ({
        id: rule.id,
        name: rule.name,
        scope: rule.scope,
        programId: rule.programId,
        programName: rule.program?.name ?? null,
        exerciseId: rule.exerciseId,
        trigger: rule.trigger,
        conditions: rule.conditions,
        action: rule.action,
        priority: rule.priority,
        isActive: rule.isActive,
        historyCount: rule._count.history,
        createdAt: rule.createdAt.toISOString(),
        updatedAt: rule.updatedAt.toISOString(),
      }));

      return { success: true, data };
    } catch (error) {
      console.error('[ProgressionRulesAdmin] List Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch progression rules' };
    }
  }

  @Post()
  @CheckPermission('create', 'ProgressionRule')
  async createRule(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Body() body: CreateRuleBody,
  ) {

    try {
      const prisma = await getPrisma();

      const rule = await prisma.progressionRule.create({
        data: {
          name: body.name,
          scope: body.scope,
          programId: body.programId ?? null,
          exerciseId: body.exerciseId ?? null,
          trigger: body.trigger,
          conditions: body.conditions as any,
          action: body.action as any,
          priority: body.priority ?? 0,
          isActive: body.isActive ?? true,
        },
      });

      return { success: true, data: rule };
    } catch (error) {
      console.error('[ProgressionRulesAdmin] Create Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to create progression rule' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'ProgressionRule')
  async getRule(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('id') id: string,
  ) {

    try {
      const prisma = await getPrisma();

      const rule = await prisma.progressionRule.findUnique({
        where: { id },
        include: {
          program: { select: { name: true } },
          _count: { select: { history: true } },
          history: {
            select: { field: true, previousValue: true, newValue: true, appliedAt: true },
            orderBy: { appliedAt: 'desc' },
            take: 20,
          },
        },
      });

      if (!rule) {
        res.status(404);
        return { success: false, error: 'Rule not found' };
      }

      const avgDelta = rule.history.length > 0
        ? rule.history.reduce((sum, h) => sum + (h.newValue - h.previousValue), 0) / rule.history.length
        : 0;

      const data = {
        id: rule.id,
        name: rule.name,
        scope: rule.scope,
        programId: rule.programId,
        programName: rule.program?.name ?? null,
        exerciseId: rule.exerciseId,
        trigger: rule.trigger,
        conditions: rule.conditions,
        action: rule.action,
        priority: rule.priority,
        isActive: rule.isActive,
        historyCount: rule._count.history,
        avgDelta: Math.round(avgDelta * 100) / 100,
        recentHistory: rule.history.map((h) => ({
          field: h.field,
          previousValue: h.previousValue,
          newValue: h.newValue,
          appliedAt: h.appliedAt.toISOString(),
        })),
        createdAt: rule.createdAt.toISOString(),
        updatedAt: rule.updatedAt.toISOString(),
      };

      return { success: true, data };
    } catch (error) {
      console.error('[ProgressionRulesAdmin] Get Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch progression rule' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'ProgressionRule')
  async updateRule(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('id') id: string,
    @Body() body: Partial<CreateRuleBody>,
  ) {
    try {
      const prisma = await getPrisma();

      const existing = await prisma.progressionRule.findUnique({ where: { id } });
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Rule not found' };
      }

      const rule = await prisma.progressionRule.update({
        where: { id },
        data: {
          ...(body.name !== undefined && { name: body.name }),
          ...(body.scope !== undefined && { scope: body.scope }),
          ...(body.programId !== undefined && { programId: body.programId ?? null }),
          ...(body.exerciseId !== undefined && { exerciseId: body.exerciseId ?? null }),
          ...(body.trigger !== undefined && { trigger: body.trigger }),
          ...(body.conditions !== undefined && { conditions: body.conditions as any }),
          ...(body.action !== undefined && { action: body.action as any }),
          ...(body.priority !== undefined && { priority: body.priority }),
          ...(body.isActive !== undefined && { isActive: body.isActive }),
        },
      });

      return { success: true, data: rule };
    } catch (error) {
      console.error('[ProgressionRulesAdmin] Update Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to update progression rule' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'ProgressionRule')
  async deleteRule(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('id') id: string,
  ) {
    try {
      const prisma = await getPrisma();

      const rule = await prisma.progressionRule.findUnique({
        where: { id },
        include: { _count: { select: { history: true } } },
      });

      if (!rule) {
        res.status(404);
        return { success: false, error: 'Rule not found' };
      }

      // If the rule has history, deactivate instead of deleting
      if (rule._count.history > 0) {
        const deactivated = await prisma.progressionRule.update({
          where: { id },
          data: { isActive: false },
        });
        return {
          success: true,
          data: deactivated,
          message: 'Rule has history — deactivated instead of deleted',
        };
      }

      await prisma.progressionRule.delete({ where: { id } });
      return { success: true, data: { id } };
    } catch (error) {
      console.error('[ProgressionRulesAdmin] Delete Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete progression rule' };
    }
  }

  @Put(':id/toggle')
  @CheckPermission('update', 'ProgressionRule')
  async toggleRule(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('id') id: string,
  ) {
    try {
      const prisma = await getPrisma();

      const existing = await prisma.progressionRule.findUnique({ where: { id } });
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Rule not found' };
      }

      const rule = await prisma.progressionRule.update({
        where: { id },
        data: { isActive: !existing.isActive },
      });

      return { success: true, data: rule };
    } catch (error) {
      console.error('[ProgressionRulesAdmin] Toggle Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to toggle progression rule' };
    }
  }
}
