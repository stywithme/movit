'use client';

/**
 * Step 1: Basic Information + Exercise Type
 * ==========================================
 * 
 * Combines basic info and counting method selection in one step.
 */

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useWizardStore } from '../WizardContext';
import { BasicInfoSchema, type BasicInfoData } from '@/modules/exercises/exercises.validation';
import { Input, Textarea, Select, Label, Card, Badge } from '@/components/ui';
import { FileUpload } from '@/components/forms';
import { Check } from 'lucide-react';
import type { CountingMethodCode } from '@/lib/types/localized';

interface BasicInfoStepProps {
  categories: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
  }>;
  countingMethods: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
    description?: { ar: string; en: string };
  }>;
}

const METHOD_DETAILS: Record<CountingMethodCode, {
  icon: string;
  flow: string;
  examples: string;
}> = {
  up_down: {
    icon: '🔄',
    flow: 'START → DOWN → UP → START',
    examples: 'Squat, Lunge, Bicep Curl',
  },
  push_pull: {
    icon: '🔃',
    flow: 'START → PUSH → PULL → START',
    examples: 'Push-up, Pull-up, Bench Press',
  },
  hold: {
    icon: '⏱️',
    flow: 'IDLE ↔ COUNT (timer)',
    examples: 'Plank, Wall Sit, Dead Hang',
  },
};

export function BasicInfoStep({ categories, countingMethods }: BasicInfoStepProps) {
  const { basicInfo, setBasicInfo, countingMethod, setCountingMethod } = useWizardStore();
  
  const {
    register,
    watch,
    setValue,
    formState: { errors },
  } = useForm<BasicInfoData>({
    resolver: zodResolver(BasicInfoSchema),
    defaultValues: {
      name: basicInfo.name || { ar: '', en: '' },
      description: basicInfo.description || { ar: '', en: '' },
      instructions: basicInfo.instructions || { ar: '', en: '' },
      categoryId: basicInfo.categoryId || '',
      imageUrl: basicInfo.imageUrl || '',
    },
  });
  
  // Sync form changes to store
  useEffect(() => {
    const subscription = watch((data) => {
      setBasicInfo(data as Partial<BasicInfoData>);
    });
    return () => subscription.unsubscribe();
  }, [watch, setBasicInfo]);
  
  const handleSelectType = (method: typeof countingMethods[number]) => {
    setCountingMethod({
      countingMethodId: method.id,
      countingMethodCode: method.code as CountingMethodCode,
    });
  };
  
  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Basic Information</h2>
        <p className="text-gray-500">Enter exercise details and select the counting type.</p>
      </div>
      
      {/* Exercise Name */}
      <div className="space-y-2">
        <Label required tooltip="The exercise name displayed to users in both languages.">
          Exercise Name
        </Label>
        <div className="grid grid-cols-2 gap-4">
          <Input
            placeholder="English name"
            error={!!errors.name?.en}
            helperText={errors.name?.en?.message}
            {...register('name.en')}
          />
          <div dir="rtl">
            <Input
              placeholder="الاسم بالعربية"
              error={!!errors.name?.ar}
              helperText={errors.name?.ar?.message}
              {...register('name.ar')}
            />
          </div>
        </div>
      </div>
      
      {/* Category */}
      <div className="space-y-2">
        <Label required tooltip="Select the main category for this exercise.">
          Category
        </Label>
        <Select
          error={!!errors.categoryId}
          helperText={errors.categoryId?.message}
          placeholder="Select a category"
          options={categories.map(cat => ({
            value: cat.id,
            label: `${cat.name.en} / ${cat.name.ar}`,
          }))}
          {...register('categoryId')}
        />
      </div>

      {/* Exercise Image */}
      <div className="space-y-2">
        <Label tooltip="Optional: Upload a primary image for this exercise.">
          Exercise Image
        </Label>
        <FileUpload
          label="Primary Image"
          value={basicInfo.imageUrl || ''}
          onChange={(imageUrl) => {
            setValue('imageUrl', imageUrl, { shouldDirty: true });
            setBasicInfo({ imageUrl });
          }}
          uploadType="exercise-image"
          accept="image/*"
          helperText="Recommended: 1200x800 JPG or PNG"
        />
      </div>
      
      {/* Exercise Type Selection */}
      <div className="space-y-3 pt-4 border-t">
        <Label required tooltip="How repetitions are counted - determines phases and validation logic.">
          Exercise Type
        </Label>
        <div className="grid grid-cols-3 gap-4">
          {countingMethods.map((method) => {
            const details = METHOD_DETAILS[method.code as CountingMethodCode];
            const isSelected = countingMethod.countingMethodId === method.id;
            
            return (
              <Card
                key={method.id}
                interactive
                selected={isSelected}
                className="p-4 relative"
                onClick={() => handleSelectType(method)}
              >
                {/* Selected check */}
                {isSelected && (
                  <div className="absolute top-2 right-2 w-5 h-5 bg-blue-500 rounded-full flex items-center justify-center">
                    <Check className="w-3 h-3 text-white" />
                  </div>
                )}
                
                <div className="text-center space-y-2">
                  <span className="text-3xl">{details?.icon || '📊'}</span>
                  <h4 className="font-semibold text-gray-900">{method.name.en}</h4>
                  <p className="text-xs text-gray-500">{method.name.ar}</p>
                  {details && (
                    <div className="font-mono text-xs bg-gray-100 rounded px-2 py-1 text-gray-600">
                      {details.flow}
                    </div>
                  )}
                  {details && (
                    <p className="text-xs text-gray-400">{details.examples}</p>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      </div>
      
      {/* Description (Optional) */}
      <div className="space-y-2 pt-4 border-t">
        <Label tooltip="Optional: Brief description of this exercise.">
          Description
        </Label>
        <div className="grid grid-cols-2 gap-4">
          <Textarea
            placeholder="English description"
            rows={2}
            {...register('description.en')}
          />
          <div dir="rtl">
            <Textarea
              placeholder="الوصف بالعربية"
              rows={2}
              {...register('description.ar')}
            />
          </div>
        </div>
      </div>
      
      {/* Instructions (Optional) */}
      <div className="space-y-2">
        <Label tooltip="Optional: How to perform this exercise correctly.">
          Instructions
        </Label>
        <div className="grid grid-cols-2 gap-4">
          <Textarea
            placeholder="English instructions"
            rows={3}
            {...register('instructions.en')}
          />
          <div dir="rtl">
            <Textarea
              placeholder="التعليمات بالعربية"
              rows={3}
              {...register('instructions.ar')}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

export default BasicInfoStep;
