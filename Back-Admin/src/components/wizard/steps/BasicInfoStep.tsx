'use client';

/**
 * Step 1: Basic Information
 * =========================
 */

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useWizardStore } from '../WizardContext';
import { BasicInfoSchema, type BasicInfoData } from '@/modules/exercises/exercises.validation';

interface BasicInfoStepProps {
  categories: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
  }>;
}

export function BasicInfoStep({ categories }: BasicInfoStepProps) {
  const { basicInfo, setBasicInfo } = useWizardStore();
  
  const {
    register,
    watch,
    formState: { errors },
  } = useForm<BasicInfoData>({
    resolver: zodResolver(BasicInfoSchema),
    defaultValues: {
      name: basicInfo.name || { ar: '', en: '' },
      description: basicInfo.description || { ar: '', en: '' },
      instructions: basicInfo.instructions || { ar: '', en: '' },
      categoryId: basicInfo.categoryId || '',
    },
  });
  
  // Sync form changes to store using subscription
  useEffect(() => {
    const subscription = watch((data) => {
      setBasicInfo(data as BasicInfoData);
    });
    return () => subscription.unsubscribe();
  }, [watch, setBasicInfo]);
  
  return (
    <div className="space-y-8 max-w-3xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Basic Information</h2>
        <p className="text-gray-500">Enter the exercise name and description in both languages.</p>
      </div>
      
      {/* Exercise Name */}
      <div className="space-y-4">
        <label className="block text-sm font-semibold text-gray-700">
          Exercise Name <span className="text-red-500">*</span>
        </label>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <input
              type="text"
              placeholder="English name"
              {...register('name.en')}
              className={`
                w-full px-4 py-3 rounded-lg border-2 transition-colors
                text-gray-900 placeholder:text-gray-500
                focus:outline-none focus:ring-2 focus:ring-blue-500
                ${errors.name?.en ? 'border-red-300 bg-red-50' : 'border-gray-200'}
              `}
            />
            {errors.name?.en && (
              <p className="mt-1 text-sm text-red-500">{errors.name.en.message}</p>
            )}
          </div>
          <div dir="rtl">
            <input
              type="text"
              placeholder="الاسم بالعربية"
              {...register('name.ar')}
              className={`
                w-full px-4 py-3 rounded-lg border-2 transition-colors
                text-gray-900 placeholder:text-gray-500
                focus:outline-none focus:ring-2 focus:ring-blue-500
                ${errors.name?.ar ? 'border-red-300 bg-red-50' : 'border-gray-200'}
              `}
            />
            {errors.name?.ar && (
              <p className="mt-1 text-sm text-red-500">{errors.name.ar.message}</p>
            )}
          </div>
        </div>
      </div>
      
      {/* Description */}
      <div className="space-y-4">
        <label className="block text-sm font-semibold text-gray-700">
          Description
        </label>
        <div className="grid grid-cols-2 gap-4">
          <textarea
            placeholder="English description"
            rows={3}
            {...register('description.en')}
            className="w-full px-4 py-3 rounded-lg border-2 border-gray-200 text-gray-900 placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <textarea
            dir="rtl"
            placeholder="الوصف بالعربية"
            rows={3}
            {...register('description.ar')}
            className="w-full px-4 py-3 rounded-lg border-2 border-gray-200 text-gray-900 placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>
      
      {/* Instructions */}
      <div className="space-y-4">
        <label className="block text-sm font-semibold text-gray-700">
          Instructions
        </label>
        <div className="grid grid-cols-2 gap-4">
          <textarea
            placeholder="English instructions"
            rows={4}
            {...register('instructions.en')}
            className="w-full px-4 py-3 rounded-lg border-2 border-gray-200 text-gray-900 placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <textarea
            dir="rtl"
            placeholder="التعليمات بالعربية"
            rows={4}
            {...register('instructions.ar')}
            className="w-full px-4 py-3 rounded-lg border-2 border-gray-200 text-gray-900 placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>
      
      {/* Category */}
      <div className="space-y-4">
        <label className="block text-sm font-semibold text-gray-700">
          Category <span className="text-red-500">*</span>
        </label>
        <select
          {...register('categoryId')}
          className={`
            w-full px-4 py-3 rounded-lg border-2 transition-colors
            text-gray-900
            focus:outline-none focus:ring-2 focus:ring-blue-500
            ${errors.categoryId ? 'border-red-300 bg-red-50' : 'border-gray-200'}
          `}
        >
          <option value="">Select a category</option>
          {categories.map((cat) => (
            <option key={cat.id} value={cat.id}>
              {cat.name.en} / {cat.name.ar}
            </option>
          ))}
        </select>
        {errors.categoryId && (
          <p className="mt-1 text-sm text-red-500">{errors.categoryId.message}</p>
        )}
      </div>
    </div>
  );
}

export default BasicInfoStep;
