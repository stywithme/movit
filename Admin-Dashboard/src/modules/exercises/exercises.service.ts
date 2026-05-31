import { api } from '@/lib/api/client';

export interface ExerciseListParams {
  page?: number;
  status?: string;
  search?: string;
}

export const exercisesService = {
  list: (params: ExerciseListParams) => api.get('/exercises', { params: { ...params } }),
  publish: (id: string) => api.put(`/exercises/${id}/publish`),
  unpublish: (id: string) => api.delete(`/exercises/${id}/publish`),
  delete: (id: string) => api.delete(`/exercises/${id}`),
  bulkDelete: (ids: string[]) => api.post('/exercises/bulk/delete', { ids }),
  bulkUnpublish: (ids: string[]) => api.post('/exercises/bulk/unpublish', { ids }),
};
