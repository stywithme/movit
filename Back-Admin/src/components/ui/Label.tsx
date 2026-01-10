import * as React from 'react';
import { cn } from '@/lib/utils';
import { Tooltip } from './Tooltip';

export interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {
  required?: boolean;
  tooltip?: string;
}

const Label = React.forwardRef<HTMLLabelElement, LabelProps>(
  ({ className, required, tooltip, children, ...props }, ref) => (
    <label
      ref={ref}
      className={cn(
        'flex items-center gap-1.5 text-sm font-semibold text-gray-700 mb-1.5',
        className
      )}
      {...props}
    >
      {children}
      {required && <span className="text-red-500">*</span>}
      {tooltip && <Tooltip content={tooltip} />}
    </label>
  )
);

Label.displayName = 'Label';

export { Label };
