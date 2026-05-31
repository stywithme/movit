'use client';

import { WorkTimeForm } from '../components/WorkTimeForm';
import { PageHeader } from '@/components/common';

export default function NewWorkTimePage() {
    return (
        <div className="space-y-6">
            <PageHeader
                title="Add Work Time"
                description="Schedule working hours for a doctor"
                breadcrumbs={[
                    { label: 'Doctor Work Times', href: '/admin/doctor-work-time' },
                    { label: 'Add Work Time' },
                ]}
            />

            <WorkTimeForm />
        </div>
    );
}
