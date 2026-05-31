import type { ReactNode } from 'react';
import { Card, CardContent, CardFooter, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { cn } from '@/lib/utils';

interface FormShellProps {
  title?: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
}

export function FormShell({ title, description, children, footer, className }: FormShellProps) {
  return (
    <Card className={cn('overflow-hidden', className)}>
      {(title || description) && (
        <CardHeader>
          {title && <CardTitle>{title}</CardTitle>}
          {description && <CardDescription>{description}</CardDescription>}
        </CardHeader>
      )}
      <CardContent className="space-y-6">{children}</CardContent>
      {footer && <CardFooter className="justify-end gap-2 border-t bg-muted/20 pt-6">{footer}</CardFooter>}
    </Card>
  );
}
