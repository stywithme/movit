'use client';

import { BookingReportForm } from '../components/BookingReportForm';

export default function NewBookingReportPage() {
    return (
        <div className="space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-gray-900">Write Medical Report</h1>
                <p className="text-gray-600 mt-1">Create a post-session diagnosis and prescription</p>
            </div>

            <BookingReportForm />
        </div>
    );
}
