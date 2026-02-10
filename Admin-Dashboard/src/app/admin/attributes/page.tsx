'use client';

import { useEffect, useState } from 'react';
import { LocalizedText } from '@/lib/types/localized';

interface Attribute {
  id: string;
  code: string;
  name: LocalizedText;
  description: string | null;
  isSystem: boolean;
  values: AttributeValue[];
}

interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
  description: LocalizedText | null;
  icon: string | null;
  color: string | null;
  isActive: boolean;
}

export default function AttributesPage() {
  const [attributes, setAttributes] = useState<Attribute[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  useEffect(() => {
    const fetchAttributes = async () => {
      try {
        const res = await fetch('/api/attributes');
        const data = await res.json();
        if (data.success) {
          setAttributes(data.data);
          if (data.data.length > 0 && !selectedCode) {
            setSelectedCode(data.data[0].code);
          }
        }
      } catch (error) {
        console.error('Error fetching attributes:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchAttributes();
  }, []);

  const selectedAttribute = attributes.find((a) => a.code === selectedCode);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Attributes</h1>
        <p className="text-gray-600 mt-1">Manage categories, muscles, equipment, and other attribute values</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        {/* Attribute Types List */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <div className="p-4 border-b border-gray-200">
            <h2 className="font-semibold text-gray-900">Attribute Types</h2>
          </div>
          <nav className="p-2">
            {attributes.map((attr) => (
              <button
                key={attr.code}
                onClick={() => setSelectedCode(attr.code)}
                className={`w-full text-left px-3 py-2 rounded-md text-sm transition-colors ${
                  selectedCode === attr.code
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
              >
                <div className="flex justify-between items-center">
                  <span>{(attr.name as LocalizedText).en}</span>
                  <span className="text-xs text-gray-400">{attr.values.length}</span>
                </div>
              </button>
            ))}
          </nav>
        </div>

        {/* Values List */}
        <div className="md:col-span-3 bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          {selectedAttribute ? (
            <>
              <div className="p-4 border-b border-gray-200 flex justify-between items-center">
                <div>
                  <h2 className="font-semibold text-gray-900">
                    {(selectedAttribute.name as LocalizedText).en}
                  </h2>
                  <p className="text-sm text-gray-500 mt-1">
                    {selectedAttribute.description}
                  </p>
                </div>
                {!selectedAttribute.isSystem && (
                  <button className="px-3 py-1.5 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700">
                    Add Value
                  </button>
                )}
              </div>

              {selectedAttribute.values.length === 0 ? (
                <div className="p-8 text-center text-gray-500">
                  No values defined yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                          Code
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                          Name (EN)
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                          Name (AR)
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                          Color
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                          Status
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {selectedAttribute.values.map((value) => (
                        <tr key={value.id} className="hover:bg-gray-50">
                          <td className="px-4 py-3 text-sm font-mono text-gray-600">
                            {value.code}
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-900">
                            {value.icon && <span className="mr-2">{value.icon}</span>}
                            {(value.name as LocalizedText).en}
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-900" dir="rtl">
                            {(value.name as LocalizedText).ar}
                          </td>
                          <td className="px-4 py-3">
                            {value.color ? (
                              <span
                                className="inline-block w-6 h-6 rounded-full border border-gray-200"
                                style={{ backgroundColor: value.color }}
                                title={value.color}
                              />
                            ) : (
                              <span className="text-gray-400">-</span>
                            )}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${
                                value.isActive
                                  ? 'bg-green-100 text-green-800'
                                  : 'bg-gray-100 text-gray-600'
                              }`}
                            >
                              {value.isActive ? 'Active' : 'Inactive'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          ) : (
            <div className="p-8 text-center text-gray-500">
              Select an attribute type to view its values.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

