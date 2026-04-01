'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePermissions } from '@/hooks/usePermissions';
import { Button } from '@/components/ui';
import { Plus, Shield, SquarePen, Trash2 } from 'lucide-react';
import { AttributeFormModal } from './AttributeFormModal';
import { AttributeValueFormModal } from './AttributeValueFormModal';
import type { Attribute, AttributeValue } from './types';

function getLocalizedLabel(
  value: { en?: string; ar?: string } | null | undefined,
  fallback = '-'
) {
  return value?.en || value?.ar || fallback;
}

export default function AttributesPage() {
  const { can, isSuperAdmin } = usePermissions();

  const canCreateAttribute = can('create', 'Attribute') || isSuperAdmin;
  const canUpdateAttribute = can('update', 'Attribute') || isSuperAdmin;
  const canDeleteAttribute = can('delete', 'Attribute') || isSuperAdmin;

  const [attributes, setAttributes] = useState<Attribute[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedAttributeId, setSelectedAttributeId] = useState<string | null>(null);

  const [attributeFormOpen, setAttributeFormOpen] = useState(false);
  const [valueFormOpen, setValueFormOpen] = useState(false);
  const [editingAttribute, setEditingAttribute] = useState<Attribute | null>(null);
  const [editingValue, setEditingValue] = useState<AttributeValue | null>(null);

  const fetchAttributes = useCallback(async (preferredAttributeId?: string | null) => {
    setLoading(true);
    try {
      const res = await fetch('/api/attributes?includeInactive=true');
      const data = await res.json();

      if (data.success) {
        const nextAttributes = data.data as Attribute[];
        setAttributes(nextAttributes);
        setSelectedAttributeId((currentSelectedId) => {
          if (
            preferredAttributeId &&
            nextAttributes.some((attribute) => attribute.id === preferredAttributeId)
          ) {
            return preferredAttributeId;
          }

          if (
            currentSelectedId &&
            nextAttributes.some((attribute) => attribute.id === currentSelectedId)
          ) {
            return currentSelectedId;
          }

          return nextAttributes[0]?.id ?? null;
        });
      } else {
        alert(`Error: ${data.error || 'Failed to fetch attributes'}`);
      }
    } catch (error) {
      console.error('Error fetching attributes:', error);
      alert('Failed to fetch attributes');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchAttributes();
  }, [fetchAttributes]);

  const selectedAttribute = useMemo(
    () => attributes.find((attribute) => attribute.id === selectedAttributeId) || null,
    [attributes, selectedAttributeId]
  );

  const handleCreateAttribute = () => {
    setEditingAttribute(null);
    setAttributeFormOpen(true);
  };

  const handleEditAttribute = (attribute: Attribute) => {
    setEditingAttribute(attribute);
    setAttributeFormOpen(true);
  };

  const handleDeleteAttribute = async (attribute: Attribute) => {
    if (attribute.isSystem) {
      alert('System attributes cannot be deleted');
      return;
    }

    if (!confirm(`Are you sure you want to delete "${getLocalizedLabel(attribute.name, attribute.code)}"?`)) {
      return;
    }

    try {
      const res = await fetch(`/api/attributes/${attribute.id}`, { method: 'DELETE' });
      const data = await res.json();

      if (data.success) {
        await fetchAttributes(selectedAttributeId === attribute.id ? null : selectedAttributeId);
        return;
      }

      alert(`Error: ${data.error || 'Failed to delete attribute'}`);
    } catch (error) {
      console.error('Error deleting attribute:', error);
      alert('Failed to delete attribute');
    }
  };

  const handleCreateValue = () => {
    setEditingValue(null);
    setValueFormOpen(true);
  };

  const handleEditValue = (value: AttributeValue) => {
    setEditingValue(value);
    setValueFormOpen(true);
  };

  const handleToggleValueStatus = async (value: AttributeValue) => {
    try {
      const res = await fetch(`/api/attributes/values/${value.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive: !value.isActive }),
      });
      const data = await res.json();

      if (data.success) {
        await fetchAttributes(selectedAttributeId);
        return;
      }

      alert(`Error: ${data.error || 'Failed to update value status'}`);
    } catch (error) {
      console.error('Error updating attribute value status:', error);
      alert('Failed to update value status');
    }
  };

  const handleDeleteValue = async (value: AttributeValue) => {
    if (!confirm(`Are you sure you want to delete "${getLocalizedLabel(value.name, value.code)}"?`)) {
      return;
    }

    try {
      const res = await fetch(`/api/attributes/values/${value.id}`, {
        method: 'DELETE',
      });
      const data = await res.json();

      if (data.success) {
        await fetchAttributes(selectedAttributeId);
        return;
      }

      alert(`Error: ${data.error || 'Failed to delete attribute value'}`);
    } catch (error) {
      console.error('Error deleting attribute value:', error);
      alert('Failed to delete attribute value');
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[400px] items-center justify-center">
        <div className="text-gray-500">Loading attributes...</div>
      </div>
    );
  }

  return (
    <>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Attributes</h1>
            <p className="mt-1 text-gray-600">
              Manage attribute types and their values from one place.
            </p>
          </div>

          {canCreateAttribute && (
            <Button onClick={handleCreateAttribute}>
              <Plus className="h-4 w-4" />
              New Attribute
            </Button>
          )}
        </div>

        {attributes.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-10 text-center text-gray-500">
            <p>No attributes found.</p>
            {canCreateAttribute && (
              <Button className="mt-4" onClick={handleCreateAttribute}>
                <Plus className="h-4 w-4" />
                Create Attribute
              </Button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-4">
            <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
              <div className="border-b border-gray-200 p-4">
                <h2 className="font-semibold text-gray-900">Attribute Types</h2>
              </div>

              <nav className="space-y-1 p-2">
                {attributes.map((attribute) => {
                  const isSelected = selectedAttributeId === attribute.id;
                  const label = getLocalizedLabel(attribute.name, attribute.code);

                  return (
                    <button
                      key={attribute.id}
                      type="button"
                      onClick={() => setSelectedAttributeId(attribute.id)}
                      className={`w-full rounded-lg px-3 py-3 text-left transition-colors ${
                        isSelected
                          ? 'bg-blue-50 text-blue-700'
                          : 'text-gray-600 hover:bg-gray-50'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="truncate font-medium text-gray-900">{label}</div>
                          <div className="truncate font-mono text-xs text-gray-500">
                            {attribute.code}
                          </div>
                        </div>

                        <div className="flex flex-col items-end gap-1">
                          <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                            {attribute.values.length}
                          </span>
                          {attribute.isSystem && (
                            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-800">
                              System
                            </span>
                          )}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </nav>
            </div>

            <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm lg:col-span-3">
              {selectedAttribute ? (
                <>
                  <div className="border-b border-gray-200 p-5">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                      <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="text-xl font-semibold text-gray-900">
                            {getLocalizedLabel(selectedAttribute.name, selectedAttribute.code)}
                          </h2>

                          {selectedAttribute.isSystem && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-800">
                              <Shield className="h-3.5 w-3.5" />
                              System Attribute
                            </span>
                          )}
                        </div>

                        <p className="font-mono text-xs text-gray-500">{selectedAttribute.code}</p>

                        <p className="text-sm text-gray-600">
                          {selectedAttribute.description || 'No description provided.'}
                        </p>

                        {selectedAttribute.isSystem && (
                          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                            The attribute definition is protected. Its values can still be managed
                            from this page.
                          </div>
                        )}
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        {canUpdateAttribute && (
                          <Button variant="secondary" onClick={handleCreateValue}>
                            <Plus className="h-4 w-4" />
                            Add Value
                          </Button>
                        )}

                        {canUpdateAttribute && !selectedAttribute.isSystem && (
                          <Button
                            variant="outline"
                            onClick={() => handleEditAttribute(selectedAttribute)}
                          >
                            <SquarePen className="h-4 w-4" />
                            Edit Attribute
                          </Button>
                        )}

                        {canDeleteAttribute && !selectedAttribute.isSystem && (
                          <Button
                            variant="danger"
                            onClick={() => handleDeleteAttribute(selectedAttribute)}
                          >
                            <Trash2 className="h-4 w-4" />
                            Delete Attribute
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>

                  {selectedAttribute.values.length === 0 ? (
                    <div className="p-10 text-center text-gray-500">
                      <p>No values defined yet.</p>
                      {canUpdateAttribute && (
                        <Button className="mt-4" onClick={handleCreateValue}>
                          <Plus className="h-4 w-4" />
                          Add First Value
                        </Button>
                      )}
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full">
                        <thead className="border-b border-gray-200 bg-gray-50">
                          <tr>
                            <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                              Code
                            </th>
                            <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                              Name
                            </th>
                            <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                              Description
                            </th>
                            <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                              Color
                            </th>
                            <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                              Status
                            </th>
                            <th className="px-4 py-3 text-right text-xs font-medium uppercase text-gray-500">
                              Actions
                            </th>
                          </tr>
                        </thead>

                        <tbody className="divide-y divide-gray-200">
                          {selectedAttribute.values.map((value) => (
                            <tr
                              key={value.id}
                              className={`hover:bg-gray-50 ${
                                value.isActive ? '' : 'bg-gray-50/60 text-gray-500'
                              }`}
                            >
                              <td className="px-4 py-3 align-top text-sm font-mono text-gray-700">
                                {value.code}
                              </td>

                              <td className="px-4 py-3 align-top text-sm text-gray-900">
                                <div className="space-y-1">
                                  <div className="font-medium text-gray-900">
                                    {value.icon && <span className="mr-2">{value.icon}</span>}
                                    {getLocalizedLabel(value.name, value.code)}
                                  </div>
                                  <div className="text-xs text-gray-500" dir="rtl">
                                    {value.name.ar || '-'}
                                  </div>
                                </div>
                              </td>

                              <td className="px-4 py-3 align-top text-sm text-gray-600">
                                {getLocalizedLabel(value.description)}
                              </td>

                              <td className="px-4 py-3 align-top">
                                {value.color ? (
                                  <span
                                    className="inline-block h-6 w-6 rounded-full border border-gray-200"
                                    style={{ backgroundColor: value.color }}
                                    title={value.color}
                                  />
                                ) : (
                                  <span className="text-sm text-gray-400">-</span>
                                )}
                              </td>

                              <td className="px-4 py-3 align-top">
                                <span
                                  className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${
                                    value.isActive
                                      ? 'bg-green-100 text-green-800'
                                      : 'bg-gray-100 text-gray-600'
                                  }`}
                                >
                                  {value.isActive ? 'Active' : 'Inactive'}
                                </span>
                              </td>

                              <td className="px-4 py-3 align-top text-right">
                                <div className="flex justify-end gap-3 text-sm">
                                  {canUpdateAttribute && (
                                    <>
                                      <button
                                        type="button"
                                        onClick={() => handleToggleValueStatus(value)}
                                        className={`font-medium ${
                                          value.isActive
                                            ? 'text-yellow-600 hover:text-yellow-800'
                                            : 'text-green-600 hover:text-green-800'
                                        }`}
                                      >
                                        {value.isActive ? 'Deactivate' : 'Activate'}
                                      </button>

                                      <button
                                        type="button"
                                        onClick={() => handleEditValue(value)}
                                        className="font-medium text-blue-600 hover:text-blue-800"
                                      >
                                        Edit
                                      </button>
                                    </>
                                  )}

                                  {canDeleteAttribute && (
                                    <button
                                      type="button"
                                      onClick={() => handleDeleteValue(value)}
                                      className="font-medium text-red-600 hover:text-red-800"
                                    >
                                      Delete
                                    </button>
                                  )}
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </>
              ) : (
                <div className="p-10 text-center text-gray-500">
                  Select an attribute type to manage its values.
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      <AttributeFormModal
        open={attributeFormOpen}
        onOpenChange={(open) => {
          setAttributeFormOpen(open);
          if (!open) {
            setEditingAttribute(null);
          }
        }}
        editAttribute={editingAttribute}
        onSaved={(attribute) => {
          setEditingAttribute(null);
          void fetchAttributes(attribute.id);
        }}
      />

      <AttributeValueFormModal
        open={valueFormOpen}
        onOpenChange={(open) => {
          setValueFormOpen(open);
          if (!open) {
            setEditingValue(null);
          }
        }}
        attribute={selectedAttribute}
        editValue={editingValue}
        onSaved={() => {
          setEditingValue(null);
          void fetchAttributes(selectedAttributeId);
        }}
      />
    </>
  );
}

