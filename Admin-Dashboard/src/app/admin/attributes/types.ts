import type { LocalizedText } from '@/lib/types/localized';

export interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
  description: LocalizedText | null;
  icon: string | null;
  color: string | null;
  isActive: boolean;
}

export interface Attribute {
  id: string;
  code: string;
  name: LocalizedText;
  description: string | null;
  isSystem: boolean;
  values: AttributeValue[];
}

export interface AttributeFormData {
  code: string;
  name: LocalizedText;
  description: string;
}

export interface AttributeValueFormData {
  code: string;
  name: LocalizedText;
  description: LocalizedText;
  icon: string;
  color: string;
  isActive: boolean;
}
