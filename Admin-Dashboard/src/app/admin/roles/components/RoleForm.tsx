'use client';

import React, { useState, useEffect, Fragment } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Button, Card, Input, Checkbox, Label } from '@/components/ui';
import { Save, Info, X, UserPlus, Search } from 'lucide-react';
import { PageHeader } from '@/components/common';
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
    ProgramMap: { name: 'Programs & map', group: 'Content' },
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
                toast.success(isEdit ? 'Role updated' : 'Role created');
                router.push('/admin/roles');
            } else {
                toast.error(data.error || 'Error saving role');
            }
        } catch (error) {
            console.error('Error saving role:', error);
            toast.error('Error saving role');
        } finally {
            setLoading(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-8 animate-in fade-in duration-500">
            <PageHeader
                title={isEdit ? 'Update Role' : 'Create Role'}
                description="Define a set of permissions and optional admin assignments."
                breadcrumbs={[
                    { label: 'Roles', href: '/admin/roles' },
                    { label: isEdit ? 'Update Role' : 'Create Role' },
                ]}
                actions={
                    <>
                        <Button type="button" variant="outline" onClick={() => router.back()}>
                            Cancel
                        </Button>
                        <Button type="submit" loading={loading || fetchingPerms}>
                            <Save className="w-4 h-4" />
                            Save Role
                        </Button>
                    </>
                }
            />

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* Left Column */}
                <div className="lg:col-span-2 space-y-8">
                    {/* General Information */}
                    <Card className="p-8">
                        <h2 className="mb-6 border-b pb-4 text-lg font-semibold">General Information</h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div className="space-y-2">
                                <Label className="flex items-center gap-1.5 text-sm font-semibold">
                                    <span className="text-destructive">*</span> Role Name
                                </Label>
                                <Input
                                    value={formData.name}
                                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                                    placeholder="e.g. Content Manager"
                                    required
                                />
                            </div>

                            <div className="space-y-2">
                                <Label className="text-sm font-semibold">Description</Label>
                                <Input
                                    value={formData.description}
                                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                                    placeholder="Briefly describe what this role is for..."
                                />
                            </div>
                        </div>
                    </Card>

                    {/* Permission Matrix — NO horizontal scroll */}
                    <Card className="overflow-hidden">
                        <div className="p-8 pb-4">
                            <div className="flex items-center justify-between">
                                <h2 className="text-lg font-semibold">Permission Matrix</h2>
                                <span className="rounded-full bg-primary/10 px-3 py-1 text-sm font-semibold text-primary">
                                    {totalEnabled} Actions Enabled
                                </span>
                            </div>
                        </div>

                        <div className="mx-8 mb-6 flex items-start gap-3 rounded-xl border bg-muted/50 p-4">
                            <Info className="mt-0.5 w-5 h-5 shrink-0 text-primary" />
                            <div>
                                <h3 className="text-sm font-semibold">Configure Access Levels</h3>
                                <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
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
                                <thead className="bg-muted/50 text-muted-foreground uppercase tracking-wider text-[10px] font-bold">
                                    <tr>
                                        <th className="px-4 py-4 border-b text-center">ALL</th>
                                        <th className="px-4 py-4 border-b">MODULE</th>
                                        {ACTIONS.map((action) => (
                                            <th key={action.id} className={`px-2 py-4 border-b text-center ${action.color}`}>
                                                {action.name}
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody className="divide-y">
                                    {modules.map((module) => {
                                        return (
                                            <tr key={module.id} className="group transition-colors hover:bg-muted/30">
                                                <td className="px-4 py-3 text-center">
                                                    <div className="flex justify-center">
                                                        <Checkbox
                                                            checked={isAllInModuleChecked(module.id)}
                                                            onCheckedChange={(checked) => toggleAllInModule(module.id, !!checked)}
                                                            className="border-input data-[state=checked]:bg-primary data-[state=checked]:border-primary"
                                                        />
                                                    </div>
                                                </td>
                                                <td className="px-4 py-3 text-xs font-semibold leading-tight transition-colors group-hover:text-primary">
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
                                                                        className="border-input data-[state=checked]:bg-primary data-[state=checked]:border-primary"
                                                                    />
                                                                ) : (
                                                                    <span className="select-none text-base font-bold text-muted-foreground/40">×</span>
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
                    <Card className="sticky top-8 h-fit overflow-hidden">
                        <div className="border-b p-6">
                            <div className="flex items-center gap-2 mb-1">
                                <UserPlus className="w-5 h-5 text-primary" />
                                <h3 className="text-base font-semibold">Assign Admins</h3>
                            </div>
                            <p className="text-xs text-muted-foreground">Select which admins will have this role.</p>
                        </div>

                        {/* Search */}
                        <div className="px-4 pt-4 pb-2">
                            <div className="relative">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    value={adminSearch}
                                    onChange={(e) => setAdminSearch(e.target.value)}
                                    placeholder="Search admins..."
                                    className="w-full rounded-lg border border-input bg-background py-2 pl-9 pr-4 text-sm transition-all focus:outline-none focus:ring-2 focus:ring-ring"
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
                                        <span key={id} className="flex items-center gap-1 rounded-full border bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                                            {a.name.split(' ')[0]}
                                            <button type="button" onClick={() => toggleAdmin(id)} className="transition-colors hover:text-destructive">
                                                <X className="w-3 h-3" />
                                            </button>
                                        </span>
                                    );
                                })}
                            </div>
                        )}

                        {/* Admin List */}
                        <div className="max-h-80 overflow-y-auto divide-y">
                            {filteredAdmins.length === 0 ? (
                                <div className="p-6 text-center text-sm text-muted-foreground">No admins found</div>
                            ) : filteredAdmins.map((admin) => {
                                const isAssigned = assignedAdminIds.includes(admin.id);
                                return (
                                    <label
                                        key={admin.id}
                                        className={`flex cursor-pointer items-center gap-3 px-4 py-3 transition-colors ${isAssigned ? 'bg-primary/10' : 'hover:bg-muted/50'}`}
                                    >
                                        <Checkbox
                                            checked={isAssigned}
                                            onCheckedChange={() => toggleAdmin(admin.id)}
                                            className="shrink-0 border-input data-[state=checked]:bg-primary data-[state=checked]:border-primary"
                                        />
                                        <div className="flex-1 min-w-0">
                                            <p className="truncate text-sm font-semibold">{admin.name}</p>
                                            <p className="truncate text-xs text-muted-foreground">{admin.email}</p>
                                        </div>
                                        {admin.role && (
                                            <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
                                                {admin.role.name}
                                            </span>
                                        )}
                                    </label>
                                );
                            })}
                        </div>

                        <div className="border-t p-4">
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
