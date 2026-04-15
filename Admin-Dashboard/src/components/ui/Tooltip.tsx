'use client';

import * as React from 'react';
import * as TooltipPrimitive from '@radix-ui/react-tooltip';
import { cn } from '@/lib/utils';
import { HelpCircle } from 'lucide-react';

const TooltipProvider = TooltipPrimitive.Provider;
const TooltipRoot = TooltipPrimitive.Root;
const TooltipTrigger = TooltipPrimitive.Trigger;

const TooltipContent = React.forwardRef<
  React.ElementRef<typeof TooltipPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof TooltipPrimitive.Content>
>(({ className, sideOffset = 4, ...props }, ref) => (
  <TooltipPrimitive.Content
    ref={ref}
    sideOffset={sideOffset}
    className={cn(
      'z-50 overflow-hidden rounded-lg bg-gray-900 px-3 py-2 text-sm text-white shadow-lg',
      'animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
      'data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2',
      'max-w-xs',
      className
    )}
    {...props}
  />
));

TooltipContent.displayName = TooltipPrimitive.Content.displayName;

/**
 * Tooltip Component
 * 
 * Shows a help icon (?) that reveals a description on hover.
 */
interface TooltipProps {
  content: React.ReactNode;
  children?: React.ReactNode;
  side?: 'top' | 'right' | 'bottom' | 'left';
  align?: 'start' | 'center' | 'end';
  delayDuration?: number;
}

function Tooltip({ 
  content, 
  children, 
  side = 'top', 
  align = 'center',
  delayDuration = 200 
}: TooltipProps) {
  return (
    <TooltipProvider>
      <TooltipRoot delayDuration={delayDuration}>
        <TooltipTrigger asChild>
          {children || (
            <button
              type="button"
              className="inline-flex items-center justify-center w-5 h-5 rounded-full text-gray-600 hover:text-gray-800 hover:bg-gray-100 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <HelpCircle className="w-4 h-4" />
            </button>
          )}
        </TooltipTrigger>
        <TooltipContent side={side} align={align}>
          {content}
          <TooltipPrimitive.Arrow className="fill-gray-900" />
        </TooltipContent>
      </TooltipRoot>
    </TooltipProvider>
  );
}

export { Tooltip, TooltipProvider, TooltipRoot, TooltipTrigger, TooltipContent };
