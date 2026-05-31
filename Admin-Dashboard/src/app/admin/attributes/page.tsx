'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePermissions } from '@/hooks/usePermissions';
import { toast } from 'sonner';
import { Badge, Button, Card, CardContent, CardHeader, CardTitle } from '@/components/ui';
import { ConfirmDialog, DataTable, PageHeader, StatusBadge, type DataTableColumn } from '@/components/common';
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

type ConfirmAction =
  | { type: 'delete-attribute'; attribute: Attribute }
  | { type: 'delete-value'; value: AttributeValue }
  | null;

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
  const [confirmAction, setConfirmAction] = useState<ConfirmAction>(null);
  const [actionLoading, setActionLoading] = useState(false);

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
        toast.error(data.error || 'Failed to fetch attributes');
      }
    } catch (error) {
      console.error('Error fetching attributes:', error);
      toast.error('Failed to fetch attributes');
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
      toast.warning('System attributes cannot be deleted');
      return;
    }

    setConfirmAction({ type: 'delete-attribute', attribute });
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
        toast.success(value.isActive ? 'Attribute value deactivated' : 'Attribute value activated');
        await fetchAttributes(selectedAttributeId);
        return;
      }

      toast.error(data.error || 'Failed to update value status');
    } catch (error) {
      console.error('Error updating attribute value status:', error);
      toast.error('Failed to update value status');
    }
  };

  const handleDeleteValue = async (value: AttributeValue) => {
    setConfirmAction({ type: 'delete-value', value });
  };

  const handleConfirmDelete = async () => {
    if (!confirmAction) return;

    setActionLoading(true);
    try {
      const isAttributeDelete = confirmAction.type === 'delete-attribute';
      const url = isAttributeDelete
        ? `/api/attributes/${confirmAction.attribute.id}`
        : `/api/attributes/values/${confirmAction.value.id}`;
      const res = await fetch(url, { method: 'DELETE' });
      const data = await res.json();

      if (data.success) {
        toast.success(isAttributeDelete ? 'Attribute deleted' : 'Attribute value deleted');
        const preferredId =
          isAttributeDelete && selectedAttributeId === confirmAction.attribute.id
            ? null
            : selectedAttributeId;
        setConfirmAction(null);
        await fetchAttributes(preferredId);
        return;
      }

      toast.error(data.error || (isAttributeDelete ? 'Failed to delete attribute' : 'Failed to delete attribute value'));
    } catch (error) {
      console.error('Error deleting attribute data:', error);
      toast.error(confirmAction.type === 'delete-attribute' ? 'Failed to delete attribute' : 'Failed to delete attribute value');
    } finally {
      setActionLoading(false);
    }
  };

  const valueColumns: DataTableColumn<AttributeValue>[] = [
    {
      key: 'code',
      header: 'Code',
      cell: (value) => <span className="font-mono text-sm text-muted-foreground">{value.code}</span>,
    },
    {
      key: 'name',
      header: 'Name',
      cell: (value) => (
        <div className="min-w-[180px] space-y-1">
          <div className="font-medium">
            {value.icon && <span className="mr-2">{value.icon}</span>}
            {getLocalizedLabel(value.name, value.code)}
          </div>
          <div className="text-xs text-muted-foreground" dir="rtl">
            {value.name.ar || '-'}
          </div>
        </div>
      ),
    },
    {
      key: 'description',
      header: 'Description',
      cell: (value) => (
        <span className="block max-w-xs truncate text-muted-foreground">
          {getLocalizedLabel(value.description)}
        </span>
      ),
    },
    {
      key: 'color',
      header: 'Color',
      cell: (value) =>
        value.color ? (
          <span
            className="inline-block size-6 rounded-full border"
            style={{ backgroundColor: value.color }}
            title={value.color}
          />
        ) : (
          <span className="text-muted-foreground">-</span>
        ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (value) => <StatusBadge status={value.isActive ? 'active' : 'inactive'} />,
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (value) => (
        <div className="flex justify-end gap-1">
          {canUpdateAttribute && (
            <>
              <Button type="button" variant="ghost" size="sm" onClick={() => handleToggleValueStatus(value)}>
                {value.isActive ? 'Deactivate' : 'Activate'}
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => handleEditValue(value)}>
                Edit
              </Button>
            </>
          )}

          {canDeleteAttribute && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-destructive hover:text-destructive"
              onClick={() => handleDeleteValue(value)}
            >
              Delete
            </Button>
          )}
        </div>
      ),
    },
  ];

  const confirmCopy = {
    title: confirmAction?.type === 'delete-attribute' ? 'Delete attribute?' : 'Delete attribute value?',
    description:
      confirmAction?.type === 'delete-attribute'
        ? `This will permanently delete "${getLocalizedLabel(confirmAction.attribute.name, confirmAction.attribute.code)}".`
        : `This will permanently delete "${getLocalizedLabel(confirmAction?.value.name, confirmAction?.value.code)}".`,
  };

  if (loading) {
    return (
      <Card>
        <CardContent className="flex min-h-[320px] items-center justify-center text-sm text-muted-foreground">
          Loading attributes...
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <div className="space-y-6">
        <PageHeader
          title="Attributes"
          description="Manage attribute types and their values from one place."
          actions={
            canCreateAttribute ? (
              <Button onClick={handleCreateAttribute}>
                <Plus className="size-4" />
                New Attribute
              </Button>
            ) : null
          }
        />

        {attributes.length === 0 ? (
          <Card>
            <CardContent className="p-10 text-center text-muted-foreground">
              <p>No attributes found.</p>
              {canCreateAttribute && (
                <Button className="mt-4" onClick={handleCreateAttribute}>
                  <Plus className="size-4" />
                  Create Attribute
                </Button>
              )}
            </CardContent>
          </Card>
        ) : (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-4">
            <Card className="overflow-hidden">
              <CardHeader className="border-b p-4">
                <CardTitle className="text-base">Attribute Types</CardTitle>
              </CardHeader>

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
                          ? 'bg-accent text-accent-foreground ring-1 ring-ring/20'
                          : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="truncate font-medium text-foreground">{label}</div>
                          <div className="truncate font-mono text-xs text-muted-foreground">
                            {attribute.code}
                          </div>
                        </div>

                        <div className="flex flex-col items-end gap-1">
                          <Badge variant="secondary">{attribute.values.length}</Badge>
                          {attribute.isSystem && (
                            <Badge variant="warning">
                              System
                            </Badge>
                          )}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </nav>
            </Card>

            <div className="space-y-4 lg:col-span-3">
              {selectedAttribute ? (
                <>
                  <Card>
                    <CardContent className="p-5">
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div className="space-y-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <h2 className="text-xl font-semibold">
                              {getLocalizedLabel(selectedAttribute.name, selectedAttribute.code)}
                            </h2>

                            {selectedAttribute.isSystem && (
                              <Badge variant="warning">
                                <Shield className="size-3.5" />
                                System Attribute
                              </Badge>
                            )}
                          </div>

                          <p className="font-mono text-xs text-muted-foreground">{selectedAttribute.code}</p>

                          <p className="text-sm text-muted-foreground">
                            {selectedAttribute.description || 'No description provided.'}
                          </p>

                          {selectedAttribute.isSystem && (
                            <div className="rounded-lg border border-warning/30 bg-warning/10 px-3 py-2 text-sm text-warning">
                              The attribute definition is protected. Its values can still be managed
                              from this page.
                            </div>
                          )}
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          {canUpdateAttribute && (
                            <Button variant="secondary" onClick={handleCreateValue}>
                              <Plus className="size-4" />
                              Add Value
                            </Button>
                          )}

                          {canUpdateAttribute && !selectedAttribute.isSystem && (
                            <Button
                              variant="outline"
                              onClick={() => handleEditAttribute(selectedAttribute)}
                            >
                              <SquarePen className="size-4" />
                              Edit Attribute
                            </Button>
                          )}

                          {canDeleteAttribute && !selectedAttribute.isSystem && (
                            <Button
                              variant="danger"
                              onClick={() => handleDeleteAttribute(selectedAttribute)}
                            >
                              <Trash2 className="size-4" />
                              Delete Attribute
                            </Button>
                          )}
                        </div>
                      </div>
                    </CardContent>
                  </Card>

                  <DataTable
                    columns={valueColumns}
                    data={selectedAttribute.values}
                    getRowKey={(value) => value.id}
                    emptyTitle="No values defined yet"
                    emptyDescription="Add the first value for this attribute type."
                  />
                </>
              ) : (
                <Card>
                  <CardContent className="p-10 text-center text-muted-foreground">
                    Select an attribute type to manage its values.
                  </CardContent>
                </Card>
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

      <ConfirmDialog
        open={!!confirmAction}
        onOpenChange={(open) => !open && setConfirmAction(null)}
        title={confirmCopy.title}
        description={confirmCopy.description}
        confirmLabel="Delete"
        destructive
        loading={actionLoading}
        onConfirm={handleConfirmDelete}
      />
    </>
  );
}

