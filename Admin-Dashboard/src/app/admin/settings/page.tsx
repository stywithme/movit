'use client';

import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { PageHeader } from '@/components/common';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

const SETTINGS_METADATA: Record<string, { label: string; type: 'number' | 'text' | 'boolean'; group: string }> = {};

export default function SettingsPage() {
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchSettings = async () => {
            try {
                const res = await fetch('/api/admin/system');
                const json = await res.json();
                if (!json.success) {
                    toast.error('Failed to load settings');
                }
            } catch {
                toast.error('Failed to load settings');
            } finally {
                setLoading(false);
            }
        };

        fetchSettings();
    }, []);

    if (loading) {
        return (
            <div className="flex h-[400px] items-center justify-center">
                <Loader2 className="h-8 w-8 animate-spin text-primary" />
            </div>
        );
    }

    const groups = Array.from(new Set(Object.values(SETTINGS_METADATA).map(m => m.group)));

    return (
        <div className="max-w-4xl mx-auto space-y-6">
            <PageHeader
                title="System Settings"
                description="Manage global platform configurations."
            />

            {groups.length === 0 ? (
                <Card>
                    <CardHeader>
                        <CardTitle>Settings</CardTitle>
                        <CardDescription>No configurable settings are available in the dashboard yet.</CardDescription>
                    </CardHeader>
                    <CardContent />
                </Card>
            ) : (
                groups.map(group => (
                    <Card key={group}>
                        <CardHeader>
                            <CardTitle>{group} Settings</CardTitle>
                            <CardDescription>Configuration related to {group.toLowerCase()}.</CardDescription>
                        </CardHeader>
                        <CardContent />
                    </Card>
                ))
            )}
        </div>
    );
}
