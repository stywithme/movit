import { Button } from '@/components/ui/Button';

export interface PaginationMeta {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

interface PaginationProps {
  pagination?: PaginationMeta | null;
  onPageChange: (page: number) => void;
  disabled?: boolean;
}

export function Pagination({ pagination, onPageChange, disabled }: PaginationProps) {
  if (!pagination || pagination.totalPages <= 1) return null;

  const start = (pagination.page - 1) * pagination.limit + 1;
  const end = Math.min(pagination.page * pagination.limit, pagination.total);

  return (
    <div className="flex flex-col gap-3 border-t px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-muted-foreground">
        {start}-{end} of {pagination.total}
      </p>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || pagination.page <= 1}
          onClick={() => onPageChange(pagination.page - 1)}
        >
          Previous
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || pagination.page >= pagination.totalPages}
          onClick={() => onPageChange(pagination.page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
