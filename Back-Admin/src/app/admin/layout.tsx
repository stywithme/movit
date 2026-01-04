import Link from 'next/link';

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center gap-8">
              <Link href="/admin" className="text-xl font-bold text-gray-900">
                Training Validator
              </Link>
              <nav className="hidden md:flex items-center gap-6">
                <Link
                  href="/admin/exercises"
                  className="text-gray-600 hover:text-gray-900 transition-colors"
                >
                  Exercises
                </Link>
                <Link
                  href="/admin/attributes"
                  className="text-gray-600 hover:text-gray-900 transition-colors"
                >
                  Attributes
                </Link>
              </nav>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-500">Admin</span>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>
    </div>
  );
}

