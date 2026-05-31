'use client';

import { BookingReportForm } from '../components/BookingReportForm';
import { PageHeader } from '@/components/common';

export default function NewBookingReportPage() {
    return (
        <div className="space-y-6">
            <PageHeader
                title="Write Medical Report"
                description="Create a post-session diagnosis and prescription"
                breadcrumbs={[
                    { label: 'Medical Reports', href: '/admin/booking-reports' },
                    { label: 'Write Medical Report' },
                ]}
            />

            <BookingReportForm />
        </div>
    );
}
