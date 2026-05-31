'use client';

import { useEffect, useState, use } from 'react';
import { RoleForm } from '../../components/RoleForm';
import type { Role } from '@/lib/types/roles';

export default function EditRolePage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const [role, setRole] = useState<Role | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchRole = async () => {
            try {
                const res = await fetch(`/api/admin/permissions/roles/${id}`);
                const data = await res.json();
                if (data.success) {
                    setRole(data.data);
                }
            } catch (error) {
                console.error('Error fetching role:', error);
            } finally {
                setLoading(false);
            }
        };

        fetchRole();
    }, [id]);

    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-[400px]">
                <div className="mr-3 h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
                <div className="font-medium text-muted-foreground">Loading role data...</div>
            </div>
        );
    }

    if (!role) {
        return (
            <div className="text-center py-12">
                <h2 className="text-2xl font-semibold">Role not found</h2>
                <p className="mt-2 text-muted-foreground">The role you are trying to edit does not exist or has been deleted.</p>
            </div>
        );
    }

    return <RoleForm initialData={role} isEdit />;
}
