import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '@/prisma/prisma.service';
import { CreateSubscriptionDto } from './dto/create-subscription.dto';
import { UpdateSubscriptionDto } from './dto/update-subscription.dto';

@Injectable()
export class SubscriptionService {
    constructor(private readonly db: PrismaService) { }

    async create(createSubscriptionDto: CreateSubscriptionDto) {
        return this.db.subscription.create({
            data: {
                ...createSubscriptionDto,
                amountPaid: Number(createSubscriptionDto.amountPaid),
            },
            include: {
                user: true,
                plan: true,
            }
        });
    }

    async findAll(query: any = {}) {
        const { status, search, page = 1, limit = 10 } = query;
        const skip = (page - 1) * limit;

        const where: any = {};
        if (status) {
            where.status = status;
        }

        if (search) {
            where.OR = [
                { user: { name: { contains: search, mode: 'insensitive' } } },
                { user: { email: { contains: search, mode: 'insensitive' } } },
            ];
        }

        const [data, total] = await Promise.all([
            this.db.subscription.findMany({
                where,
                skip: Number(skip),
                take: Number(limit),
                orderBy: { createdAt: 'desc' },
                include: {
                    user: {
                        select: { id: true, name: true, email: true, avatarUrl: true }
                    },
                    plan: true,
                }
            }),
            this.db.subscription.count({ where }),
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
        const subscription = await this.db.subscription.findUnique({
            where: { id },
            include: {
                user: {
                    select: { id: true, name: true, email: true, avatarUrl: true }
                },
                plan: true,
            }
        });

        if (!subscription) {
            throw new NotFoundException(`Subscription with ID ${id} not found`);
        }

        return subscription;
    }

    async update(id: string, updateSubscriptionDto: UpdateSubscriptionDto) {
        await this.findOne(id);

        return this.db.subscription.update({
            where: { id },
            data: {
                ...updateSubscriptionDto,
                ...(updateSubscriptionDto.amountPaid ? { amountPaid: Number(updateSubscriptionDto.amountPaid) } : {}),
            },
            include: {
                user: {
                    select: { id: true, name: true, email: true, avatarUrl: true }
                },
                plan: true,
            }
        });
    }

    async remove(id: string) {
        await this.findOne(id);

        // Soft delete or status change
        return this.db.subscription.update({
            where: { id },
            data: { status: 'cancelled' },
        });
    }
}
