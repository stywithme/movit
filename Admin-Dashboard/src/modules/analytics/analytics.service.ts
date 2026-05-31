import { api } from '@/lib/api/client';

export interface MetricDelta {
  value: number;
  previous: number;
  delta: number;
}

export interface ChartPoint {
  date?: string;
  name?: string;
  value: number;
  [key: string]: string | number | null | undefined;
}

export interface FunnelStep {
  name: string;
  value: number;
  conversion?: number;
  dropoff?: number;
}

export interface OverviewAnalytics {
  kpis: Record<string, MetricDelta>;
  northStar: { completed: number; eligibleUsers: number; rate: number; label: string };
  activationFunnel: FunnelStep[];
  trends: { sessions: ChartPoint[]; revenue: ChartPoint[] };
  distributions: { levels: Array<{ name: string; value: number; color?: string }>; sessionContexts: ChartPoint[] };
  safety: { dangerRepRate: number; abandonedReports: number };
  alerts: { pendingReassessments: number; failedCheckouts: number; abandonedReports: number };
}

export interface UsersGrowthAnalytics {
  total: number;
  trend: ChartPoint[];
  cumulative: ChartPoint[];
  status: ChartPoint[];
  trainingGoals: ChartPoint[];
  demographics: {
    ageBuckets: ChartPoint[];
    availableDays: ChartPoint[];
    trainingLocations: ChartPoint[];
    avgHeightCm: number;
    avgWeightKg: number;
  };
}

export interface ActivationAnalytics {
  funnel: FunnelStep[];
  timeToFirstSession: { avgHours: number; usersWithFirstSession: number; buckets: ChartPoint[] };
  northStar: OverviewAnalytics['northStar'];
}

export interface RetentionAnalytics {
  activeUsers: number;
  stickiness: { dau: number; wau: number; mau: number };
  sessionsPerActiveUser: number;
  cohorts: Array<Record<string, string | number>>;
  churnSignals: Record<string, number>;
}

export interface TrainingAnalytics {
  volume: Record<string, number>;
  contexts: ChartPoint[];
  trends: { sessions: ChartPoint[]; formScore: ChartPoint[] };
  averages: Record<string, number>;
  exercises: Array<{ exerciseId: string; name: string; sessions: number; dangerRepRate: number }>;
}

export interface ProgressionAnalytics {
  changes: number;
  activeRules: number;
  trend: ChartPoint[];
  fields: ChartPoint[];
  axes: ChartPoint[];
  decisions: ChartPoint[];
  streaks: { avgSuccess: number; avgRegression: number; byAxis: ChartPoint[] };
  topRules: ChartPoint[];
}

export interface RevenueAnalytics {
  summary: Record<string, number>;
  revenueTrend: ChartPoint[];
  subscriptionsByStatus: ChartPoint[];
  subscriptionsByPlan: ChartPoint[];
  checkoutFunnel: FunnelStep[];
  plans: Array<{ id: string; name: string; isActive: boolean; monthlyPrice: number; yearlyPrice: number; currency: string }>;
}

export interface BookingAnalytics {
  summary: Record<string, number>;
  trend: ChartPoint[];
  statuses: ChartPoint[];
  paymentStatuses: ChartPoint[];
  doctors: Array<{ name: string; value: number; reports: number }>;
}

export interface SafetyAnalytics {
  summary: Record<string, number>;
  trends: { dangerReps: ChartPoint[]; abandoned: ChartPoint[]; safetyScore: ChartPoint[] };
  riskyExercises: Array<{ name: string; danger: number; total: number; dangerRate: number }>;
  injuries: ChartPoint[];
}

export interface ContentAnalytics {
  exercises: { total: number; byStatus: ChartPoint[]; byCategory: ChartPoint[]; familyCoverage: ChartPoint[] };
  workouts: { total: number; byStatus: ChartPoint[]; byDifficulty: ChartPoint[] };
  programs: { total: number; published: number; byType: ChartPoint[] };
  messages: { total: number; active: number; byCategory: ChartPoint[] };
  cameraPositions: { total: number; active: number };
  mostUsedExercises: Array<{ exerciseId: string; name: string; sessions: number }>;
}

export type AnalyticsParams = Record<string, string | number | boolean | null | undefined>;

const getData = async <T>(path: string, params?: AnalyticsParams) => {
  const response = await api.get<T>(path, { params });
  return response.data;
};

export const analyticsService = {
  overview: (params?: AnalyticsParams) => getData<OverviewAnalytics>('/admin/analytics/overview', params),
  users: (params?: AnalyticsParams) => getData<UsersGrowthAnalytics>('/admin/analytics/users', params),
  activation: (params?: AnalyticsParams) => getData<ActivationAnalytics>('/admin/analytics/activation', params),
  retention: (params?: AnalyticsParams) => getData<RetentionAnalytics>('/admin/analytics/retention', params),
  training: (params?: AnalyticsParams) => getData<TrainingAnalytics>('/admin/analytics/training', params),
  progression: (params?: AnalyticsParams) => getData<ProgressionAnalytics>('/admin/analytics/progression', params),
  revenue: (params?: AnalyticsParams) => getData<RevenueAnalytics>('/admin/analytics/revenue', params),
  bookings: (params?: AnalyticsParams) => getData<BookingAnalytics>('/admin/analytics/bookings', params),
  safety: (params?: AnalyticsParams) => getData<SafetyAnalytics>('/admin/analytics/safety', params),
  content: (params?: AnalyticsParams) => getData<ContentAnalytics>('/admin/analytics/content', params),
  programs: (params?: AnalyticsParams) => getData<any[]>('/admin/analytics/programs', params),
  programDetail: (id: string, params?: AnalyticsParams) => getData<any>(`/admin/analytics/programs/${id}`, params),
  levels: (params?: AnalyticsParams) => getData<any[]>('/admin/analytics/levels', params),
  levelTransitions: (params?: AnalyticsParams) => getData<any>('/admin/analytics/level-transitions', params),
  assessments: (params?: AnalyticsParams) => getData<any>('/admin/analytics/assessments', params),
  userReport: (id: string) => getData<any>(`/admin/analytics/users/${id}/report`),
  sessionReport: (id: string) => getData<any>(`/admin/analytics/sessions/${id}/report`),
};
