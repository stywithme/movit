'use client';

import { CloseTimeForm } from '../components/CloseTimeForm';

export default function NewCloseTimePage() {
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
