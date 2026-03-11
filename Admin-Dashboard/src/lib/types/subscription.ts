import { Plan } from "./plan";

export interface Subscription {
    id: string;
    userId: string;
    planId: string;
    status: string; // active, cancelled, expired, etc.
    amountPaid: number;
    startDate: string;
    endDate: string;
    createdAt: string;
    updatedAt: string;

    user?: {
        id: string;
        name: string;
        email: string;
        avatarUrl?: string;
    };
    plan?: Plan;
}

export interface CreateSubscriptionDto {
    userId: string;
    planId: string;
    status?: string;
    amountPaid: number;
    startDate?: string;
    endDate: string;
}

export interface UpdateSubscriptionDto extends Partial<CreateSubscriptionDto> { }
