import type { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';

export async function seedUsers(prisma: PrismaClient) {
  const userPassword = process.env.USER_SEED_PASSWORD || 'password';
  const hashedPassword = await bcrypt.hash(userPassword, 12);

  const users = [
    {
      email: 'alustadh.manager@gmail.com',
      name: 'Demo User',
      preferredLanguage: 'en',
    },
    {
      email: 'arabic@pose.app',
      name: 'مستخدم عربي',
      preferredLanguage: 'ar',
    },
  ];

  for (const user of users) {
    await prisma.user.upsert({
      where: { email: user.email },
      update: {
        name: user.name,
        preferredLanguage: user.preferredLanguage,
        isActive: true,
        deletedAt: null,
        ...(process.env.USER_SEED_PASSWORD ? { password: hashedPassword } : {}),
      },
      create: {
        email: user.email,
        name: user.name,
        password: hashedPassword,
        preferredLanguage: user.preferredLanguage,
        provider: 'email',
        isActive: true,
        emailVerified: true,
      },
    });
  }

  console.log('✅ Users seeded');
}
