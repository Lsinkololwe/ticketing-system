'use client';

import { useWatch } from 'react-hook-form';
import type { EditorForm } from './model';

/** Live form values (the zod input shape equals EditorForm). */
export function useEditorValues(): EditorForm {
  return useWatch() as EditorForm;
}
