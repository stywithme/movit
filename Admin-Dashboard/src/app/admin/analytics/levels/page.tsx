'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { ChartCard, DonutChart, HorizontalBars, StatCard } from '@/components/charts';
import { analyticsService } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';

interface LevelRow {
  levelNumber: number;
  code: string;
  name: Record<string, string> | string;
  color?: string;
  userCount: number;
}

interface TransitionRow {
  fromLevel: number;
  toLevel: number;
  count: number;
  avgDays: number;
  minDays: number;
  maxDays: number;
}

function levelName(row: LevelRow) {
  return typeof row.name === 'string' ? row.name : row.name?.en || row.name?.ar || `Level ${row.levelNumber}`;
}

export default function LevelsAnalyticsPage() {
  const [levels, setLevels] = useState<LevelRow[]>([]);
  const [transitions, setTransitions] = useState<TransitionRow[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [levelData, transitionData] = await Promise.all([
        analyticsService.levels(),
        analyticsService.levelTransitions(),
      ]);
      setLevels(Array.isArray(levelData) ? levelData : []);
      setTransitions(Array.isArray(transitionData?.transitions) ? transitionData.transitions : []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<TransitionRow>[] = [
    { key: 'from', header: 'From', cell: (row) => `Level ${row.fromLevel}` },
    { key: 'to', header: 'To', cell: (row) => `Level ${row.toLevel}` },
    { key: 'count', header: 'Count', cell: (row) => formatNumber(row.count) },
    { key: 'avg', header: 'Avg Days', cell: (row) => row.avgDays.toFixed(1) },
    { key: 'range', header: 'Range', cell: (row) => `${row.minDays}-${row.maxDays} days` },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Level Analytics" description="User distribution across levels and transition speed." />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <StatCard title="Assigned Users" value={formatNumber(levels.reduce((sum, level) => sum + level.userCount, 0))} />
        <StatCard title="Active Levels" value={formatNumber(levels.filter((level) => level.userCount > 0).length)} />
        <StatCard title="Transitions" value={formatNumber(transitions.reduce((sum, row) => sum + row.count, 0))} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Level Distribution" loading={loading} empty={!levels.length}>
          <DonutChart data={levels.map((level) => ({ name: levelName(level), value: level.userCount }))} />
        </ChartCard>
        <ChartCard title="Users per Level" loading={loading} empty={!levels.length}>
          <HorizontalBars data={levels.map((level) => ({ name: levelName(level), value: level.userCount, color: level.color }))} />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={transitions}
        getRowKey={(row) => `${row.fromLevel}-${row.toLevel}`}
        loading={loading}
        emptyTitle="No level transitions"
        emptyDescription="Users need at least two level profiles to calculate transitions."
      />
    </div>
  );
}
