export interface LocalizedText {
  ar?: string;
  en?: string;
}

export interface CreateAttributeInput {
  code: string;
  name: LocalizedText;
  description?: string | null;
}

export interface UpdateAttributeInput {
  code?: string;
  name?: LocalizedText;
  description?: string | null;
}

export interface CreateAttributeValueInput {
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  icon?: string | null;
  color?: string | null;
  isActive?: boolean;
}

export interface UpdateAttributeValueInput {
  code?: string;
  name?: LocalizedText;
  description?: LocalizedText | null;
  icon?: string | null;
  color?: string | null;
  isActive?: boolean;
}
