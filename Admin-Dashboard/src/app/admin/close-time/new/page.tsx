'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/auth/auth-store';
import { PageHeader } from '@/components/common';
import { CloseTimeForm } from '../components/CloseTimeForm';

export default function NewCloseTimePage() {
    const { user, initialized } = useAuthStore();
    const router = useRouter();

    useEffect(() => {
        if (initialized && user?.isDoctor) {
            router.replace('/admin/close-time');
        }
    }, [user, initialized, router]);

    if (!initialized || user?.isDoctor) {
        return null;
    }

    return (
        <div className="space-y-6">
            <PageHeader
                title="Add Close Time"
                description="Schedule vacations or close the clinic temporarily"
                breadcrumbs={[
                    { label: 'Close Times', href: '/admin/close-time' },
                    { label: 'Add Close Time' },
                ]}
            />

            <CloseTimeForm />
        </div>
    );
}
