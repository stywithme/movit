'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
import { Card, Button } from '@/components/ui';
import { PageHeader } from '@/components/common';

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

    if (loading) return <div className="p-8 text-center text-muted-foreground">Loading Report...</div>;

    if (error || !report) {
        return (
            <div className="p-8 text-center">
                <p className="mb-4 text-destructive">{error || 'Medical Report not found'}</p>
                <Button type="button" variant="outline" onClick={() => router.back()}>
                    Go back
                </Button>
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
            <PageHeader
                title="Medical Report Details"
                description="Diagnosis and prescription overview"
                breadcrumbs={[
                    { label: 'Medical Reports', href: '/admin/booking-reports' },
                    { label: 'Report Details' },
                ]}
                actions={
                    <Button variant="outline" onClick={() => router.back()}>
                        Back to Reports
                    </Button>
                }
            />

            {/* Booking Summary */}
            <Card className="border-primary/20 bg-primary/10 p-6 shadow-sm">
                <h3 className="mb-4 border-b border-primary/20 pb-2 font-semibold">Session Info</h3>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div>
                        <p className="mb-1 font-medium text-muted-foreground">Patient</p>
                        <p>{report.booking?.user?.name || 'Unknown'}</p>
                    </div>
                    <div>
                        <p className="mb-1 font-medium text-muted-foreground">Doctor</p>
                        <p>{report.booking?.admin?.name || 'Unknown'}</p>
                    </div>
                    <div>
                        <p className="mb-1 font-medium text-muted-foreground">Date</p>
                        <p>{report.booking?.startAt ? formatDate(report.booking.startAt) : 'N/A'}</p>
                    </div>
                    <div>
                        <p className="mb-1 font-medium text-muted-foreground">Report Generated</p>
                        <p>{formatDate(report.createdAt)}</p>
                    </div>
                </div>
            </Card>

            {/* Medical Content */}
            <Card className="p-6 shadow-sm">
                <div className="space-y-6">
                    <div>
                        <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-muted-foreground">Diagnosis</h3>
                        <div className="whitespace-pre-wrap rounded-lg bg-muted/50 p-4">
                            {report.content?.diagnosis || 'No diagnosis recorded.'}
                        </div>
                    </div>

                    <div>
                        <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-muted-foreground">Prescription</h3>
                        <div className="whitespace-pre-wrap rounded-lg bg-muted/50 p-4">
                            {report.content?.prescription || 'No prescription recorded.'}
                        </div>
                    </div>

                    {report.content?.notes && (
                        <div>
                            <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-muted-foreground">Additional Notes</h3>
                            <div className="whitespace-pre-wrap rounded-lg bg-muted/50 p-4">
                                {report.content.notes}
                            </div>
                        </div>
                    )}
                </div>
            </Card>
        </div>
    );
}
