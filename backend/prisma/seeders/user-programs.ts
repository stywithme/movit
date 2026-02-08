import type { PrismaClient } from '@prisma/client';

export async function seedUserPrograms(prisma: PrismaClient) {
  const safeFindProgram = async () => {
    try {
      return await prisma.program.findUnique({ where: { slug: 'starter-4-weeks' } });
    } catch (error: any) {
      if (error?.code === 'P2021') {
        console.warn('⚠️ Programs table missing. Skipping user program seed.');
        return null;
      }
      throw error;
    }
  };

  const user = await prisma.user.findUnique({ where: { email: 'alustadh.manager@gmail.com' } });
  const program = await safeFindProgram();

  if (!user || !program) {
    console.warn('⚠️ Missing user or program for user program seed.');
    return;
  }

  const existingUserProgram = await prisma.userProgram.findFirst({
    where: { userId: user.id, programId: program.id },
  });

  const userProgram = existingUserProgram
    ? await prisma.userProgram.update({
        where: { id: existingUserProgram.id },
        data: { isActive: true, startDate: new Date() },
      })
    : await prisma.userProgram.create({
        data: {
          userId: user.id,
          programId: program.id,
          name: program.name as object,
          isActive: true,
          startDate: new Date(),
        },
      });

  await prisma.userProgramProgress.deleteMany({
    where: { userProgramId: userProgram.id },
  });

  const sessions = await prisma.programSession.findMany({
    where: { day: { week: { programId: program.id } } },
    include: { day: { include: { week: true } } },
    orderBy: { sortOrder: 'asc' },
  });

  const progressData = sessions.slice(0, 3).map((session, index) => ({
    userProgramId: userProgram.id,
    weekNumber: session.day.week.weekNumber,
    dayNumber: session.day.dayNumber,
    sessionId: session.id,
    completedAt: index < 2 ? new Date(Date.now() - (2 - index) * 86400000) : null,
    status: index < 2 ? 'completed' : 'in_progress',
  }));

  if (progressData.length > 0) {
    await prisma.userProgramProgress.createMany({
      data: progressData,
      skipDuplicates: true,
    });
  }

  const firstSession = sessions[0];
  if (firstSession) {
    await prisma.programSessionReport.deleteMany({
      where: { userId: user.id, programSessionId: firstSession.id },
    });

    await prisma.programSessionReport.create({
      data: {
        userId: user.id,
        programId: program.id,
        programSessionId: firstSession.id,
        weekNumber: firstSession.day.week.weekNumber,
        dayNumber: firstSession.day.dayNumber,
        startedAt: new Date(Date.now() - 3600000),
        completedAt: new Date(Date.now() - 1800000),
        status: 'completed',
        totalDurationMs: 1800000,
        totalExercises: 3,
        totalSets: 8,
        completedSets: 8,
        totalReps: 72,
        avgAccuracy: 87.5,
        report: {
          summary: { accuracy: 87.5, reps: 72 },
          notes: ['solid form', 'keep breathing steady'],
        },
      },
    });
  }

  console.log('✅ User programs seeded');
}
