/**
 * Shared tab ids for program create / edit pages.
 */
export const PROGRAM_EDITOR_TABS = [
  { id: 'basics', label: 'Basics' },
  { id: 'prescription', label: 'Prescription' },
  { id: 'builder', label: 'Calendar' },
  { id: 'advanced', label: 'Advanced' },
] as const;

export type ProgramEditorTabId = (typeof PROGRAM_EDITOR_TABS)[number]['id'];
