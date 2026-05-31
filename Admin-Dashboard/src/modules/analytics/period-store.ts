'use client';

import { create } from 'zustand';

export type AnalyticsPeriod = '7d' | '30d' | '90d' | 'all' | 'custom';

interface AnalyticsPeriodState {
  period: AnalyticsPeriod;
  from: string;
  to: string;
  setPeriod: (period: AnalyticsPeriod) => void;
  setCustomRange: (from: string, to: string) => void;
  params: () => Record<string, string | undefined>;
}

export const useAnalyticsPeriod = create<AnalyticsPeriodState>((set, get) => ({
  period: '30d',
  from: '',
  to: '',
  setPeriod: (period) => set({ period }),
  setCustomRange: (from, to) => set({ from, to, period: 'custom' }),
  params: () => {
    const { period, from, to } = get();
    return period === 'custom' ? { period, from, to } : { period };
  },
}));
