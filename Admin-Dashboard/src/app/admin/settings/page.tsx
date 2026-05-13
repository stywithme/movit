'use client';

import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { toast } from 'react-hot-toast';
import { Loader2, Save } from 'lucide-react';

interface Setting {
    key: string;
    value: string;
}

const SETTINGS_METADATA: Record<string, { label: string; type: 'number' | 'text' | 'boolean'; group: string }> = {
    booking_duration: { label: 'Booking Duration (Minutes)', type: 'number', group: 'Time' },
    max_advance_booking_days: { label: 'Max Advance Booking (Days)', type: 'number', group: 'Time' },
    min_booking_hours: { label: 'Min Advance Booking (Hours)', type: 'number', group: 'Time' },
    reschedule_allowed_time: { label: 'Reschedule Allowed Before (Hours)', type: 'number', group: 'Time' },
    meeting_join_before_minutes: { label: 'Join Button Opens (Minutes Before Meeting)', type: 'number', group: 'Time' },
    meeting_join_after_minutes: { label: 'Join Button Closes (Minutes After Meeting)', type: 'number', group: 'Time' },
    booking_price: { label: 'Booking Price', type: 'number', group: 'Payment' },
    follow_up_price: { label: 'Follow-up Price', type: 'number', group: 'Payment' },
    booking_currency: { label: 'Booking Currency', type: 'text', group: 'Payment' },
    allow_booking: { label: 'Allow New Bookings', type: 'boolean', group: 'Status' },
};

export default function SettingsPage() {
    const [settings, setSettings] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        fetchSettings();
    }, []);

    const fetchSettings = async () => {
        try {
            const res = await fetch('/api/admin/system');
            const json = await res.json();
            if (json.success) {
                const data: Record<string, string> = {};
                json.data.forEach((s: Setting) => {
                    data[s.key] = s.value;
                });
                setSettings(data);
            }
        } catch (error) {
            toast.error('Failed to load settings');
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setSaving(true);
        try {
            const res = await fetch('/api/admin/system', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(settings),
            });
            const json = await res.json();
            if (json.success) {
                toast.success('Settings saved successfully');
            } else {
                toast.error(json.error || 'Failed to save settings');
            }
        } catch (error) {
            toast.error('Failed to save settings');
        } finally {
            setSaving(false);
        }
    };

    const updateSetting = (key: string, value: string) => {
        setSettings(prev => ({ ...prev, [key]: value }));
    };

    if (loading) {
        return (
            <div className="flex h-[400px] items-center justify-center">
                <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
            </div>
        );
    }

    const groups = Array.from(new Set(Object.values(SETTINGS_METADATA).map(m => m.group)));

    return (
        <div className="max-w-4xl mx-auto space-y-6">
            <div>
                <h1 className="text-2xl font-bold tracking-tight text-black">System Settings</h1>
                <p className="text-gray-500 text-sm">Manage global configurations for the booking system.</p>
            </div>

            <form onSubmit={handleSave} className="space-y-6">
                {groups.map(group => (
                    <Card key={group}>
                        <CardHeader>
                            <CardTitle>{group} Settings</CardTitle>
                            <CardDescription>Configuration related to {group.toLowerCase()}.</CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            {Object.entries(SETTINGS_METADATA)
                                .filter(([_, meta]) => meta.group === group)
                                .map(([key, meta]) => (
                                    <div key={key} className="space-y-2">
                                        <Label htmlFor={key}>{meta.label}</Label>
                                        {meta.type === 'boolean' ? (
                                            <div className="flex items-center gap-2">
                                                <input
                                                    type="checkbox"
                                                    id={key}
                                                    checked={settings[key] === 'true'}
                                                    onChange={(e) => updateSetting(key, e.target.checked.toString())}
                                                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-600"
                                                />
                                                <span className="text-sm text-gray-600">Enabled</span>
                                            </div>
                                        ) : (
                                            <Input
                                                id={key}
                                                type={meta.type}
                                                value={settings[key] || ''}
                                                onChange={(e) => updateSetting(key, e.target.value)}
                                                placeholder={`Enter ${meta.label.toLowerCase()}...`}
                                            />
                                        )}
                                    </div>
                                ))}
                        </CardContent>
                    </Card>
                ))}

                <div className="flex justify-end pt-4">
                    <Button type="submit" disabled={saving} size="lg" className="px-10">
                        {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
                        Save Settings
                    </Button>
                </div>
            </form>
        </div>
    );
}
