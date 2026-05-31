export function formatNumber(value: number | null | undefined) {
  return new Intl.NumberFormat('en-US').format(value ?? 0);
}

export function formatCurrency(value: number | null | undefined, currency = 'SAR') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(value ?? 0);
}

export function formatPercent(value: number | null | undefined) {
  return `${(value ?? 0).toFixed(1)}%`;
}

export function metricValue(metric?: { value: number }) {
  return metric?.value ?? 0;
}

export function metricDelta(metric?: { delta: number }) {
  return metric?.delta ?? 0;
}
