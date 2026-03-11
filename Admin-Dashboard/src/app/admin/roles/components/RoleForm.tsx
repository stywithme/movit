'use client';

import React, { useState, useEffect, Fragment } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card, Input, Checkbox, Label } from '@/components/ui';
import { ChevronLeft, Save, Info, X, UserPlus, Search } from 'lucide-react';
import type { Role } from '@/lib/types/roles';

interface RoleFormProps {
    initialData?: Partial<Role>;
    isEdit?: boolean;
}

interface AdminBasic {
    id: string;
    name: string;
    email: string;
    role?: { id: string; name: string } | null;
}

const SUBJECT_GROUP_MAP: Record<string, { name: string; group: string }> = {
    // Content
    Exercise: { name: 'Exercises', group: 'Content' },
    Workout: { name: 'Workouts', group: 'Content' },
    Program: { name: 'Programs', group: 'Content' },
    ProgramMap: { name: 'Programs Map', group: 'Content' },
    Attribute: { name: 'Attributes', group: 'Content' },
    FeedbackMessage: { name: 'Feedback Messages', group: 'Content' },
    PosePosition: { name: 'Camera Positions', group: 'Content' },
    Upload: { name: 'Uploads / Files', group: 'Content' },
    // Training System
    Level: { name: 'Levels', group: 'Training' },
    AssessmentTemplate: { name: 'Assessment Templates', group: 'Training' },
    ProgressionRule: { name: 'Progression Rules', group: 'Training' },
    // Analytics
    Analytics: { name: 'Analytics (Overview)', group: 'Analytics' },
    ProgramAnalytics: { name: 'Analytics / Programs', group: 'Analytics' },
    LevelAnalytics: { name: 'Analytics / Levels', group: 'Analytics' },
    AssessmentAnalytics: { name: 'Analytics / Assessments', group: 'Analytics' },
    // Administration
    User: { name: 'Users', group: 'Admin' },
    Admin: { name: 'Admins', group: 'Admin' },
    Role: { name: 'Roles', group: 'Admin' },
    System: { name: 'System Settings', group: 'Admin' },
    Config: { name: 'Configurations', group: 'Admin' },
    // Booking System
    Booking: { name: 'Bookings', group: 'Booking' },
    DoctorWorkTime: { name: 'Doctor Work Times', group: 'Booking' },
    CloseTime: { name: 'Close Times', group: 'Booking' },
    BookingReport: { name: 'Medical Reports', group: 'Booking' },
    // Business (Merged into Admin as seen in your screenshot)
    Plan: { name: 'Subscription Plans', group: 'Admin' },
    Subscription: { name: 'User Subscriptions', group: 'Admin' },
    // Nutrition
    Recipe: { name: 'Recipes', group: 'Nutrition' },
    MealPlan: { name: 'Meal Plans', group: 'Nutrition' },
    // Other
    Reports: { name: 'General Reports', group: 'Analytics' },
    Reassessment: { name: 'Reassessments', group: 'Training' },
    Muscle: { name: 'Muscles Library', group: 'Content' },
    Equipment: { name: 'Equipment Library', group: 'Content' },
};

const ACTIONS = [
    { id: 'read', name: 'VIEW', color: 'text-blue-600' },
    { id: 'update', name: 'EDIT', color: 'text-orange-500' },
    { id: 'create', name: 'CREATE', color: 'text-green-600' },
    { id: 'delete', name: 'DELETE', color: 'text-red-600' },
    { id: 'publish', name: 'PUBLISH', color: 'text-purple-600' },
    { id: 'duplicate', name: 'DUPLICATE', color: 'text-indigo-600' },
] as const;

interface DerivedModule {
    id: string;
    name: string;
    group: string;
}

export function RoleForm({ initialData, isEdit }: RoleFormProps) {
    const router = useRouter();
    const [loading, setLoading] = useState(false);
    const [fetchingPerms, setFetchingPerms] = useState(true);
    const [allPermissions, setAllPermissions] = useState<Array<{ id: string; subject: string; action: string }>>([]);
    const [modules, setModules] = useState<DerivedModule[]>([]);
    const [formData, setFormData] = useState({
        name: initialData?.name || '',
        description: initialData?.description?.en || initialData?.description?.ar || '',
    });

    // permissions: { subject: { action: boolean } }
    const [permissions, setPermissions] = useState<Record<string, Record<string, boolean>>>({});

    // Admin assignment
    const [allAdmins, setAllAdmins] = useState<AdminBasic[]>([]);
    const [assignedAdminIds, setAssignedAdminIds] = useState<string[]>([]);
    const [adminSearch, setAdminSearch] = useState('');

    // --- Fetch all permissions from server ---
    useEffect(() => {
        const fetchAllPermissions = async () => {
            try {
                const res = await fetch('/api/admin/permissions');
                const data = await res.json();
                if (data.success) {
                    setAllPermissions(data.data);

                    // Derive modules from unique subjects
                    const subjects = Array.from(new Set((data.data as any[]).map(p => p.subject)));
                    const derived = subjects.map(s => ({
                        id: s,
                        name: SUBJECT_GROUP_MAP[s]?.name || s,
                        group: SUBJECT_GROUP_MAP[s]?.group || 'Other'
                    }));

                    // Sort by group then name to keep UI consistent
                    derived.sort((a, b) => {
                        if (a.group !== b.group) return a.group.localeCompare(b.group);
                        return a.name.localeCompare(b.name);
                    });

                    setModules(derived);
                }
            } catch (error) {
                console.error('Error fetching global permissions', error);
            } finally {
                setFetchingPerms(false);
            }
        };
        fetchAllPermissions();
    }, []);

    // --- Fetch all admins and pre-select those assigned to this role ---
    useEffect(() => {
        const fetchAdmins = async () => {
            try {
                const res = await fetch('/api/admins?limit=100');
                const data = await res.json();
                if (data.success) {
                    setAllAdmins(data.data);
                    // Pre-select admins already assigned to this role
                    if (initialData?.id) {
                        const preSelected = (data.data as AdminBasic[]).filter(
                            (a) => a.role?.id === initialData.id
                        ).map(a => a.id);
                        setAssignedAdminIds(preSelected);
                    }
                }
            } catch (error) {
                console.error('Error fetching admins', error);
            }
        };
        fetchAdmins();
    }, [initialData?.id]);

    // --- Init permissions from initialData ---
    useEffect(() => {
        if (initialData?.permissions) {
            const perms: Record<string, Record<string, boolean>> = {};
            initialData.permissions.forEach((p) => {
                if (!perms[p.permission.subject]) perms[p.permission.subject] = {};
                perms[p.permission.subject][p.permission.action] = true;
            });
            setPermissions(perms);
        }
    }, [initialData]);

    const togglePermission = (subject: string, action: string) => {
        setPermissions((prev) => ({
            ...prev,
            [subject]: { ...(prev[subject] || {}), [action]: !prev[subject]?.[action] },
        }));
    };

    const toggleAllInModule = (subject: string, checked: boolean) => {
        const availableActions = ACTIONS.filter(a => allPermissions.some(p => p.subject === subject && p.action === a.id));
        const modulePerms: Record<string, boolean> = {};
        availableActions.forEach((action) => { modulePerms[action.id] = checked; });
        setPermissions((prev) => ({ ...prev, [subject]: modulePerms }));
    };

    const isAllInModuleChecked = (subject: string) => {
        const availableActions = ACTIONS.filter(a => allPermissions.some(p => p.subject === subject && p.action === a.id));
        if (availableActions.length === 0) return false;
        return availableActions.every((action) => permissions[subject]?.[action.id]);
    };

    const totalEnabled = Object.values(permissions).reduce((t, actions) =>
        t + Object.values(actions).filter(Boolean).length, 0);

    const toggleAdmin = (adminId: string) => {
        setAssignedAdminIds(prev =>
            prev.includes(adminId) ? prev.filter(id => id !== adminId) : [...prev, adminId]
        );
    };

    const filteredAdmins = allAdmins.filter(a =>
        a.name.toLowerCase().includes(adminSearch.toLowerCase()) ||
        a.email.toLowerCase().includes(adminSearch.toLowerCase())
    );

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);

        try {
            const selectedPermissionIds: string[] = [];
            Object.entries(permissions).forEach(([subject, actions]) => {
                Object.entries(actions).forEach(([action, checked]) => {
                    if (checked) {
                        const p = allPermissions.find((perm) => perm.subject === subject && perm.action === action);
                        if (p) selectedPermissionIds.push(p.id);
                    }
                });
            });

            const payload = {
                name: formData.name,
                displayName: { en: formData.name, ar: formData.name },
                description: { en: formData.description, ar: formData.description },
                permissionIds: selectedPermissionIds,
                assignedAdminIds: assignedAdminIds.length > 0 ? assignedAdminIds : undefined,
            };

            const url = isEdit
                ? `/api/admin/permissions/roles/${initialData?.id}`
                : '/api/admin/permissions/roles';

            const res = await fetch(url, {
                method: isEdit ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            const data = await res.json();
            if (data.success) {
                router.push('/admin/roles');
            } else {
                alert('Error: ' + data.error);
            }
        } catch (error) {
            console.error('Error saving role:', error);
            alert('Error saving role');
        } finally {
            setLoading(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-8 animate-in fade-in duration-500">
            {/* Top Header */}
            <div className="flex justify-between items-center -mt-2">
                <div className="flex items-center gap-4">
                    <button
                        type="button"
                        onClick={() => router.back()}
                        className="p-2 hover:bg-gray-100 rounded-full transition-colors text-gray-500"
                    >
                        <ChevronLeft className="w-6 h-6" />
                    </button>
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">
                            {isEdit ? 'Update Role' : 'Create Role'}
                        </h1>
                        <p className="text-sm text-gray-500 mt-0.5">Define a new set of permissions</p>
                    </div>
                </div>
                <Button
                    type="submit"
                    disabled={loading || fetchingPerms}
                    className="flex items-center gap-2 px-6 shadow-md shadow-blue-200"
                >
                    <Save className="w-4 h-4" />
                    {loading || fetchingPerms ? 'Saving...' : 'Save Role'}
                </Button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* Left Column */}
                <div className="lg:col-span-2 space-y-8">
                    {/* General Information */}
                    <Card className="p-8 border-none shadow-sm">
                        <h2 className="text-lg font-bold text-gray-900 border-b border-gray-100 pb-4 mb-6">General Information</h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div className="space-y-2">
                                <Label className="text-sm font-semibold text-gray-700 flex items-center gap-1.5">
                                    <span className="text-red-500">*</span> Role Name
                                </Label>
                                <Input
                                    value={formData.name}
                                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                                    placeholder="e.g. Content Manager"
                                    required
                                    className="bg-gray-50/50 border-gray-200 focus:bg-white transition-all"
                                />
                            </div>

                            <div className="space-y-2">
                                <Label className="text-sm font-semibold text-gray-700">Description</Label>
                                <Input
                                    value={formData.description}
                                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                                    placeholder="Briefly describe what this role is for..."
                                    className="bg-gray-50/50 border-gray-200 focus:bg-white transition-all"
                                />
                            </div>
                        </div>
                    </Card>

                    {/* Permission Matrix — NO horizontal scroll */}
                    <Card className="border-none shadow-sm overflow-hidden">
                        <div className="p-8 pb-4">
                            <div className="flex items-center justify-between">
                                <h2 className="text-lg font-bold text-gray-900">Permission Matrix</h2>
                                <span className="text-sm font-semibold text-blue-600 bg-blue-50 px-3 py-1 rounded-full">
                                    {totalEnabled} Actions Enabled
                                </span>
                            </div>
                        </div>

                        <div className="mx-8 mb-6 p-4 bg-blue-50/50 border border-blue-100 rounded-xl flex items-start gap-3">
                            <Info className="w-5 h-5 text-blue-600 mt-0.5 shrink-0" />
                            <div>
                                <h3 className="text-sm font-bold text-blue-900">Configure Access Levels</h3>
                                <p className="text-xs text-blue-700 mt-1 leading-relaxed">
                                    Select the actions this role is allowed to perform. <span className="font-bold">×</span> means the action is not available for that module.
                                </p>
                            </div>
                        </div>

                        {/* Table — fixed layout, no overflow-x */}
                        <div className="w-full">
                            <table className="w-full text-sm text-left border-collapse table-fixed">
                                <colgroup>
                                    <col className="w-8" />
                                    <col className="w-[200px]" />
                                    {ACTIONS.map(a => <col key={a.id} />)}
                                </colgroup>
                                <thead className="bg-gray-50/80 text-gray-500 uppercase tracking-wider text-[10px] font-bold">
                                    <tr>
                                        <th className="px-4 py-4 border-b border-gray-100 text-center">ALL</th>
                                        <th className="px-4 py-4 border-b border-gray-100">MODULE</th>
                                        {ACTIONS.map((action) => (
                                            <th key={action.id} className={`px-2 py-4 border-b border-gray-100 text-center ${action.color}`}>
                                                {action.name}
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {modules.map((module) => {
                                        return (
                                            <tr key={module.id} className="hover:bg-gray-50/30 transition-colors group">
                                                <td className="px-4 py-3 text-center">
                                                    <div className="flex justify-center">
                                                        <Checkbox
                                                            checked={isAllInModuleChecked(module.id)}
                                                            onCheckedChange={(checked) => toggleAllInModule(module.id, !!checked)}
                                                            className="border-gray-300 data-[state=checked]:bg-blue-600 data-[state=checked]:border-blue-600"
                                                        />
                                                    </div>
                                                </td>
                                                <td className="px-4 py-3 font-semibold text-gray-700 group-hover:text-blue-600 transition-colors text-xs leading-tight">
                                                    {module.name}
                                                </td>
                                                {ACTIONS.map((action) => {
                                                    const isAvailable = allPermissions.some(p => p.subject === module.id && p.action === action.id);
                                                    return (
                                                        <td key={action.id} className="px-2 py-3">
                                                            <div className="flex justify-center">
                                                                {isAvailable ? (
                                                                    <Checkbox
                                                                        checked={permissions[module.id]?.[action.id] || false}
                                                                        onCheckedChange={() => togglePermission(module.id, action.id)}
                                                                        className="border-gray-200 data-[state=checked]:bg-blue-600 data-[state=checked]:border-blue-600"
                                                                    />
                                                                ) : (
                                                                    <span className="text-gray-200 font-bold select-none text-base">×</span>
                                                                )}
                                                            </div>
                                                        </td>
                                                    );
                                                })}
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </Card>
                </div>

                {/* Right Column — Assign Admins */}
                <div className="space-y-8">
                    <Card className="border-none shadow-sm h-fit sticky top-8 overflow-hidden">
                        <div className="p-6 border-b border-gray-100">
                            <div className="flex items-center gap-2 mb-1">
                                <UserPlus className="w-5 h-5 text-blue-600" />
                                <h3 className="text-base font-bold text-gray-900">Assign Admins</h3>
                            </div>
                            <p className="text-xs text-gray-500">Select which admins will have this role.</p>
                        </div>

                        {/* Search */}
                        <div className="px-4 pt-4 pb-2">
                            <div className="relative">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                                <input
                                    type="text"
                                    value={adminSearch}
                                    onChange={(e) => setAdminSearch(e.target.value)}
                                    placeholder="Search admins..."
                                    className="w-full pl-9 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
                                />
                            </div>
                        </div>

                        {/* Assigned badges */}
                        {assignedAdminIds.length > 0 && (
                            <div className="px-4 pb-3 flex flex-wrap gap-2">
                                {assignedAdminIds.map(id => {
                                    const a = allAdmins.find(x => x.id === id);
                                    if (!a) return null;
                                    return (
                                        <span key={id} className="flex items-center gap-1 text-xs bg-blue-50 text-blue-700 border border-blue-100 rounded-full px-2 py-0.5 font-medium">
                                            {a.name.split(' ')[0]}
                                            <button type="button" onClick={() => toggleAdmin(id)} className="hover:text-red-500 transition-colors">
                                                <X className="w-3 h-3" />
                                            </button>
                                        </span>
                                    );
                                })}
                            </div>
                        )}

                        {/* Admin List */}
                        <div className="max-h-80 overflow-y-auto divide-y divide-gray-50">
                            {filteredAdmins.length === 0 ? (
                                <div className="p-6 text-center text-sm text-gray-400">No admins found</div>
                            ) : filteredAdmins.map((admin) => {
                                const isAssigned = assignedAdminIds.includes(admin.id);
                                return (
                                    <label
                                        key={admin.id}
                                        className={`flex items-center gap-3 px-4 py-3 cursor-pointer transition-colors ${isAssigned ? 'bg-blue-50/60' : 'hover:bg-gray-50'}`}
                                    >
                                        <Checkbox
                                            checked={isAssigned}
                                            onCheckedChange={() => toggleAdmin(admin.id)}
                                            className="border-gray-300 data-[state=checked]:bg-blue-600 data-[state=checked]:border-blue-600 shrink-0"
                                        />
                                        <div className="flex-1 min-w-0">
                                            <p className="text-sm font-semibold text-gray-900 truncate">{admin.name}</p>
                                            <p className="text-xs text-gray-400 truncate">{admin.email}</p>
                                        </div>
                                        {admin.role && (
                                            <span className="text-[10px] bg-gray-100 text-gray-500 rounded-full px-2 py-0.5 font-medium shrink-0">
                                                {admin.role.name}
                                            </span>
                                        )}
                                    </label>
                                );
                            })}
                        </div>

                        <div className="p-4 border-t border-gray-100">
                            <Button
                                type="button"
                                variant="secondary"
                                className="w-full text-xs font-bold"
                                onClick={() => router.back()}
                            >
                                CANCEL CHANGES
                            </Button>
                        </div>
                    </Card>
                </div>
            </div >
        </form >
    );
}
