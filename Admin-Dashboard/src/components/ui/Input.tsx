import * as React from 'react';
import { cn } from '@/lib/utils';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean;
  helperText?: string;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, error, helperText, type = 'text', ...props }, ref) => {
    return (
      <div className="w-full">
        <input
          type={type}
          className={cn(
            'flex h-9 w-full min-w-0 rounded-md border bg-transparent px-3 py-1 text-base shadow-xs transition-[color,box-shadow] outline-none md:text-sm',
            'placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground',
            'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
            'disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50',
            error ? 'border-destructive aria-invalid:ring-destructive/20' : 'border-input',
            className
          )}
          aria-invalid={error || undefined}
          ref={ref}
          {...props}
        />
        {helperText && (
          <p className={cn(
            'mt-1.5 text-sm',
            error ? 'text-destructive' : 'text-muted-foreground'
          )}>
            {helperText}
          </p>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

export { Input };
