export interface Role {
    id: string;
    name: string;
    displayName: {
        ar: string;
        en: string;
    };
    description?: {
        ar: string;
        en: string;
    } | null;
    isSystem: boolean;
    createdAt: string;
    updatedAt: string;
    _count?: {
        admins: number;
        permissions: number;
    };
    permissions?: RolePermission[];
}

export interface RolePermission {
    id: string;
    roleId: string;
    permissionId: string;
    permission: {
        id: string;
        subject: string;
        action: string;
    };
}
