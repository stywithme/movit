'use client';

import { forwardRef, InputHTMLAttributes, TextareaHTMLAttributes } from 'react';

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> {
  className?: string;
  error?: string;
}

interface TextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className'> {
  className?: string;
  error?: string;
}

/**
 * Base Input component with consistent styling
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className = '', error, ...props }, ref) => {
    return (
      <input
        ref={ref}
        className={`w-full px-3 py-2 border rounded-md shadow-sm text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed ${
          error ? 'border-red-300 focus:border-red-500 focus:ring-red-500' : 'border-gray-300'
        } ${className}`}
        {...props}
      />
    );
  }
);

Input.displayName = 'Input';

/**
 * Base Textarea component with consistent styling
 */
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className = '', error, ...props }, ref) => {
    return (
      <textarea
        ref={ref}
        className={`w-full px-3 py-2 border rounded-md shadow-sm text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed ${
          error ? 'border-red-300 focus:border-red-500 focus:ring-red-500' : 'border-gray-300'
        } ${className}`}
        {...props}
      />
    );
  }
);

Textarea.displayName = 'Textarea';

export default Input;

