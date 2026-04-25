export interface Plan {
    id: string;
    name: Record<string, any>;
    description?: Record<string, any> | null;
    monthlyPrice: number;
    yearlyPrice: number;
    currency: string;
    discount: number;
    maxExercisesLimit: number;
    maxWorkoutsLimit: number;
    freeDoctorSessionsLimit: number;
    monthlyGooglePlayProductId?: string | null;
    yearlyGooglePlayProductId?: string | null;
    features?: any;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface CreatePlanDto {
    name: Record<string, any>;
    description?: Record<string, any> | null;
    monthlyPrice: number;
    yearlyPrice: number;
    currency?: string;
    discount?: number;
    maxExercisesLimit?: number;
    maxWorkoutsLimit?: number;
    freeDoctorSessionsLimit?: number;
    monthlyGooglePlayProductId?: string | null;
    yearlyGooglePlayProductId?: string | null;
    features?: any;
    isActive?: boolean;
}

export interface UpdatePlanDto extends Partial<CreatePlanDto> { }
