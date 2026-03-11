'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
import { Card, Button } from '@/components/ui';

export default function ViewBookingReportPage({ params }: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const resolvedParams = use(params);
    const [report, setReport] = useState<any>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchReport = async () => {
            try {
                const res = await fetch(`/api/admin/booking-reports/${resolvedParams.id}`);
                const result = await res.json();

                if (result.success) {
                    setReport(result.data);
                } else {
                    setError(result.error || 'Medical Report not found');
                }
            } catch (err) {
                setError('Failed to fetch medical report');
            } finally {
                setLoading(false);
            }
        };

        fetchReport();
    }, [resolvedParams.id]);

    if (loading) return <div className="p-8 text-center text-gray-500">Loading Report...</div>;

    if (error || !report) {
        return (
            <div className="p-8 text-center">
                <p className="text-red-500 mb-4">{error || 'Medical Report not found'}</p>
                <button
                    onClick={() => router.back()}
                    className="text-blue-600 hover:underline"
                >
                    Go back
                </button>
            </div>
        );
    }

    const formatDate = (isoStr: string) => {
        return new Date(isoStr).toLocaleString('en-GB', {
            year: 'numeric', month: 'long', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    };

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Medical Report Details</h1>
                    <p className="text-gray-600 mt-1">Diagnosis and prescription overview</p>
                </div>
                <Button variant="outline" onClick={() => router.back()}>
                    Back to Reports
                </Button>
            </div>

            {/* Booking Summary */}
            <Card className="p-6 bg-blue-50 border border-blue-100 shadow-sm">
                <h3 className="font-bold text-blue-900 mb-4 border-b border-blue-200 pb-2">Session Info</h3>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div>
                        <p className="text-blue-700 font-medium mb-1">Patient</p>
                        <p className="text-blue-900">{report.booking?.user?.name || 'Unknown'}</p>
                    </div>
                    <div>
                        <p className="text-blue-700 font-medium mb-1">Doctor</p>
                        <p className="text-blue-900">{report.booking?.admin?.name || 'Unknown'}</p>
                    </div>
                    <div>
                        <p className="text-blue-700 font-medium mb-1">Date</p>
                        <p className="text-blue-900">{report.booking?.startAt ? formatDate(report.booking.startAt) : 'N/A'}</p>
                    </div>
                    <div>
                        <p className="text-blue-700 font-medium mb-1">Report Generated</p>
                        <p className="text-blue-900">{formatDate(report.createdAt)}</p>
                    </div>
                </div>
            </Card>

            {/* Medical Content */}
            <Card className="p-6 shadow-sm border border-gray-100">
                <div className="space-y-6">
                    <div>
                        <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-2">Diagnosis</h3>
                        <div className="bg-gray-50 p-4 rounded-lg whitespace-pre-wrap text-gray-800">
                            {report.content?.diagnosis || 'No diagnosis recorded.'}
                        </div>
                    </div>

                    <div>
                        <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-2">Prescription</h3>
                        <div className="bg-gray-50 p-4 rounded-lg whitespace-pre-wrap text-gray-800">
                            {report.content?.prescription || 'No prescription recorded.'}
                        </div>
                    </div>

                    {report.content?.notes && (
                        <div>
                            <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-2">Additional Notes</h3>
                            <div className="bg-gray-50 p-4 rounded-lg whitespace-pre-wrap text-gray-800">
                                {report.content.notes}
                            </div>
                        </div>
                    )}
                </div>
            </Card>
        </div>
    );
}
