import { Sidebar } from '@/components/layout/Sidebar';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { Toaster } from 'react-hot-toast';

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthProvider>
      <div className="flex h-screen bg-gray-50 overflow-hidden">
        <Toaster position="top-right" toastOptions={{ duration: 3000 }} />
        {/* Sidebar */}
        <Sidebar />

        {/* Main Content Area */}
        <main className="flex-1 flex flex-col overflow-hidden w-full">
          {/* Scrollable Content */}
          <div className="flex-1 overflow-y-auto w-full">
            <div className="container mx-auto px-4 py-8 max-w-7xl">
              {children}
            </div>
          </div>
        </main>
      </div>
    </AuthProvider>
  );
}
