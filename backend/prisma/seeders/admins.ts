import type { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';

export async function seedAdmins(prisma: PrismaClient) {
  const adminEmail = (process.env.ADMIN_SEED_EMAIL || 'alustadh.manager@gmail.com').toLowerCase();
  const adminName = process.env.ADMIN_SEED_NAME || 'Super Admin';
  const adminPassword = process.env.ADMIN_SEED_PASSWORD || 'password';
  const hashedPassword = await bcrypt.hash(adminPassword, 12);

  await prisma.admin.upsert({
    where: { email: adminEmail },
    update: {
      name: adminName,
      isSuperAdmin: true,
      isActive: true,
      deletedAt: null,
      ...(process.env.ADMIN_SEED_PASSWORD ? { password: hashedPassword } : {}),
    },
    create: {
      name: adminName,
      email: adminEmail,
      password: hashedPassword,
      isSuperAdmin: true,
      isActive: true,
    },
  });

  console.log(`✅ Super admin seeded: ${adminEmail}`);
}
