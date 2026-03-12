'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/auth/auth-store';
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
            <div>
                <h1 className="text-2xl font-bold text-gray-900">Add Close Time</h1>
                <p className="text-gray-600 mt-1">Schedule vacations or close the clinic temporarily</p>
            </div>

            <CloseTimeForm />
        </div>
    );
}
