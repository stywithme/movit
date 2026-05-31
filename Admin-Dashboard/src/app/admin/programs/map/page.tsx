'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { toast } from 'sonner';
import { Card, CardContent, Button } from '@/components/ui';
import { FilterBar, PageHeader } from '@/components/common';
import { RefreshCw, ArrowRight } from 'lucide-react';
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

function formatProgramLevelRangeLabel(program: Program): string {
  const min = program.levelRangeMin;
  const max = program.levelRangeMax;
  if (min != null && max != null) {
    return min === max ? `L${min}` : `L${min}–L${max}`;
  }
  if (min != null) return `L${min}`;
  if (max != null) return `L${max}`;
  return '—';
}

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
      <PageHeader
        title="Programs & assessments map"
        description="Programs and assessment templates by training level. Initial assessments are listed above the grid."
        breadcrumbs={[
          { label: 'Programs', href: '/admin/programs' },
          { label: 'Map' },
        ]}
        actions={
          <>
            <Button asChild variant="secondary">
              <Link href="/admin/assessment-templates/new">New assessment</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href="/admin/programs/new">New program</Link>
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={fetchData}
              icon={<RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />}
            >
              Refresh
            </Button>
          </>
        }
      />

      <FilterBar
        searchValue={searchQuery}
        searchPlaceholder="Search by name or slug..."
        onSearchChange={setSearchQuery}
        selects={FILTER_ATTR_CODES.map((code) => ({
          id: code,
          value: filters[code],
          onChange: (value) => setFilters((f) => ({ ...f, [code]: value })),
          options: [
            { value: '', label: `All ${code}` },
            ...(filterOptions[code] ?? []).map((opt) => ({ value: opt.code, label: opt.label })),
          ],
          className: 'lg:w-40',
        }))}
        onReset={() => {
          setSearchQuery('');
          setShowPublishedOnly(false);
          setFilters({ domain: '', goal: '', equipment: '', gender: '', place: '' });
        }}
      >
        <label className="flex items-center gap-2 rounded-md border bg-background px-3 py-2 text-sm">
          <input
            type="checkbox"
            checked={showPublishedOnly}
            onChange={(e) => setShowPublishedOnly(e.target.checked)}
            className="size-4 rounded border-input accent-primary"
          />
          Published only
        </label>
      </FilterBar>

      {loading ? (
        <Card className="animate-pulse">
          <CardContent className="pt-6">
            <div className="h-96 rounded bg-muted" />
          </CardContent>
        </Card>
      ) : levels.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="py-12 text-center text-muted-foreground">No levels defined. Create levels first to see the map.</p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="p-0">
            {initialAssessments.length > 0 ? (
              <div className="border-b border-amber-200/70 bg-amber-50/50 px-3 py-3">
                <p className="mb-2 text-xs font-semibold text-amber-900">Initial assessments (onboarding)</p>
                <div className="flex flex-wrap gap-2">
                  {initialAssessments.map((t) => (
                    <Link
                      key={t.id}
                      href={`/admin/assessment-templates/${t.id}/edit`}
                      className="inline-flex min-w-[140px] flex-col rounded-lg border border-amber-200 bg-card px-2.5 py-2 text-left hover:border-amber-400 hover:shadow-sm"
                    >
                      <span className="line-clamp-2 text-xs font-medium">
                        {t.name?.en || t.type}
                      </span>
                      <span className="mt-0.5 text-[10px] text-muted-foreground">
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
                <div className="flex border-b bg-muted/50">
                  {levels.map((level) => (
                    <div
                      key={level.id}
                      className="min-w-[200px] flex-1 border-r px-3 py-3 text-center last:border-r-0"
                    >
                      <div className="flex items-center justify-center gap-1.5">
                        <div
                          className="h-2.5 w-2.5 rounded-full"
                          style={{ backgroundColor: level.color || '#6B7280' }}
                        />
                        <span className="text-xs font-semibold text-muted-foreground">
                          {level.name?.en || `Level ${level.order}`}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="flex border-b">
                  {levels.map((level) => {
                    const cellPrograms = programsByLevelColumn[level.id] || [];
                    return (
                      <div
                        key={level.id}
                        className="min-w-[200px] flex-1 border-r px-2 py-2 align-top last:border-r-0"
                      >
                        <div className="space-y-2">
                          <Link
                            href={createHereHref(level)}
                            className="inline-flex items-center rounded-md border border-dashed px-2 py-1 text-[11px] font-medium text-muted-foreground hover:border-primary/50 hover:text-primary"
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
                                      ? 'border-primary ring-2 ring-primary/30'
                                      : ''
                                  }`}
                                >
                                  <Link
                                    href={`/admin/programs/${program.id}/edit`}
                                    className="group block rounded-t-lg p-2.5 transition-all hover:shadow-md"
                                  >
                                    <p className="text-xs font-semibold leading-snug transition-colors group-hover:text-primary">
                                      {program.name?.en || program.slug}
                                    </p>
                                    <div className="flex flex-wrap gap-1 mt-1.5">
                                      {badges.map((b) => (
                                        <span
                                          key={b}
                                          className="rounded border bg-background/80 px-1 py-0.5 text-[9px] text-muted-foreground"
                                        >
                                          {b}
                                        </span>
                                      ))}
                                    </div>
                                    <div className="flex items-center gap-1.5 mt-1.5">
                                      <span className="rounded border bg-background/80 px-1 py-0.5 text-[10px] font-medium text-muted-foreground">
                                        {formatProgramLevelRangeLabel(program)}
                                      </span>
                                      <span className="text-[10px] text-muted-foreground">{program.durationWeeks}w</span>
                                      {!program.isPublished ? (
                                        <span className="text-[10px] text-amber-700">draft</span>
                                      ) : null}
                                    </div>
                                  </Link>
                                  <div className="flex items-center justify-between border-t border-background/70 px-2.5 py-2">
                                    <Link
                                      href={`/admin/programs/${program.id}/edit`}
                                      className="text-[11px] font-medium text-primary hover:text-primary/80"
                                    >
                                      Edit
                                    </Link>
                                    <button
                                      type="button"
                                      onClick={() => handleDuplicateAndEdit(program.id)}
                                      className="text-[11px] font-medium text-muted-foreground hover:text-foreground"
                                    >
                                      Duplicate &amp; Edit
                                    </button>
                                  </div>
                                </div>
                                {hasNext && (
                                  <div className="absolute -right-3 top-1/2 -translate-y-1/2 z-10">
                                    <div className="flex size-5 items-center justify-center rounded-full border-2 bg-card shadow-sm">
                                      <ArrowRight className="size-3 text-muted-foreground" />
                                    </div>
                                  </div>
                                )}
                              </div>
                            );
                          })}
                          {cellPrograms.length === 0 && (
                            <div className="flex h-12 items-center justify-center rounded-lg border border-dashed">
                              <span className="text-[10px] text-muted-foreground">—</span>
                            </div>
                          )}
                          {(assessmentsByLevelColumn[level.id] ?? []).length > 0 ? (
                            <div className="mt-2 border-t pt-3">
                              <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                                Assessments
                              </p>
                              <div className="space-y-1.5">
                                {(assessmentsByLevelColumn[level.id] ?? []).map((t) => (
                                  <Link
                                    key={t.id}
                                    href={`/admin/assessment-templates/${t.id}/edit`}
                                    className="block rounded-md border border-teal-100 bg-teal-50/60 px-2 py-1.5 hover:border-teal-300 hover:bg-teal-50"
                                  >
                                    <span className="line-clamp-2 text-[11px] font-medium">
                                      {t.name?.en || t.type}
                                    </span>
                                    <div className="flex items-center gap-1 mt-0.5">
                                      <span className="rounded border border-teal-100 bg-background/80 px-1 text-[9px] text-teal-800">
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
              <p className="text-sm text-muted-foreground">Visible Programs</p>
              <p className="mt-1 text-2xl font-bold">{filteredPrograms.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-muted-foreground">Published Visible</p>
              <p className="mt-1 text-2xl font-bold text-success">
                {filteredPrograms.filter((p) => p.isPublished).length}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-muted-foreground">Levels</p>
              <p className="mt-1 text-2xl font-bold text-primary">{levels.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-muted-foreground">With Sequences</p>
              <p className="mt-1 text-2xl font-bold text-violet-600">{connectionsMap.size}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-muted-foreground">Visible assessments</p>
              <p className="mt-1 text-2xl font-bold text-teal-700">{filteredAssessments.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 text-center">
              <p className="text-sm text-muted-foreground">Initial (onboarding)</p>
              <p className="mt-1 text-2xl font-bold text-amber-700">{initialAssessments.length}</p>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
