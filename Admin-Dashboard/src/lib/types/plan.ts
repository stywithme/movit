export interface Plan {
    id: string;
    name: Record<string, any>;
    description?: Record<string, any> | null;
    monthlyPrice: number;
    yearlyPrice: number;
    discount: number;
    maxExercisesLimit: number;
    maxWorkoutsLimit: number;
    freeDoctorSessionsLimit: number;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface CreatePlanDto {
    name: Record<string, any>;
    description?: Record<string, any> | null;
    monthlyPrice: number;
    yearlyPrice: number;
    discount?: number;
    maxExercisesLimit?: number;
    maxWorkoutsLimit?: number;
    freeDoctorSessionsLimit?: number;
    isActive?: boolean;
}

export interface UpdatePlanDto extends Partial<CreatePlanDto> { }
