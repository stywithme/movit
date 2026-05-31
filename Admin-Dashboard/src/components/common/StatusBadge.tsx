import { Badge, type BadgeProps } from '@/components/ui/Badge';

type StatusTone = NonNullable<BadgeProps['variant']>;

const statusToneMap: Record<string, StatusTone> = {
  active: 'success',
  published: 'success',
  completed: 'success',
  approved: 'success',
  enabled: 'success',
  pending: 'warning',
  draft: 'warning',
  inactive: 'secondary',
  archived: 'secondary',
  cancelled: 'error',
  canceled: 'error',
  rejected: 'error',
  failed: 'error',
};

export function StatusBadge({ status, label }: { status?: string | null; label?: string }) {
  const normalized = (status || 'unknown').toLowerCase();
  const variant = statusToneMap[normalized] || 'outline';

  return (
    <Badge variant={variant} className="capitalize">
      {label || normalized.replace(/_/g, ' ')}
    </Badge>
  );
}
