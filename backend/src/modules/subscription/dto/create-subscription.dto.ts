export class CreateSubscriptionDto {
    userId: string;
    planId: string;
    status?: string;
    amountPaid: number;
    startDate?: string;
    endDate: string;
}
