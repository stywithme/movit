import { Injectable, NotFoundException } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';

@Injectable()
export class SystemService {
    async getAll() {
        const prisma = await getPrisma();
        return prisma.system.findMany({
            orderBy: { key: 'asc' },
        });
    }

    async getByKey(key: string) {
        const prisma = await getPrisma();
        const setting = await prisma.system.findUnique({ where: { key } });
        if (!setting) throw new NotFoundException(`Setting ${key} not found`);
        return setting;
    }

    async update(key: string, value: string) {
        const prisma = await getPrisma();
        return prisma.system.upsert({
            where: { key },
            update: { value },
            create: { key, value },
        });
    }

    async updateMany(settings: Record<string, string>) {
        const prisma = await getPrisma();
        const promises = Object.entries(settings).map(([key, value]) =>
            prisma.system.upsert({
                where: { key },
                update: { value },
                create: { key, value },
            })
        );
        return Promise.all(promises);
    }
}
