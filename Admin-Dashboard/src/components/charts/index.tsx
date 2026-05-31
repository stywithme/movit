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
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn } from '@/lib/utils';

export interface ChartDatum {
  name?: string;
  date?: string;
  value: number;
  [key: string]: string | number | null | undefined;
}

const chartColors = [
  'hsl(var(--chart-1))',
  'hsl(var(--chart-2))',
  'hsl(var(--chart-3))',
  'hsl(var(--chart-4))',
  'hsl(var(--chart-5))',
];

interface ChartCardProps {
  title: string;
  description?: string;
  loading?: boolean;
  empty?: boolean;
  children: ReactNode;
  className?: string;
}

export function ChartCard({ title, description, loading, empty, children, className }: ChartCardProps) {
  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
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
}

export function StatCard({ title, value, delta, description, icon }: StatCardProps) {
  const positive = (delta ?? 0) > 0;
  const negative = (delta ?? 0) < 0;

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-4">
          <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
          {icon && <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">{icon}</div>}
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
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
          <XAxis dataKey="date" tickLine={false} axisLine={false} fontSize={12} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} />
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
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
          <XAxis dataKey="date" tickLine={false} axisLine={false} fontSize={12} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} />
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
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
          <XAxis dataKey={nameKey} tickLine={false} axisLine={false} fontSize={12} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} />
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
                      style={{ backgroundColor: `hsl(var(--chart-1) / ${Math.max(value / 100, 0.08)})` }}
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
