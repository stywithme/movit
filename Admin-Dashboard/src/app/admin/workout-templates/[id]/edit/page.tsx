'use client';

import { useParams } from 'next/navigation';
import { WorkoutEditor } from '../../_components/WorkoutEditor';

export default function EditWorkoutTemplatePage() {
  const params = useParams();
  return <WorkoutEditor mode="edit" workoutId={params.id as string} />;
}
