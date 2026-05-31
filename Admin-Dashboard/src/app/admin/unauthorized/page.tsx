'use client';

import { ShieldAlert } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui';

export default function UnauthorizedPage() {
    return (
        <div className="flex min-h-screen flex-col items-center justify-center px-4 text-center animate-in fade-in zoom-in duration-500">
            <div className="mb-6 rounded-full bg-destructive/10 p-6">
                <ShieldAlert className="size-16 text-destructive" strokeWidth={1.5} />
            </div>

            <h1 className="mb-2 text-3xl font-semibold tracking-tight">Access Denied</h1>
            <p className="mb-8 max-w-md text-muted-foreground">
                You do not have the necessary permissions to view this page. If you believe this is a mistake, please contact your administrator.
            </p>

            <Button asChild>
                <Link href="/admin">Return to Dashboard</Link>
            </Button>
        </div>
    );
}
