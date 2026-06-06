export class CreatePlanDto {
    name: any;
    description?: any;
    monthlyPrice: number;
    yearlyPrice: number;
    currency?: string;
    discount?: number;
    maxExercisesLimit?: number;
    maxWorkoutTemplatesLimit?: number;
    freeDoctorSessionsLimit?: number;
    monthlyGooglePlayProductId?: string;
    yearlyGooglePlayProductId?: string;
    features?: any;
    isActive?: boolean;
}
