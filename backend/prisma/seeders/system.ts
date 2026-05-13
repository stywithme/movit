import type { PrismaClient } from '@prisma/client';

export async function seedSystemConfig(prisma: PrismaClient) {
    // We specify the super_admin_email or ID
    const superAdminEmail = (process.env.ADMIN_SEED_EMAIL || 'alustadh.manager@gmail.com').toLowerCase();

    const systemConfig = [
        { key: 'super_admin_email', value: superAdminEmail },
        { key: 'allow_booking', value: 'true' },
        { key: 'booking_duration', value: '30' },
        { key: 'booking_price', value: '500' },
        { key: 'currency', value: 'SAR' },
        { key: 'booking_currency', value: 'SAR' },
        { key: 'reschedule_allowed_time', value: '24' },
        { key: 'max_advance_booking_days', value: '30' },
        { key: 'min_booking_hours', value: '2' },
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
