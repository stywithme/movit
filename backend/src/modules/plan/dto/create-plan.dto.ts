export class CreatePlanDto {
    name: any;
    description?: any;
    monthlyPrice: number;
    yearlyPrice: number;
    discount?: number;
    maxExercisesLimit?: number;
    maxWorkoutsLimit?: number;
    freeDoctorSessionsLimit?: number;
    isActive?: boolean;
}
