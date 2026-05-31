'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent } from '@/components/ui';

export default function NewCameraPositionPage() {
  const router = useRouter();

  useEffect(() => {
    router.replace('/admin/camera-positions');
  }, [router]);

  return (
    <Card>
      <CardContent className="flex min-h-[320px] items-center justify-center text-sm text-muted-foreground">
        Camera positions are fixed and cannot be created. Redirecting...
      </CardContent>
    </Card>
  );
}
