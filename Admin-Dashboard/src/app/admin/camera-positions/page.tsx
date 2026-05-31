'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Camera } from 'lucide-react';
import { toast } from 'sonner';
import { LocalizedText } from '@/lib/types/localized';
import { Button } from '@/components/ui';
import { DataTable, PageHeader, StatusBadge, type DataTableColumn } from '@/components/common';

interface CameraPosition {
  id: string;
  code: string;
  name: LocalizedText;
  imageUrl: string | null;
  isActive: boolean;
  sortOrder: number;
}

export default function CameraPositionsListPage() {
  const [cameraPositions, setCameraPositions] = useState<CameraPosition[]>([]);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);

  const fetchCameraPositions = async () => {
    setLoading(true);
    setPageError(null);
    try {
      const res = await fetch('/api/camera-positions?includeInactive=true');
      const data = await res.json();

      if (data.success) {
        setCameraPositions(data.data);
      } else {
        const message = data.error || 'Failed to load camera positions';
        setPageError(message);
        toast.error(message);
      }
    } catch (error) {
      console.error('Error fetching camera positions:', error);
      setPageError('Failed to load camera positions');
      toast.error('Failed to load camera positions');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCameraPositions();
  }, []);

  const handleToggleActive = async (id: string, currentStatus: boolean) => {
    try {
      const res = await fetch(`/api/camera-positions/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive: !currentStatus }),
      });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success !== false) {
        toast.success(currentStatus ? 'Camera position deactivated' : 'Camera position activated');
        fetchCameraPositions();
      } else {
        toast.error(data?.error || 'Failed to update camera position');
      }
    } catch (error) {
      console.error('Error toggling status:', error);
      toast.error('Failed to update camera position');
    }
  };

  const columns: DataTableColumn<CameraPosition>[] = [
    {
      key: 'position',
      header: 'Camera Position',
      cell: (cp) => (
        <div className="flex min-w-[260px] items-center gap-4">
          {cp.imageUrl ? (
            <img
              src={cp.imageUrl}
              alt={cp.name.en || 'Camera position image'}
              className="size-16 rounded-md border object-cover"
            />
          ) : (
            <div className="flex size-16 items-center justify-center rounded-md bg-muted text-muted-foreground">
              <Camera className="size-7" />
            </div>
          )}
          <div className="min-w-0">
            <Button asChild variant="link" className="h-auto p-0 text-base">
              <Link href={`/admin/camera-positions/${cp.id}/edit`}>{cp.name.en}</Link>
            </Button>
            <p className="truncate text-sm text-muted-foreground" dir="rtl">
              {cp.name.ar}
            </p>
          </div>
        </div>
      ),
    },
    {
      key: 'code',
      header: 'Code',
      cell: (cp) => <span className="font-mono text-sm text-muted-foreground">{cp.code}</span>,
    },
    {
      key: 'sortOrder',
      header: 'Sort',
      cell: (cp) => <span className="text-muted-foreground">{cp.sortOrder}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (cp) => <StatusBadge status={cp.isActive ? 'active' : 'inactive'} />,
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (cp) => (
        <div className="flex justify-end gap-1">
          <Button type="button" variant="ghost" size="sm" onClick={() => handleToggleActive(cp.id, cp.isActive)}>
            {cp.isActive ? 'Deactivate' : 'Activate'}
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href={`/admin/camera-positions/${cp.id}/edit`}>Edit</Link>
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Camera Positions"
        description="Fixed pose positions for exercises. Edit the display name, reference image, or status."
      />

      <DataTable
        columns={columns}
        data={cameraPositions}
        getRowKey={(cameraPosition) => cameraPosition.id}
        loading={loading}
        error={pageError}
        emptyTitle="No camera positions found"
        emptyDescription="Camera positions are fixed system records and cannot be created here."
      />
    </div>
  );
}
