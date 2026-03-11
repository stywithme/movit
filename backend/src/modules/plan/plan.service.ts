import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '@/prisma/prisma.service';
import { CreatePlanDto } from './dto/create-plan.dto';
import { UpdatePlanDto } from './dto/update-plan.dto';

@Injectable()
export class PlanService {
    constructor(private readonly db: PrismaService) { }

    async create(createPlanDto: CreatePlanDto) {
        return this.db.plan.create({
            data: createPlanDto,
        });
    }

    async findAll(query: any = {}) {
        const { isActive, search, page = 1, limit = 10 } = query;
        const skip = (page - 1) * limit;

        const where: any = {};
        if (isActive !== undefined) {
            where.isActive = isActive === 'true' || isActive === true;
        }

        const [data, total] = await Promise.all([
            this.db.plan.findMany({
                where,
                skip: Number(skip),
                take: Number(limit),
                orderBy: { createdAt: 'desc' },
            }),
            this.db.plan.count({ where }),
        ]);

        return {
            data,
            meta: {
                total,
                page: Number(page),
                limit: Number(limit),
                totalPages: Math.ceil(total / limit),
            },
        };
    }

    async findOne(id: string) {
        const plan = await this.db.plan.findUnique({
            where: { id },
        });

        if (!plan) {
            throw new NotFoundException(`Plan with ID ${id} not found`);
        }

        return plan;
    }

    async update(id: string, updatePlanDto: UpdatePlanDto) {
        await this.findOne(id);

        return this.db.plan.update({
            where: { id },
            data: updatePlanDto,
        });
    }

    async remove(id: string) {
        await this.findOne(id);

        // Soft delete
        return this.db.plan.update({
            where: { id },
            data: { isActive: false },
        });
    }
}
