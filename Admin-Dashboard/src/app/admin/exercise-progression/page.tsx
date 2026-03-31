'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Badge, Input } from '@/components/ui';

const ARCHETYPE_LABELS: Record<string, { label: string; color: 'primary' | 'purple' | 'teal' | 'yellow' | 'default' }> = {
  weighted_strength: { label: 'Weighted Strength', color: 'primary' },
  bodyweight_dynamic: { label: 'Bodyweight Dynamic', color: 'purple' },
  isometric_hold: { label: 'Isometric Hold', color: 'teal' },
  mobility_rom: { label: 'Mobility ROM', color: 'yellow' },
  motor_control: { label: 'Motor Control', color: 'default' },
};

const ARCHETYPE_OPTIONS = [
  { value: '', label: 'Select Archetype...' },
  { value: 'weighted_strength', label: 'Weighted Strength — Squat, Bench, Deadlift' },
  { value: 'bodyweight_dynamic', label: 'Bodyweight Dynamic — Push-ups, Pull-ups' },
  { value: 'isometric_hold', label: 'Isometric Hold — Plank, Wall Sit' },
  { value: 'mobility_rom', label: 'Mobility ROM — Stretches, Hip Opener' },
  { value: 'motor_control', label: 'Motor Control — Balance, Coordination' },
];

const PROFILE_STATUS_LABELS: Record<string, { label: string; className: string }> = {
  auto: { label: 'Auto', className: 'bg-blue-100 text-blue-700' },
  custom: { label: 'Custom', className: 'bg-green-100 text-green-700' },
  none: { label: 'None', className: 'bg-gray-100 text-gray-500' },
};

interface ExerciseWithProfile {
  id: string;
  name: { en: string; ar: string };
  slug: string;
  archetype: string | null;
  supportsWeight: boolean;
  profileStatus: 'auto' | 'custom' | 'none';
  profileId: string | null;
  profileUpdatedAt: string | null;
}

export default function ExerciseProgressionPage() {
  const router = useRouter();
  const [exercises, setExercises] = useState<ExerciseWithProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filterArchetype, setFilterArchetype] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [settingArchetype, setSettingArchetype] = useState<string | null>(null);

  const fetchExercises = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/exercise-progression-profiles?view=exercises');
      const data = await res.json();
      if (data.success) {
        setExercises(data.data);
      }
    } catch (error) {
      console.error('Error fetching exercises:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchExercises();
  }, [fetchExercises]);

  const handleSetArchetype = async (exerciseId: string, archetype: string) => {
    if (!archetype) return;
    setSettingArchetype(exerciseId);
    try {
      const res = await fetch(`/api/admin/exercise-progression-profiles/${exerciseId}/archetype`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ archetype }),
      });
      const data = await res.json();
      if (data.success) {
        fetchExercises();
      }
    } catch (error) {
      console.error('Error setting archetype:', error);
    } finally {
      setSettingArchetype(null);
    }
  };

  const filteredExercises = exercises.filter((ex) => {
    if (search) {
      const q = search.toLowerCase();
      const nameEn = (ex.name?.en || '').toLowerCase();
      const nameAr = (ex.name?.ar || '').toLowerCase();
      if (!nameEn.includes(q) && !nameAr.includes(q) && !ex.slug.includes(q)) return false;
    }
    if (filterArchetype && ex.archetype !== filterArchetype) return false;
    if (filterStatus && ex.profileStatus !== filterStatus) return false;
    return true;
  });

  const stats = {
    total: exercises.length,
    withProfile: exercises.filter((e) => e.profileStatus !== 'none').length,
    withArchetype: exercises.filter((e) => e.archetype).length,
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Exercise Progression Profiles</h1>
        <p className="text-gray-600 mt-1">
          Assign archetypes and manage progression parameters for each exercise
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <p className="text-sm text-gray-500">Total Exercises</p>
          <p className="text-2xl font-bold text-gray-900">{stats.total}</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <p className="text-sm text-gray-500">With Archetype</p>
          <p className="text-2xl font-bold text-blue-600">{stats.withArchetype}</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <p className="text-sm text-gray-500">With Profile</p>
          <p className="text-2xl font-bold text-green-600">{stats.withProfile}</p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4 items-end">
        <div className="flex-1">
          <Input
            placeholder="Search by name or slug..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <select
          className="border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white"
          value={filterArchetype}
          onChange={(e) => setFilterArchetype(e.target.value)}
        >
          <option value="">All Archetypes</option>
          <option value="weighted_strength">Weighted Strength</option>
          <option value="bodyweight_dynamic">Bodyweight Dynamic</option>
          <option value="isometric_hold">Isometric Hold</option>
          <option value="mobility_rom">Mobility ROM</option>
          <option value="motor_control">Motor Control</option>
        </select>
        <select
          className="border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white"
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
        >
          <option value="">All Statuses</option>
          <option value="auto">Auto Generated</option>
          <option value="custom">Custom</option>
          <option value="none">No Profile</option>
        </select>
      </div>

      {/* Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading exercises...</div>
        ) : filteredExercises.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            No exercises found matching your filters.
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Exercise
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Archetype
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Profile
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Weight
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {filteredExercises.map((ex) => {
                const archetypeInfo = ex.archetype ? ARCHETYPE_LABELS[ex.archetype] : null;
                const statusInfo = PROFILE_STATUS_LABELS[ex.profileStatus];
                return (
                  <tr key={ex.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4">
                      <p className="font-medium text-gray-900">{ex.name?.en || ex.slug}</p>
                      <p className="text-xs text-gray-500" dir="rtl">{ex.name?.ar || ''}</p>
                    </td>
                    <td className="px-6 py-4">
                      {archetypeInfo ? (
                        <Badge variant={archetypeInfo.color}>{archetypeInfo.label}</Badge>
                      ) : (
                        <select
                          className="border border-gray-300 rounded px-2 py-1 text-sm bg-white"
                          value=""
                          disabled={settingArchetype === ex.id}
                          onChange={(e) => handleSetArchetype(ex.id, e.target.value)}
                        >
                          {ARCHETYPE_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                          ))}
                        </select>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${statusInfo.className}`}>
                        {statusInfo.label}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      {ex.supportsWeight ? 'Yes' : 'No'}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => router.push(`/admin/exercise-progression/${ex.id}`)}
                        className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                      >
                        {ex.profileStatus === 'none' ? 'Configure' : 'Edit Profile'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
