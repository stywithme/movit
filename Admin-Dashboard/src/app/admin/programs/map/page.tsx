'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { toast } from 'react-hot-toast';
import { Card, CardContent, Badge, Button, Input } from '@/components/ui';
import { ArrowLeft, RefreshCw, ArrowRight } from 'lucide-react';
import { LocalizedText } from '@/lib/types/localized';

interface Level {
  id: string;
  name: LocalizedText;
  order: number;
  color: string;
}

type ProgramAttributeRow = {
  mode: string;
  attributeValue?: { code: string; attribute?: { code: string } };
};

interface Program {
  id: string;
  name: LocalizedText;
  slug: string;
  type?: string;
  programDomain?: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  durationWeeks: number;
  isPublished: boolean;
  levelRangeMin?: number;
  levelRangeMax?: number;
  prerequisiteProgramId?: string | null;
  nextProgramId?: string | null;
  programAttributes?: ProgramAttributeRow[];
}

interface AssessmentMapItem {
  id: string;
  name: LocalizedText;
  type: string;
  isPublished?: boolean;
  status?: string;
  isDefault?: boolean;
  levelRangeMin?: number | null;
  levelRangeMax?: number | null;
  targetLevel?: {
    id: string;
    number?: number;
    levelNumber?: number;
    name?: LocalizedText;
    color?: string | null;
  } | null;
  _count?: { exercises: number; assessmentAttributes?: number };
}

const FILTER_ATTR_CODES = ['domain', 'goal', 'equipment', 'gender', 'place'] as const;

const TYPE_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  training: { bg: 'bg-blue-50', text: 'text-blue-700', border: 'border-blue-200' },
  mobility: { bg: 'bg-emerald-50', text: 'text-emerald-700', border: 'border-emerald-200' },
  therapeutic: { bg: 'bg-purple-50', text: 'text-purple-700', border: 'border-purple-200' },
};

function mapProgramDomainToCellType(program: Program): string {
  const fromAttr = program.programAttributes?.find(
    (r) => r.attributeValue?.attribute?.code === 'domain' && r.mode !== 'EXCLUDED',
  );
  const code = fromAttr?.attributeValue?.code;
  if (code === 'pd_mobility') return 'mobility';
  if (code === 'pd_therapeutic') return 'therapeutic';
  return 'training';
}

function programAttrValues(program: Program, attrCode: string): string[] {
  const rows = program.programAttributes ?? [];
  return rows
    .filter((r) => r.attributeValue?.attribute?.code === attrCode && r.mode !== 'EXCLUDED')
    .map((r) => r.attributeValue?.code ?? '')
    .filter(Boolean);
}

function uniqueFilterOptions(programs: Program[], attrCode: string): { code: string; label: string }[] {
  const seen = new Set<string>();
  const out: { code: string; label: string }[] = [];
  for (const p of programs) {
    for (const c of programAttrValues(p, attrCode)) {
      if (seen.has(c)) continue;
      seen.add(c);
      out.push({ code: c, label: c.replace(/^pd_|^pg_|^pl_|^pgen_|^eq_/i, '') });
    }
  }
  return out.sort((a, b) => a.label.localeCompare(b.label));
}

function programPassesFilters(
  program: Program,
  filters: Record<(typeof FILTER_ATTR_CODES)[number], string>,
): boolean {
  for (const key of FILTER_ATTR_CODES) {
    const want = filters[key];
    if (!want) continue;
    const have = programAttrValues(program, key);
    if (!have.includes(want)) return false;
  }
  return true;
}

function targetLevelOrder(t: AssessmentMapItem): number | null {
  const tl = t.targetLevel;
  if (!tl) return null;
  if (typeof tl.number === 'number') return tl.number;
  if (typeof tl.levelNumber === 'number') return tl.levelNumber;
  return null;
}

function templateIsPublished(t: AssessmentMapItem): boolean {
  return t.isPublished === true || t.status === 'published';
}

/** Initial onboarding templates — shown once above the level grid. */
function isInitialAssessment(t: AssessmentMapItem): boolean {
  return t.type === 'initial';
}

function assessmentMatchesLevelColumn(
  t: AssessmentMapItem,
  levelOrder: number,
  levels: Level[],
): boolean {
  if (isInitialAssessment(t)) return false;
  const tl = targetLevelOrder(t);
  if (tl != null) return tl === levelOrder;
  const minOrder = levels[0]?.order ?? 1;
  const maxOrder = levels[levels.length - 1]?.order ?? levelOrder;
  const min = t.levelRangeMin ?? minOrder;
  const max = t.levelRangeMax ?? maxOrder;
  return levelOrder >= min && levelOrder <= max;
}

const DIFFICULTY_VARIANT: Record<string, 'default' | 'primary' | 'success' | 'warning' | 'error' | 'purple' | 'orange' | 'teal'> = {
  beginner: 'success',
  intermediate: 'warning',
  advanced: 'error',
};

export default function ProgramsMapPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [programs, setPrograms] = useState<Program[]>([]);
  const [assessments, setAssessments] = useState<AssessmentMapItem[]>([]);
  const [levels, setLevels] = useState<Level[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [showPublishedOnly, setShowPublishedOnly] = useState(false);
  const [filters, setFilters] = useState<Record<(typeof FILTER_ATTR_CODES)[number], string>>({
    domain: '',
    goal: '',
    equipment: '',
    gender: '',
    place: '',
  });

  const fetchData = async () => {
    setLoading(true);
    try {
      const [programsRes, levelsRes, assessRes] = await Promise.allSettled([
        fetch('/api/programs?limit=500').then((r) => r.json()),
        fetch('/api/admin/levels').then((r) => r.json()),
        fetch('/api/admin/assessment-templates').then((r) => r.json()),
      ]);

      if (programsRes.status === 'fulfilled' && programsRes.value?.data) {
        setPrograms(Array.isArray(programsRes.value.data) ? programsRes.value.data : []);
      }
      if (levelsRes.status === 'fulfilled' && levelsRes.value?.data) {
        setLevels(
          (Array.isArray(levelsRes.value.data) ? levelsRes.value.data : []).sort(
            (a: Level, b: Level) => a.order - b.order,
          ),
        );
      }
      if (assessRes.status === 'fulfilled') {
        const raw = assessRes.value as { success?: boolean; data?: unknown };
        if (raw?.success !== false && Array.isArray(raw?.data)) {
          setAssessments(raw.data as AssessmentMapItem[]);
        }
      }
    } catch (error) {
      console.error('Error fetching programs map data:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const highlightedProgramId = searchParams.get('programId');

  const filteredPrograms = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    return programs.filter((program) => {
      if (showPublishedOnly && !program.isPublished) return false;
      if (!programPassesFilters(program, filters)) return false;
      if (!query) return true;

      return (
        program.slug.toLowerCase().includes(query) ||
        program.name.en.toLowerCase().includes(query) ||
        program.name.ar.toLowerCase().includes(query)
      );
    });
  }, [programs, searchQuery, showPublishedOnly, filters]);

  const filteredAssessments = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    return assessments.filter((t) => {
      if (showPublishedOnly && !templateIsPublished(t)) return false;
      if (!query) return true;
      return (
        (t.name?.en ?? '').toLowerCase().includes(query) ||
        (t.name?.ar ?? '').toLowerCase().includes(query)
      );
    });
  }, [assessments, searchQuery, showPublishedOnly]);

  const initialAssessments = useMemo(
    () => filteredAssessments.filter((t) => isInitialAssessment(t)),
    [filteredAssessments],
  );

  const leveledAssessments = useMemo(
    () => filteredAssessments.filter((t) => !isInitialAssessment(t)),
    [filteredAssessments],
  );

  const assessmentsByLevelColumn = useMemo(() => {
    const col: Record<string, AssessmentMapItem[]> = {};
    for (const level of levels) {
      col[level.id] = [];
    }
    leveledAssessments.forEach((t) => {
      levels.forEach((level) => {
        if (!assessmentMatchesLevelColumn(t, level.order, levels)) return;
        const list = col[level.id];
        if (list && !list.some((x) => x.id === t.id)) list.push(t);
      });
    });
    return col;
  }, [leveledAssessments, levels]);

  const programsByLevelColumn = useMemo(() => {
    const map: Record<string, Program[]> = {};
    const minOrder = levels[0]?.order ?? 1;
    const maxOrder = levels[levels.length - 1]?.order ?? levels.length;

    for (const level of levels) {
      map[level.id] = [];
    }

    filteredPrograms.forEach((program) => {
      const minLevel = program.levelRangeMin ?? minOrder;
      const maxLevel = program.levelRangeMax ?? maxOrder;

      levels.forEach((level) => {
        if (level.order >= minLevel && level.order <= maxLevel) {
          const list = map[level.id];
          if (list && !list.some((p) => p.id === program.id)) {
            list.push(program);
          }
        }
      });
    });
    return map;
  }, [filteredPrograms, levels]);

  const connectionsMap = useMemo(() => {
    const m = new Map<string, string>();
    programs.forEach((p) => {
      if (p.nextProgramId) {
        m.set(p.id, p.nextProgramId);
      }
    });
    return m;
  }, [programs]);

  const filterOptions = useMemo(() => {
    const o: Partial<Record<(typeof FILTER_ATTR_CODES)[number], { code: string; label: string }[]>> = {};
    for (const code of FILTER_ATTR_CODES) {
      o[code] = uniqueFilterOptions(programs, code);
    }
    return o;
  }, [programs]);

  const createHereHref = (level: Level) => {
    const params = new URLSearchParams();
    params.set('levelRangeMin', String(level.order));
    params.set('levelRangeMax', String(level.order));
    params.set('source', 'map');
    if (filters.domain) params.set('programDomain', filters.domain === 'pd_mobility' ? 'MOBILITY' : filters.domain === 'pd_therapeutic' ? 'THERAPEUTIC' : 'TRAINING');
    return `/admin/programs/new?${params.toString()}`;
  };

  const handleDuplicateAndEdit = async (programId: string) => {
    try {
      const res = await fetch(`/api/programs/${programId}/duplicate`, { method: 'POST' });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success && data?.data?.id) {
        toast.success('Program duplicated');
        router.push(`/admin/programs/${data.data.id}/edit`);
      } else {
        toast.error(data?.error || 'Failed to duplicate program');
      }
    } catch (error) {
      console.error('Error duplicating program from map:', error);
      toast.error('Failed to duplicate program');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-4">
          <Link
            href="/admin/programs"
            className="p-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Programs &amp; assessments map</h1>
            <p className="text-gray-600 mt-1">
              Programs and assessment templates by training level (columns). Initial assessments are listed above the grid.
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Link href="/admin/assessment-templates/new">
            <Button variant="secondary">New assessment</Button>
          </Link>
          <Link href="/admin/programs/new">
            <Button variant="secondary">New program</Button>
          </Link>
          <Button
            type="button"
            variant="outline"
            onClick={fetchData}
            icon={<RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />}
          >
            Refresh
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-wrap items-end gap-4">
            <div className="min-w-[260px] flex-1">
              <label className="mb-1 block text-sm font-medium text-gray-700">Search in map</label>
              <Input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search by name or slug..."
              />
            </div>

            {FILTER_ATTR_CODES.map((code) => (
              <div key={code} className="min-w-[140px]">
                <label className="mb-1 block text-xs font-medium text-gray-600 capitalize">{code}</label>
                <select
                  className="w-full rounded-md border border-gray-300 px-2 py-2 text-sm"
                  value={filters[code]}
                  onChange={(e) => setFilters((f) => ({ ...f, [code]: e.target.value }))}
                >
                  <option value="">All</option>
                  {(filterOptions[code] ?? []).map((opt) => (
                    <option key={opt.code} value={opt.code}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
            ))}

            <label className="flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={showPublishedOnly}
                onChange={(e) => setShowPublishedOnly(e.target.checked)}
                className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              Published only
            </label>

            {searchQuery || showPublishedOnly || Object.values(filters).some(Boolean) ? (
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setSearchQuery('');
                  setShowPublishedOnly(false);
                  setFilters({ domain: '', goal: '', equipment: '', gender: '', place: '' });
                }}
              >
                Reset
              </Button>
            ) : null}
          </div>
        </CardContent>
      </Card>

      {loading ? (
        <Card className="animate-pulse">
          <CardContent className="pt-6">
            <div className="h-96 bg-gray-100 rounded" />
          </CardContent>
        </Card>
      ) : levels.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-gray-500 text-center py-12">No levels defined. Create levels first to see the map.</p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="p-0">
            {initialAssessments.length > 0 ? (
              <div className="border-b border-amber-100 bg-amber-50/50 px-3 py-3">
                <p className="text-xs font-semibold text-amber-900 mb-2">Initial assessments (onboarding)</p>
                <div className="flex flex-wrap gap-2">
                  {initialAssessments.map((t) => (
                    <Link
                      key={t.id}
                      href={`/admin/assessment-templates/${t.id}/edit`}
                      className="inline-flex flex-col rounded-lg border border-amber-200 bg-white px-2.5 py-2 text-left hover:border-amber-400 hover:shadow-sm min-w-[140px]"
                    >
                      <span className="text-xs font-medium text-gray-900 line-clamp-2">
                        {t.name?.en || t.type}
                      </span>
                      <span className="text-[10px] text-gray-500 mt-0.5">
                        {t._count?.exercises ?? 0} exercises
                        {!templateIsPublished(t) ? ' · draft' : ''}
                      </span>
                    </Link>
                  ))}
                </div>
              </div>
            ) : null}
            <div className="overflow-x-auto">
              <div className="min-w-[720px]">
                <div className="flex border-b border-gray-200 bg-gray-50">
                  {levels.map((level) => (
                    <div
                      key={level.id}
                      className="flex-1 min-w-[200px] px-3 py-3 text-center border-r border-gray-100 last:border-r-0"
                    >
                      <div className="flex items-center justify-center gap-1.5">
                        <div
                          className="h-2.5 w-2.5 rounded-full"
                          style={{ backgroundColor: level.color || '#6B7280' }}
                        />
                        <span className="text-xs font-semibold text-gray-700">
                          {level.name?.en || `Level ${level.order}`}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="flex border-b border-gray-100">
                  {levels.map((level) => {
                    const cellPrograms = programsByLevelColumn[level.id] || [];
                    return (
                      <div
                        key={level.id}
                        className="flex-1 min-w-[200px] px-2 py-2 border-r border-gray-50 last:border-r-0 align-top"
                      >
                        <div className="space-y-2">
                          <Link
                            href={createHereHref(level)}
                            className="inline-flex items-center rounded-md border border-dashed border-gray-300 px-2 py-1 text-[11px] font-medium text-gray-600 hover:border-blue-300 hover:text-blue-700"
                          >
                            Create here
                          </Link>
                          {cellPrograms.map((program) => {
                            const type = mapProgramDomainToCellType(program);
                            const typeColors = TYPE_COLORS[type] ?? TYPE_COLORS.training;
                            const hasNext = connectionsMap.has(program.id);
                            const badges = (program.programAttributes ?? [])
                              .filter((r) => r.mode === 'REQUIRED' || r.mode === 'OPTIONAL')
                              .slice(0, 4)
                              .map((r) => r.attributeValue?.code)
                              .filter(Boolean) as string[];
                            return (
                              <div key={program.id} className="relative">
                                <div
                                  className={`rounded-lg border ${typeColors.border} ${typeColors.bg} ${
                                    highlightedProgramId === program.id
                                      ? 'ring-2 ring-blue-300 border-blue-400'
                                      : ''
                                  }`}
                                >
                                  <Link
                                    href={`/admin/programs/${program.id}/edit`}
                                    className="block p-2.5 hover:shadow-md transition-all group rounded-t-lg"
                                  >
                                    <p className="text-xs font-semibold text-gray-900 leading-snug group-hover:text-blue-600 transition-colors">
                                      {program.name?.en || program.slug}
                                    </p>
                                    <div className="flex flex-wrap gap-1 mt-1.5">
                                      {badges.map((b) => (
                                        <span
                                          key={b}
                                          className="text-[9px] px-1 py-0.5 rounded bg-white/80 text-gray-700 border border-gray-200"
                                        >
                                          {b}
                                        </span>
                                      ))}
                                    </div>
                                    <div className="flex items-center gap-1.5 mt-1.5">
                                      <Badge
                                        variant={DIFFICULTY_VARIANT[program.difficulty] || 'default'}
                                        size="sm"
                                      >
                                        {program.difficulty}
                                      </Badge>
                                      <span className="text-[10px] text-gray-500">{program.durationWeeks}w</span>
                                      {!program.isPublished ? (
                                        <span className="text-[10px] text-amber-700">draft</span>
                                      ) : null}
                                    </div>
                                  </Link>
                                  <div className="flex items-center justify-between border-t border-white/70 px-2.5 py-2">
                                    <Link
                                      href={`/admin/programs/${program.id}/edit`}
                                      className="text-[11px] font-medium text-blue-700 hover:text-blue-800"
                                    >
                                      Edit
                                    </Link>
                                    <button
                                      type="button"
                                      onClick={() => handleDuplicateAndEdit(program.id)}
                                      className="text-[11px] font-medium text-gray-600 hover:text-gray-900"
                                    >
                                      Duplicate &amp; Edit
                                    </button>
                                  </div>
                                </div>
                                {hasNext && (
                                  <div className="absolute -right-3 top-1/2 -translate-y-1/2 z-10">
                                    <div className="h-5 w-5 rounded-full bg-white border-2 border-gray-300 flex items-center justify-center shadow-sm">
                                      <ArrowRight className="h-3 w-3 text-gray-500" />
                                    </div>
                                  </div>
                                )}
                              </div>
                            );
                          })}
                          {cellPrograms.length === 0 && (
                            <div className="h-12 rounded-lg border border-dashed border-gray-200 flex items-center justify-center">
                              <span className="text-[10px] text-gray-300">—</span>
                            </div>
                          )}
                          {(assessmentsByLevelColumn[level.id] ?? []).length > 0 ? (
                            <div className="pt-3 mt-2 border-t border-gray-100">
                              <p className="text-[10px] font-semibold text-gray-500 uppercase tracking-wide mb-1.5">
                                Assessments
                              </p>
                              <div className="space-y-1.5">
                                {(assessmentsByLevelColumn[level.id] ?? []).map((t) => (
                                  <Link
                                    key={t.id}
                                    href={`/admin/assessment-templates/${t.id}/edit`}
                                    className="block rounded-md border border-teal-100 bg-teal-50/60 px-2 py-1.5 hover:border-teal-300 hover:bg-teal-50"
                                  >
                                    <span className="text-[11px] font-medium text-gray-900 line-clamp-2">
                                      {t.name?.en || t.type}
                                    </span>
                                    <div className="flex items-center gap-1 mt-0.5">
                                      <span className="text-[9px] text-teal-800 bg-white/80 px-1 rounded border border-teal-100">
                                        {t.type}
                                      </span>
                                      {!templateIsPublished(t) ? (
                                        <span className="text-[9px] text-amber-700">draft</span>
                                      ) : null}
                                    </div>
                                  </Link>
                                ))}
                              </div>
                            </div>
                          ) : null}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {!loading && (programs.length > 0 || assessments.length > 0) && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-gray-500">Visible Programs</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">{filteredPrograms.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-gray-500">Published Visible</p>
              <p className="text-2xl font-bold text-green-600 mt-1">
                {filteredPrograms.filter((p) => p.isPublished).length}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-gray-500">Levels</p>
              <p className="text-2xl font-bold text-blue-600 mt-1">{levels.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-gray-500">With Sequences</p>
              <p className="text-2xl font-bold text-purple-600 mt-1">{connectionsMap.size}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-gray-500">Visible assessments</p>
              <p className="text-2xl font-bold text-teal-700 mt-1">{filteredAssessments.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-gray-500">Initial (onboarding)</p>
              <p className="text-2xl font-bold text-amber-700 mt-1">{initialAssessments.length}</p>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
