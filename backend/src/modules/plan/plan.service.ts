import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '@/prisma/prisma.service';
import { CreatePlanDto } from './dto/create-plan.dto';
import { UpdatePlanDto } from './dto/update-plan.dto';

@Injectable()
export class PlanService {
    constructor(private readonly db: PrismaService) { }

    private serializePlan(plan: any) {
        if (!plan) return plan;
        return {
            ...plan,
            monthlyPrice: Number(plan.monthlyPrice),
            yearlyPrice: Number(plan.yearlyPrice),
            discount: plan.discount == null ? 0 : Number(plan.discount),
        };
    }

    async create(createPlanDto: CreatePlanDto) {
        const db = this.db as any;
        const plan = await db.plan.create({
            data: createPlanDto,
        });
        return this.serializePlan(plan);
    }

    async findAll(query: any = {}) {
        const { isActive, search, page = 1, limit = 10 } = query;
        const skip = (page - 1) * limit;

        const where: any = {};
        if (isActive !== undefined) {
            where.isActive = isActive === 'true' || isActive === true;
        }

        if (search) {
            where.OR = [
                { name: { path: ['en'], string_contains: search, mode: 'insensitive' } },
                { name: { path: ['ar'], string_contains: search, mode: 'insensitive' } },
            ];
        }

        const db = this.db as any;
        const [data, total] = await Promise.all([
            db.plan.findMany({
                where,
                skip: Number(skip),
                take: Number(limit),
                orderBy: { createdAt: 'desc' },
            }),
            db.plan.count({ where }),
        ]);

        return {
            data: data.map((plan: any) => this.serializePlan(plan)),
            meta: {
                total,
                page: Number(page),
                limit: Number(limit),
                totalPages: Math.ceil(total / limit),
            },
        };
    }

    async findOne(id: string) {
        const db = this.db as any;
        const plan = await db.plan.findUnique({
            where: { id },
        });

        if (!plan) {
            throw new NotFoundException(`Plan with ID ${id} not found`);
        }

        return this.serializePlan(plan);
    }

    async update(id: string, updatePlanDto: UpdatePlanDto) {
        await this.findOne(id);

        const db = this.db as any;
        const plan = await db.plan.update({
            where: { id },
            data: updatePlanDto,
        });
        return this.serializePlan(plan);
    }

    async remove(id: string) {
        await this.findOne(id);

        // Soft delete
        const db = this.db as any;
        const plan = await db.plan.update({
            where: { id },
            data: { isActive: false },
        });
        return this.serializePlan(plan);
    }
}
