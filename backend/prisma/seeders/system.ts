import type { PrismaClient } from '@prisma/client';

export async function seedSystemConfig(prisma: PrismaClient) {
    // We specify the super_admin_email or ID
    const superAdminEmail = (process.env.ADMIN_SEED_EMAIL || 'alustadh.manager@gmail.com').toLowerCase();

    // For SystemConfig, we just need to ensure the key exists.
    // The actual super admin will be created in the admins seeder, 
    // and we'll use email as the identifier or just hardcode the logic 
    // to always treat the first seeded admin as the god-mode admin if needed.
    // However, since we added `isSuperAdmin` flag to the Admin table directly,
    // we primarily rely on `isSuperAdmin: true`. But we'll seed this config
    // just in case we need to reference the super_admin's email/id globally.

    await prisma.systemConfig.upsert({
        where: { key: 'super_admin_email' },
        update: { value: superAdminEmail },
        create: {
            key: 'super_admin_email',
            value: superAdminEmail,
        },
    });

    console.log(`✅ SystemConfig seeded for super admin.`);
}
