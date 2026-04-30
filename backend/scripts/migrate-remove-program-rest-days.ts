/**
 * One-off data migration: remove explicit rest ProgramDays, renumber training days per week,
 * set Program.weeklySessionTarget from week 1 training count.
 *
 * Run: npm run migrate:remove-rest-days --prefix backend
 */
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

function isTrainingDay(d: {
  isRestDay: boolean;
  dayType: string;
}): boolean {
  if (d.isRestDay) return false;
  if (d.dayType === 'rest' || d.dayType === 'active_recovery') return false;
  return true;
}

async function main() {
  const programs = await prisma.program.findMany({
    where: { deletedAt: null },
    select: {
      id: true,
      weeks: {
        orderBy: [{ weekNumber: 'asc' }],
        select: {
          id: true,
          weekNumber: true,
          days: {
            orderBy: { dayNumber: 'asc' },
            select: { id: true, dayNumber: true, isRestDay: true, dayType: true },
          },
        },
      },
    },
  });

  for (const program of programs) {
    const w1 = program.weeks.find((w) => w.weekNumber === 1);
    const trainingCountW1 = w1 ? w1.days.filter((d) => isTrainingDay(d)).length : 0;

    await prisma.$transaction(async (tx) => {
      const userProgramIds = (
        await tx.userProgram.findMany({
          where: { programId: program.id },
          select: { id: true },
        })
      ).map((u) => u.id);

      for (const week of program.weeks) {
        const restDayIds = week.days.filter((d) => !isTrainingDay(d)).map((d) => d.id);
        if (restDayIds.length > 0) {
          await tx.programDay.deleteMany({ where: { id: { in: restDayIds } } });
        }

        const remaining = await tx.programDay.findMany({
          where: { weekId: week.id },
          orderBy: { dayNumber: 'asc' },
          select: { id: true, dayNumber: true },
        });

        if (remaining.length === 0) continue;

        const oldOrder = remaining.map((d) => d.dayNumber);
        for (let i = 0; i < remaining.length; i++) {
          await tx.programDay.update({
            where: { id: remaining[i]!.id },
            data: { dayNumber: 1000 + i },
          });
        }
        for (let i = 0; i < remaining.length; i++) {
          const newDn = i + 1;
          const oldDn = oldOrder[i]!;
          await tx.programDay.update({
            where: { id: remaining[i]!.id },
            data: { dayNumber: newDn, isRestDay: false },
          });
          if (oldDn !== newDn && userProgramIds.length > 0) {
            await tx.userProgramProgress.updateMany({
              where: {
                userProgramId: { in: userProgramIds },
                weekNumber: week.weekNumber,
                dayNumber: oldDn,
              },
              data: { dayNumber: newDn },
            });
            await tx.userProgramOverride.updateMany({
              where: {
                userProgramId: { in: userProgramIds },
                weekNumber: week.weekNumber,
                dayNumber: oldDn,
              },
              data: { dayNumber: newDn },
            });
          }
          if (oldDn !== newDn) {
            await tx.programSessionReport.updateMany({
              where: {
                programId: program.id,
                weekNumber: week.weekNumber,
                dayNumber: oldDn,
              },
              data: { dayNumber: newDn },
            });
          }
        }
      }

      if (trainingCountW1 > 0) {
        await tx.program.update({
          where: { id: program.id },
          data: { weeklySessionTarget: trainingCountW1 },
        });
      }
    });

    console.log(`Migrated program ${program.id}`);
  }

  console.log('Done.');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
