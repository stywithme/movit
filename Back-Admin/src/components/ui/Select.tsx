'use client';

import { forwardRef, SelectHTMLAttributes } from 'react';

interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> {
  className?: string;
  error?: string;
}

/**
 * Base Select component with consistent styling
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ className = '', error, ...props }, ref) => {
    return (
      <select
        ref={ref}
        className={`w-full px-3 py-2 border rounded-md shadow-sm text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed ${
          error ? 'border-red-300 focus:border-red-500 focus:ring-red-500' : 'border-gray-300'
        } ${className}`}
        {...props}
      />
    );
  }
);

Select.displayName = 'Select';

export default Select;

