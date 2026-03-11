export interface Permission {
    action: string;
    subject: string;
}

export interface AdminProfile {
    id: string;
    email: string;
    name: string;
    roleId: string | null;
    isSuperAdmin: boolean;
    isDoctor: boolean;
    isActive: boolean;
    createdAt: string;
    permissions: Permission[];
}
