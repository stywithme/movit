import { SetMetadata } from '@nestjs/common';
import { Action, Subject } from './casl.types';

export const PERMISSION_CHECK_KEY = 'permission_check';

export type RequiredPermission = [Action, Subject];

export const CheckPermission = (action: Action, subject: Subject) =>
    SetMetadata(PERMISSION_CHECK_KEY, [action, subject] as RequiredPermission);
