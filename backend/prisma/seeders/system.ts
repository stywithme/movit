import type { PrismaClient } from '@prisma/client';

export async function seedSystemConfig(prisma: PrismaClient) {
    // We specify the super_admin_email or ID
    const superAdminEmail = (process.env.ADMIN_SEED_EMAIL || 'alustadh.manager@gmail.com').toLowerCase();

    const systemConfig = [
        { key: 'super_admin_email', value: superAdminEmail },
        { key: 'currency', value: 'SAR' },
    ];

    console.log(`🌱 Seeding System settings...`);

    for (const item of systemConfig) {
        await prisma.system.upsert({
            where: { key: item.key },
            update: { value: item.value },
            create: item,
        });
    }

    console.log(`✅ System settings seeded.`);
}
