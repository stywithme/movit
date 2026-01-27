import { Sidebar } from '@/components/layout/Sidebar';

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-screen bg-gray-50 overflow-hidden">
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
  );
}
