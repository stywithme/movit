'use client';

/**
 * Exercise Families — list / reorder / rename (backend: admin/exercise-families)
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { toast } from 'sonner';
import { Button, Badge, Card, CardHeader, CardTitle, CardContent, Input, Label } from '@/components/ui';
import { PageHeader } from '@/components/common';

interface FamilyRow {
  familyKey: string;
  exerciseCount: number;
  dominantArchetype: string | null;
}

interface FamilyExercise {
  id: string;
  slug: string;
  name: { en: string; ar: string };
  archetype: string | null;
  movementPattern: string | null;
  familyOrder: number;
}

export default function ExerciseFamiliesPage() {
  const [families, setFamilies] = useState<FamilyRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [detailByKey, setDetailByKey] = useState<Record<string, FamilyExercise[]>>({});
  const [loadingDetail, setLoadingDetail] = useState<string | null>(null);
  const [renameKey, setRenameKey] = useState('');
  const [renameSaving, setRenameSaving] = useState(false);

  const fetchFamilies = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/exercise-families');
      const data = await res.json();
      if (data.success) setFamilies(data.data ?? []);
      else toast.error(data.error || 'Failed to load families');
    } catch {
      toast.error('Failed to load families');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchFamilies();
  }, [fetchFamilies]);

  const sortedFamilies = useMemo(
    () => [...families].sort((a, b) => a.familyKey.localeCompare(b.familyKey)),
    [families],
  );

  const loadDetail = async (familyKey: string) => {
    if (detailByKey[familyKey]) return;
    setLoadingDetail(familyKey);
    try {
      const enc = encodeURIComponent(familyKey);
      const res = await fetch(`/api/admin/exercise-families/${enc}`);
      const data = await res.json();
      if (data.success && data.data?.exercises) {
        setDetailByKey((prev) => ({ ...prev, [familyKey]: data.data.exercises }));
      } else {
        toast.error(data.error || 'Failed to load family');
      }
    } catch {
      toast.error('Failed to load family');
    } finally {
      setLoadingDetail(null);
    }
  };

  const toggleExpand = async (familyKey: string) => {
    if (expandedKey === familyKey) {
      setExpandedKey(null);
      return;
    }
    setExpandedKey(familyKey);
    await loadDetail(familyKey);
  };

  const persistOrder = async (familyKey: string, ordered: FamilyExercise[]) => {
    const enc = encodeURIComponent(familyKey);
    try {
      const res = await fetch(`/api/admin/exercise-families/${enc}/order`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderedExerciseIds: ordered.map((e) => e.id) }),
      });
      const data = await res.json();
      if (data.success) {
        toast.success('Order saved');
        setDetailByKey((prev) => ({
          ...prev,
          [familyKey]: ordered.map((e, i) => ({ ...e, familyOrder: i + 1 })),
        }));
      } else {
        toast.error(data.error || 'Failed to save order');
      }
    } catch {
      toast.error('Failed to save order');
    }
  };

  const moveExercise = (familyKey: string, list: FamilyExercise[], index: number, delta: number) => {
    const nextIndex = index + delta;
    if (nextIndex < 0 || nextIndex >= list.length) return;
    const copy = [...list];
    const [removed] = copy.splice(index, 1);
    copy.splice(nextIndex, 0, removed!);
    setDetailByKey((prev) => ({ ...prev, [familyKey]: copy }));
    void persistOrder(familyKey, copy);
  };

  const submitRename = async (oldKey: string) => {
    const newKey = renameKey.trim();
    if (!newKey || newKey === oldKey) {
      toast.error('Enter a different family key');
      return;
    }
    setRenameSaving(true);
    try {
      const enc = encodeURIComponent(oldKey);
      const res = await fetch(`/api/admin/exercise-families/${enc}/rename`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newKey }),
      });
      const data = await res.json();
      if (data.success) {
        toast.success(data.message || 'Renamed');
        setRenameKey('');
        setExpandedKey(null);
        setDetailByKey({});
        await fetchFamilies();
      } else {
        toast.error(data.error || 'Rename failed');
      }
    } catch {
      toast.error('Rename failed');
    } finally {
      setRenameSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Exercise families"
        description="Groups by familyKey. Order drives substitution and progression ladders."
        actions={
          <Button asChild variant="outline">
            <Link href="/admin/exercise-progression">Back to progression</Link>
          </Button>
        }
      />

      {loading ? (
        <p className="text-muted-foreground">Loading...</p>
      ) : sortedFamilies.length === 0 ? (
        <p className="text-muted-foreground">No families found (assign familyKey on exercises).</p>
      ) : (
        <div className="space-y-3">
          {sortedFamilies.map((row) => (
            <Card key={row.familyKey}>
              <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 pb-2">
                <div>
                  <CardTitle className="text-lg font-semibold">{row.familyKey}</CardTitle>
                  <div className="mt-1 flex flex-wrap gap-2">
                    <Badge variant="outline">{row.exerciseCount} exercises</Badge>
                    {row.dominantArchetype ? (
                      <Badge variant="default">{row.dominantArchetype}</Badge>
                    ) : null}
                  </div>
                </div>
                <Button type="button" variant="outline" size="sm" onClick={() => void toggleExpand(row.familyKey)}>
                  {expandedKey === row.familyKey ? 'Collapse' : 'Manage'}
                </Button>
              </CardHeader>
              {expandedKey === row.familyKey ? (
                <CardContent className="space-y-4 border-t pt-4">
                  <div className="grid gap-3 md:grid-cols-[1fr_auto] md:items-end">
                    <div>
                      <Label>Rename family key</Label>
                      <Input
                        value={renameKey}
                        onChange={(e) => setRenameKey(e.target.value)}
                        placeholder={`New key (was ${row.familyKey})`}
                      />
                    </div>
                    <Button
                      type="button"
                      variant="secondary"
                      disabled={renameSaving}
                      onClick={() => void submitRename(row.familyKey)}
                    >
                      {renameSaving ? 'Saving…' : 'Rename'}
                    </Button>
                  </div>

                  {loadingDetail === row.familyKey ? (
                    <p className="text-sm text-muted-foreground">Loading exercises...</p>
                  ) : (
                    <div className="space-y-2">
                      <p className="text-sm font-medium">Order (first = easiest progression step)</p>
                      <ul className="divide-y rounded-lg border">
                        {(detailByKey[row.familyKey] ?? []).map((ex, idx, arr) => (
                          <li key={ex.id} className="flex flex-wrap items-center gap-2 px-3 py-2 text-sm">
                            <span className="w-8 text-muted-foreground">{idx + 1}</span>
                            <span className="min-w-0 flex-1 font-medium">
                              {ex.name.en} / <span dir="rtl">{ex.name.ar}</span>
                            </span>
                            <span className="text-xs text-muted-foreground">{ex.slug}</span>
                            <div className="flex gap-1">
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                disabled={idx === 0}
                                onClick={() => moveExercise(row.familyKey, arr, idx, -1)}
                              >
                                Up
                              </Button>
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                disabled={idx === arr.length - 1}
                                onClick={() => moveExercise(row.familyKey, arr, idx, 1)}
                              >
                                Down
                              </Button>
                              <Link href={`/admin/exercises/${ex.id}/edit`}>
                                <Button type="button" variant="ghost" size="sm">
                                  Edit
                                </Button>
                              </Link>
                            </div>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </CardContent>
              ) : null}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
