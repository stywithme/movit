<?php

namespace Modules\Orders\Queries;

use Modules\Orders\Models\SellOrderItem;
use Modules\Orders\Models\SellOrder;
use Modules\Inventories\Models\Stock;
use Illuminate\Support\Facades\DB;

class GetPurchaseList
{
    /**
     * Get purchase list of products that need to be purchased
     *
     * Conditions:
     * - Order date within last 90 days
     * - order_status_id = 1
     * - Item fulfill_status = 0 (NOT_FULFILLED)
     * - Stock quantity < 0 for the SKU
     *
     * @param mixed $_
     * @param array $args
     * @return array
     */
    public function __invoke($_, array $args)
    {
        $search = $args['search'] ?? null;
        $productIds = $args['product_ids'] ?? null;
        $productTagIds = $args['product_tag_ids'] ?? null;
        $productCategoryIds = $args['product_category_ids'] ?? null;
        $sortBy = $args['sort_by'] ?? null;
        $sortDirection = $args['sort_direction'] ?? 'asc';

        // Get SKUs with negative total stock
        $skusWithNegativeStock = Stock::select('sku_id')
            ->groupBy('sku_id')
            ->havingRaw('SUM(qty) < 0')
            ->pluck('sku_id')
            ->toArray();

        // Base query for unfulfilled items with conditions
        $query = SellOrderItem::query()
            ->with([
                'sku.attributeValues',
                'product.tags',
                'product.categories',
            ])
            ->where('fulfill_status', SellOrder::FULFILL_STATUS_NOT_FULFILLED)
            ->whereHas('sellOrder', function ($orderQuery) {
                $orderQuery->where('order_status_id', 1)
                    ->where('date', '>=', now()->subDays(90));
            })
            // Filter items where stock is less than 0
            ->whereIn('sku_id', $skusWithNegativeStock);

        // Apply filters
        $this->applyFilters($query, $search, $productIds, $productTagIds, $productCategoryIds);

        $items = $query->get();

        // Group items by SKU and calculate required quantities
        $groupedData = $this->groupItemsBySku($items);

        // Apply sorting
        $groupedData = $this->applySorting($groupedData, $sortBy, $sortDirection);

        return $groupedData;
    }

    /**
     * Apply filters to the query
     *
     * @param \Illuminate\Database\Eloquent\Builder $query
     * @param string|null $search
     * @param array|null $productIds
     * @param array|null $productTagIds
     * @param array|null $productCategoryIds
     * @return void
     */
    protected function applyFilters($query, $search, $productIds, $productTagIds, $productCategoryIds): void
    {
        // Search filter
        if (!empty($search)) {
            $query->where(function ($q) use ($search) {
                // Search in product name
                $q->whereHas('product', function ($productQuery) use ($search) {
                    $productQuery->where('name', 'like', "%{$search}%");
                })
                // Search in SKU code
                ->orWhereHas('sku', function ($skuQuery) use ($search) {
                    $skuQuery->where('sku_code', 'like', "%{$search}%");
                })
                // Search in SKU attribute values
                ->orWhereHas('sku.attributeValues', function ($attrQuery) use ($search) {
                    $attrQuery->where('value', 'like', "%{$search}%");
                });
            });
        }

        // Product IDs filter
        if (!empty($productIds)) {
            $query->whereIn('product_id', $productIds);
        }

        // Product tag IDs filter
        if (!empty($productTagIds)) {
            $query->whereHas('product.tags', function ($tagQuery) use ($productTagIds) {
                $tagQuery->whereIn('product_tags.id', $productTagIds);
            });
        }

        // Product category IDs filter
        if (!empty($productCategoryIds)) {
            $query->whereHas('product.categories', function ($categoryQuery) use ($productCategoryIds) {
                $categoryQuery->whereIn('categories.id', $productCategoryIds);
            });
        }
    }

    /**
     * Apply sorting to grouped data
     *
     * @param array $groupedData
     * @param string|null $sortBy
     * @param string $sortDirection
     * @return array
     */
    protected function applySorting(array $groupedData, ?string $sortBy, string $sortDirection): array
    {
        if (empty($sortBy)) {
            return $groupedData;
        }

        usort($groupedData, function ($a, $b) use ($sortBy, $sortDirection) {
            $valueA = null;
            $valueB = null;

            switch ($sortBy) {
                case 'product_name':
                    $valueA = $a['product']->name ?? '';
                    $valueB = $b['product']->name ?? '';
                    break;

                case 'total_qty':
                    $valueA = $a['total_qty'] ?? 0;
                    $valueB = $b['total_qty'] ?? 0;
                    break;

                default:
                    return 0;
            }

            // Compare values
            if ($valueA == $valueB) {
                return 0;
            }

            if ($sortDirection === 'asc') {
                return $valueA <=> $valueB;
            } else {
                return $valueB <=> $valueA;
            }
        });

        return $groupedData;
    }

    /**
     * Group items by SKU and calculate needed quantities
     *
     * @param \Illuminate\Database\Eloquent\Collection $items
     * @return array
     */
    protected function groupItemsBySku($items): array
    {
        $grouped = [];

        foreach ($items as $item) {
            $skuId = $item->sku_id;

            if (!isset($grouped[$skuId])) {
                // Get total stock for this SKU across all inventories
                $totalStock = Stock::where('sku_id', $skuId)->sum('qty');

                $grouped[$skuId] = [
                    'sku' => $item->sku,
                    'product' => $item->product,
                    'total_qty' => abs($totalStock), // Quantity needed to balance inventory to 0
                    'current_stock' => $totalStock,
                    'needed_qty' => abs($totalStock), // Same as total_qty (items already deducted from stock)
                    'orders_count' => 0,
                ];
            }

            $grouped[$skuId]['orders_count']++;
        }

        return array_values($grouped);
    }
}

