'use client';

import { WorkTimeForm } from '../components/WorkTimeForm';

export default function NewWorkTimePage() {
    return (
        <div className="space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-gray-900">Add Work Time</h1>
                <p className="text-gray-600 mt-1">Schedule working hours for a doctor</p>
            </div>

            <WorkTimeForm />
        </div>
    );
}
