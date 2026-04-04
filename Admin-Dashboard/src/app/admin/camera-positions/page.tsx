'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { LocalizedText } from '@/lib/types/localized';

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

  const fetchCameraPositions = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/camera-positions?includeInactive=true');
      const data = await res.json();

      if (data.success) {
        setCameraPositions(data.data);
      }
    } catch (error) {
      console.error('Error fetching camera positions:', error);
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
      if (res.ok) fetchCameraPositions();
    } catch (error) {
      console.error('Error toggling status:', error);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Camera Positions</h1>
        <p className="text-gray-600 mt-1">Fixed pose positions for exercises — edit name, image, or status</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200">
        {loading ? (
          <div className="p-6 text-center text-gray-500">Loading camera positions...</div>
        ) : cameraPositions.length === 0 ? (
          <div className="p-6 text-center text-gray-500">No camera positions found.</div>
        ) : (
          <ul role="list" className="divide-y divide-gray-200">
            {cameraPositions.map((cp) => (
              <li key={cp.id} className="flex items-center justify-between p-4 hover:bg-gray-50">
                <div className="flex items-center gap-4">
                  {cp.imageUrl ? (
                    <img
                      src={cp.imageUrl}
                      alt={cp.name.en || 'Camera position image'}
                      className="h-16 w-16 rounded-md object-cover border border-gray-200"
                    />
                  ) : (
                    <div className="h-16 w-16 rounded-md bg-gray-100 flex items-center justify-center text-gray-400">
                      <svg className="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                      </svg>
                    </div>
                  )}
                  <div>
                    <Link
                      href={`/admin/camera-positions/${cp.id}/edit`}
                      className="text-lg font-semibold text-blue-600 hover:underline"
                    >
                      {cp.name.en} ({cp.name.ar})
                    </Link>
                    <p className="text-sm text-gray-500">Code: {cp.code}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span
                    className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                      cp.isActive
                        ? 'bg-green-100 text-green-800'
                        : 'bg-gray-100 text-gray-800'
                    }`}
                  >
                    {cp.isActive ? 'Active' : 'Inactive'}
                  </span>
                  <button
                    onClick={() => handleToggleActive(cp.id, cp.isActive)}
                    className={`text-sm ${cp.isActive ? 'text-yellow-600 hover:text-yellow-900' : 'text-green-600 hover:text-green-900'}`}
                  >
                    {cp.isActive ? 'Deactivate' : 'Activate'}
                  </button>
                  <Link
                    href={`/admin/camera-positions/${cp.id}/edit`}
                    className="text-blue-600 hover:text-blue-900 text-sm"
                  >
                    Edit
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
