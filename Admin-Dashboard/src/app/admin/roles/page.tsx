'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Input, Button, Card } from '@/components/ui';
import { Shield, Plus, Search, Edit2, Trash2, UsersIcon } from 'lucide-react';
import type { Role } from '@/lib/types/roles';

export default function RolesPage() {
    const [roles, setRoles] = useState<Role[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');

    const fetchRoles = async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (searchQuery) params.set('search', searchQuery);

            const res = await fetch(`/api/admin/permissions/roles?${params}`);
            const data = await res.json();

            if (data.success) {
                setRoles(data.data);
            }
        } catch (error) {
            console.error('Error fetching roles:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchRoles();
    }, []);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchRoles();
    };

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to delete this role?')) return;
        try {
            const res = await fetch(`/api/admin/permissions/roles/${id}`, { method: 'DELETE' });
            if (res.ok) fetchRoles();
            else {
                const data = await res.json();
                alert('Error: ' + (data.error || 'Failed to delete role'));
            }
        } catch (error) {
            console.error('Error deleting role:', error);
        }
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Roles Management</h1>
                    <p className="text-gray-600 mt-1">Define and manage roles and their permissions</p>
                </div>
                <Link href="/admin/roles/new">
                    <Button className="flex items-center gap-2">
                        <Plus className="w-4 h-4" />
                        Add Role
                    </Button>
                </Link>
            </div>

            {/* Filters */}
            <Card className="p-4">
                <form onSubmit={handleSearch} className="flex gap-4">
                    <div className="relative flex-1">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                        <Input
                            type="text"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="Search roles..."
                            className="pl-9 w-full"
                        />
                    </div>
                    <Button type="submit" variant="secondary">
                        Search
                    </Button>
                </form>
            </Card>

            {/* Roles Table */}
            <Card className="overflow-hidden border-none shadow-sm">
                {loading ? (
                    <div className="p-12 text-center text-gray-500">
                        <div className="animate-spin h-8 w-8 border-4 border-blue-600 border-t-transparent rounded-full mx-auto mb-4"></div>
                        Loading roles...
                    </div>
                ) : roles.length === 0 ? (
                    <div className="p-12 text-center text-gray-500">
                        <Shield className="h-12 w-12 text-gray-300 mx-auto mb-4" />
                        <p className="text-lg font-medium text-gray-900">No roles found</p>
                        <p className="mt-1">Get started by creating your first role.</p>
                        <Link href="/admin/roles/new" className="mt-4 inline-block">
                            <Button variant="outline">Create Role</Button>
                        </Link>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="bg-gray-50 text-gray-500 font-medium border-b border-gray-100">
                                <tr>
                                    <th className="px-6 py-4 font-semibold uppercase tracking-wider">ID</th>
                                    <th className="px-6 py-4 font-semibold uppercase tracking-wider">Role Name</th>
                                    <th className="px-6 py-4 font-semibold uppercase tracking-wider">Permissions</th>
                                    <th className="px-6 py-4 font-semibold uppercase tracking-wider">Assigned Admins</th>
                                    <th className="px-6 py-4 font-semibold uppercase tracking-wider text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {roles.map((role, index) => (
                                    <tr key={role.id} className="hover:bg-gray-50/50 transition-colors">
                                        <td className="px-6 py-4 text-gray-400 font-mono text-xs">
                                            {index + 1}
                                        </td>
                                        <td className="px-6 py-4">
                                            <div>
                                                <div className="font-bold text-gray-900">{role.name}</div>
                                                <div className="text-xs text-gray-500">{role.displayName.en}</div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-purple-50 text-purple-700 border border-purple-100">
                                                {role._count?.permissions || 0} Actions
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-2">
                                                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-orange-50 text-orange-700 border border-orange-100">
                                                    {role._count?.admins || 0} Members
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-right leading-none">
                                            <div className="flex justify-end gap-2">
                                                <Link href={`/admin/roles/${role.id}/edit`}>
                                                    <button className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors" title="Edit Role">
                                                        <Edit2 className="w-4 h-4" />
                                                    </button>
                                                </Link>
                                                {!role.isSystem && (
                                                    <button
                                                        onClick={() => handleDelete(role.id)}
                                                        className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                                                        title="Delete Role"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                )}
                                                <button className="p-2 text-gray-400 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors" title="View Members">
                                                    <UsersIcon className="w-4 h-4" />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </Card>
        </div>
    );
}
