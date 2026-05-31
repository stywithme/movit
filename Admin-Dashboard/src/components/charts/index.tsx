'use client';

import type { ReactNode } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ArrowDownRight, ArrowUpRight, Info, Minus } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Tooltip as HelpTooltip } from '@/components/ui/Tooltip';
import { cn } from '@/lib/utils';

export interface ChartDatum {
  name?: string;
  date?: string;
  value: number;
  [key: string]: string | number | null | undefined;
}

const chartColors = [
  '#2563eb',
  '#16a34a',
  '#f97316',
  '#8b5cf6',
  '#ef4444',
  '#06b6d4',
  '#eab308',
];

const axisColor = '#64748b';
const gridColor = '#e2e8f0';

function HelpIcon({ content }: { content?: ReactNode }) {
  if (!content) return null;
  return (
    <HelpTooltip content={content} side="top">
      <button
        type="button"
        className="inline-flex size-5 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        aria-label="Metric explanation"
      >
        <Info className="size-3.5" />
      </button>
    </HelpTooltip>
  );
}

interface ChartCardProps {
  title: string;
  description?: string;
  loading?: boolean;
  empty?: boolean;
  children: ReactNode;
  className?: string;
  help?: ReactNode;
}

export function ChartCard({ title, description, loading, empty, children, className, help }: ChartCardProps) {
  return (
    <Card className={className}>
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-base">{title}</CardTitle>
            {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
          </div>
          <HelpIcon content={help} />
        </div>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="h-72 animate-pulse rounded-lg bg-muted" />
        ) : empty ? (
          <div className="flex h-72 items-center justify-center text-sm text-muted-foreground">No data available</div>
        ) : (
          children
        )}
      </CardContent>
    </Card>
  );
}

interface StatCardProps {
  title: string;
  value: string | number;
  delta?: number;
  description?: string;
  icon?: ReactNode;
  help?: ReactNode;
}

export function StatCard({ title, value, delta, description, icon, help }: StatCardProps) {
  const positive = (delta ?? 0) > 0;
  const negative = (delta ?? 0) < 0;

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-1.5">
            <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
            <HelpIcon content={help} />
          </div>
          {icon && <div className="flex size-9 items-center justify-center rounded-lg bg-blue-50 text-blue-600">{icon}</div>}
        </div>
      </CardHeader>
      <CardContent>
        <p className="text-3xl font-bold">{value}</p>
        <div className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
          {delta == null ? (
            <Minus className="size-4" />
          ) : positive ? (
            <ArrowUpRight className="size-4 text-success" />
          ) : negative ? (
            <ArrowDownRight className="size-4 text-destructive" />
          ) : (
            <Minus className="size-4" />
          )}
          {delta != null && (
            <span className={cn(positive && 'text-success', negative && 'text-destructive')}>
              {delta > 0 ? '+' : ''}
              {delta.toFixed(1)}%
            </span>
          )}
          {description && <span>{description}</span>}
        </div>
      </CardContent>
    </Card>
  );
}

export function LineTrend({ data, dataKey = 'value' }: { data: ChartDatum[]; dataKey?: string }) {
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
          <XAxis dataKey="date" tickLine={false} axisLine={false} fontSize={12} stroke={axisColor} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} stroke={axisColor} />
          <Tooltip />
          <Line type="monotone" dataKey={dataKey} stroke={chartColors[0]} strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function AreaTrend({ data, dataKey = 'value' }: { data: ChartDatum[]; dataKey?: string }) {
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
          <XAxis dataKey="date" tickLine={false} axisLine={false} fontSize={12} stroke={axisColor} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} stroke={axisColor} />
          <Tooltip />
          <Area type="monotone" dataKey={dataKey} stroke={chartColors[1]} fill={chartColors[1]} fillOpacity={0.18} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function BarsChart({ data, dataKey = 'value', nameKey = 'name' }: { data: ChartDatum[]; dataKey?: string; nameKey?: string }) {
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
          <XAxis dataKey={nameKey} tickLine={false} axisLine={false} fontSize={12} stroke={axisColor} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} stroke={axisColor} />
          <Tooltip />
          <Bar dataKey={dataKey} radius={[6, 6, 0, 0]}>
            {data.map((_, index) => (
              <Cell key={index} fill={chartColors[index % chartColors.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function DonutChart({ data }: { data: ChartDatum[] }) {
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="name" innerRadius={60} outerRadius={95} paddingAngle={2}>
            {data.map((_, index) => (
              <Cell key={index} fill={chartColors[index % chartColors.length]} />
            ))}
          </Pie>
          <Tooltip />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

export function FunnelChart({ data }: { data: Array<{ name: string; value: number; conversion?: number }> }) {
  const max = Math.max(...data.map((item) => item.value), 1);
  return (
    <div className="space-y-3">
      {data.map((item, index) => (
        <div key={item.name} className="space-y-1">
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium">{item.name}</span>
            <span className="text-muted-foreground">
              {item.value} {item.conversion != null ? `(${item.conversion}%)` : ''}
            </span>
          </div>
          <div className="h-7 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full"
              style={{ width: `${Math.max((item.value / max) * 100, 2)}%`, backgroundColor: chartColors[index % chartColors.length] }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

export function CohortHeatmap({ data }: { data: Array<Record<string, string | number>> }) {
  const columns = ['day1', 'day2', 'week1', 'week4'];
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-sm">
        <thead>
          <tr className="border-b">
            <th className="py-2 text-left">Cohort</th>
            <th className="py-2 text-right">Users</th>
            {columns.map((column) => (
              <th key={column} className="py-2 text-right capitalize">{column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr key={String(row.cohort)} className="border-b last:border-b-0">
              <td className="py-2 font-medium">{row.cohort}</td>
              <td className="py-2 text-right">{row.users}</td>
              {columns.map((column) => {
                const value = Number(row[column] ?? 0);
                return (
                  <td key={column} className="py-2 text-right">
                    <span
                      className="inline-flex min-w-16 justify-center rounded-md px-2 py-1 font-medium"
                      style={{ backgroundColor: `rgba(37, 99, 235, ${Math.max(value / 100, 0.1)})` }}
                    >
                      {value.toFixed(1)}%
                    </span>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function HorizontalBars({ data }: { data: Array<{ name: string; value: number; color?: string }> }) {
  const max = Math.max(...data.map((item) => item.value), 1);
  return (
    <div className="space-y-3">
      {data.map((item, index) => (
        <div key={`${item.name}-${index}`} className="flex items-center gap-3">
          <span className="w-36 truncate text-sm font-medium">{item.name}</span>
          <div className="h-6 flex-1 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full"
              style={{ width: `${Math.max((item.value / max) * 100, 2)}%`, backgroundColor: item.color || chartColors[index % chartColors.length] }}
            />
          </div>
          <span className="w-14 text-right text-sm font-semibold">{item.value}</span>
        </div>
      ))}
    </div>
  );
}
