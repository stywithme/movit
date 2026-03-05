'use client';

import { ShieldAlert } from 'lucide-react';
import Link from 'next/link';

export default function UnauthorizedPage() {
    return (
        <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4 animate-in fade-in zoom-in duration-500">
            <div className="bg-red-50 p-6 rounded-full mb-6">
                <ShieldAlert className="w-16 h-16 text-red-500" strokeWidth={1.5} />
            </div>

            <h1 className="text-3xl font-bold text-gray-900 mb-2">Access Denied</h1>
            <p className="text-gray-600 max-w-md mb-8">
                You do not have the necessary permissions to view this page. If you believe this is a mistake, please contact your administrator.
            </p>

            <Link href="/admin">
                <button className="px-6 py-2.5 bg-gray-900 text-white font-medium rounded-lg hover:bg-gray-800 transition-colors shadow-sm">
                    Return to Dashboard
                </button>
            </Link>
        </div>
    );
}
