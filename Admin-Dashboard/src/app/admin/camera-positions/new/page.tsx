'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function NewCameraPositionPage() {
  const router = useRouter();

  useEffect(() => {
    router.replace('/admin/camera-positions');
  }, [router]);

  return (
    <div className="flex items-center justify-center min-h-[400px]">
      <p className="text-gray-500">Camera positions are fixed and cannot be created. Redirecting...</p>
    </div>
  );
}
