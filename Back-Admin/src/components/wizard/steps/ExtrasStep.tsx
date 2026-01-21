'use client';

/**
 * Step 7: Extras (Attributes + Feedback Messages)
 * ================================================
 */

import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge, Input, Label } from '@/components/ui';
import { Plus, X } from 'lucide-react';
import type { FeedbackMessageData } from '@/modules/exercises/exercises.validation';

interface ExtrasStepProps {
  muscles: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  equipment: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  tags: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
}

export function ExtrasStep({ muscles, equipment, tags }: ExtrasStepProps) {
  const { extras, setExtras, addFeedbackMessage, updateFeedbackMessage, removeFeedbackMessage } = useWizardStore();
  
  const toggleAttribute = (type: 'muscles' | 'equipment' | 'tags', id: string) => {
    const current = extras[type] || [];
    const updated = current.includes(id)
      ? current.filter(i => i !== id)
      : [...current, id];
    setExtras({ [type]: updated });
  };
  
  const feedbackMessages = extras.feedbackMessages || [];
  
  const addNewMessage = (type: FeedbackMessageData['type']) => {
    addFeedbackMessage({
      type,
      message: { ar: '', en: '' },
    });
  };
  
  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Attributes & Feedback</h2>
        <p className="text-gray-500">Add metadata and user feedback messages.</p>
      </div>
      
      {/* Attributes Section */}
      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <CardTitle>Target Muscles</CardTitle>
              <Label tooltip="Select the primary muscles worked by this exercise." />
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {muscles.map((muscle) => {
                const isSelected = (extras.muscles || []).includes(muscle.id);
                return (
                  <Badge
                    key={muscle.id}
                    variant={isSelected ? 'purple' : 'default'}
                    className={`cursor-pointer px-3 py-1.5 ${!isSelected && 'hover:bg-gray-200'}`}
                    onClick={() => toggleAttribute('muscles', muscle.id)}
                  >
                    {isSelected && '✓ '}{muscle.name.en}
                  </Badge>
                );
              })}
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <CardTitle>Equipment</CardTitle>
              <Label tooltip="Select any equipment required to perform this exercise." />
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {equipment.map((eq) => {
                const isSelected = (extras.equipment || []).includes(eq.id);
                return (
                  <Badge
                    key={eq.id}
                    variant={isSelected ? 'orange' : 'default'}
                    className={`cursor-pointer px-3 py-1.5 ${!isSelected && 'hover:bg-gray-200'}`}
                    onClick={() => toggleAttribute('equipment', eq.id)}
                  >
                    {isSelected && '✓ '}{eq.name.en}
                  </Badge>
                );
              })}
            </div>
          </CardContent>
        </Card>
        
        {tags.length > 0 && (
          <Card>
            <CardHeader>
              <div className="flex items-center gap-2">
                <CardTitle>Tags</CardTitle>
                <Label tooltip="Add tags to help users filter and find this exercise." />
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap gap-2">
                {tags.map((tag) => {
                  const isSelected = (extras.tags || []).includes(tag.id);
                  return (
                    <Badge
                      key={tag.id}
                      variant={isSelected ? 'teal' : 'default'}
                      className={`cursor-pointer px-3 py-1.5 ${!isSelected && 'hover:bg-gray-200'}`}
                      onClick={() => toggleAttribute('tags', tag.id)}
                    >
                      {isSelected && '✓ '}{tag.name.en}
                    </Badge>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        )}
      </div>
      
      {/* Feedback Messages */}
      <div className="space-y-4 pt-6 border-t">
        <div className="flex items-center gap-2">
          <h3 className="text-xl font-bold text-gray-900">Feedback Messages</h3>
          <Label tooltip="Messages shown to the user during or after the exercise." />
        </div>
        
        <div className="grid gap-4">
          {/* Message Types */}
          {(['motivational', 'tip'] as const).map((type) => {
            const typeMessages = feedbackMessages.filter(m => m.type === type);
            const typeConfig = {
              motivational: { label: '💪 Motivational', variant: 'success' as const },
              tip: { label: '💡 Tips', variant: 'primary' as const },
            };
            const { label } = typeConfig[type];
            
            return (
              <Card key={type}>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-lg">{label}</CardTitle>
                    <Button variant="ghost" size="sm" onClick={() => addNewMessage(type)} icon={<Plus className="h-4 w-4" />}>
                      Add
                    </Button>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {typeMessages.map((msg, idx) => {
                    const globalIndex = feedbackMessages.indexOf(msg);
                    return (
                      <div key={idx} className="flex gap-2 items-start">
                        <div className="flex-1 grid grid-cols-2 gap-2">
                          <Input
                            value={msg.message.en}
                            onChange={(e) => updateFeedbackMessage(globalIndex, {
                              ...msg,
                              message: { ...msg.message, en: e.target.value },
                            })}
                            placeholder="English message"
                          />
                          <Input
                            dir="rtl"
                            value={msg.message.ar}
                            onChange={(e) => updateFeedbackMessage(globalIndex, {
                              ...msg,
                              message: { ...msg.message, ar: e.target.value },
                            })}
                            placeholder="الرسالة بالعربية"
                          />
                        </div>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => removeFeedbackMessage(globalIndex)}
                          className="text-red-500 hover:text-red-700"
                        >
                          <X className="h-4 w-4" />
                        </Button>
                      </div>
                    );
                  })}
                  
                  {typeMessages.length === 0 && (
                    <p className="text-sm text-gray-400 italic text-center py-2">No {type.replace('_', ' ')} messages</p>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default ExtrasStep;
