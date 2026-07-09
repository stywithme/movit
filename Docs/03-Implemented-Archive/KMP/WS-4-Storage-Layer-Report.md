# WS-4 — قرار طبقة التخزين (Phase Pre-07)

آخر تحديث: **2026-06-10** · **أُرشف:** 2026-06-22 (القرار مُنفَّذ — راجع [`KMP-Mobile-As-Built.md`](../../00-Active-Reference/Architecture-As-Built/KMP-Mobile-As-Built.md) §4)

## القرار

| البديل | الحكم |
|--------|--------|
| **SQLDelight** | **معتمد** — KMP أصيل، SQL صريح، استعلامات outbox/cache/metadata |
| Room-KMP | مرفوض — ثقل Android-centric، تبعية أوسع |
| JSON في prefs فقط | مرفوض — لا يدعم queue مُهيكل ولا drift queries |

## Schema (`MovitDatabase` — `com.movit.core.data.db`)

| جدول | الغرض | مستهلك |
|------|--------|--------|
| `outbox_entry` | طابور كتابات offline | WS-2 `OfflineWriteQueue` |
| `json_cache_entry` | كاش JSON مُفاتَح `(store, key)` | sync repos عبر `MovitCachePolicy` |
| `sync_metadata` | نسخة/طابع مزامنة لكل `scope` | WS-3 drift / orchestrator |
| `session_journal` | سجل جلسة التدريب | training journal / uploads |

مفاتيح الكاش **لم تتغيّر** — نفس `MovitCacheKeys` ونفس أسماء `store` في legacy prefs.

## الملفات الرئيسية

- `local/MovitLocalStore.kt` — العقد الموحّد
- `local/SqlDelightMovitLocalStore.kt` — تنفيذ SQLDelight
- `local/MigratingMovitLocalStore.kt` — ترحيل lazy من `MovitPlatformBindings.readCache`
- `local/CreateMovitLocalStore.{android,ios}.kt` — `expect/actual` للإنتاج
- `local/InMemoryMovitLocalStore.kt` / `FakeMovitLocalStore.kt` — اختبارات
- `sqldelight/.../db/{Outbox,JsonCacheEntry,SyncMetadata,SessionJournal}.sq`

## مسار الترحيل

1. عند `MovitData.install()`: `MigratingMovitLocalStore.migrateKnownCachesFromPlatform()` ينسخ المفاتيح الثابتة المعروفة.
2. عند كل `readString`: إن لم يوجد في SQL → يُقرأ من prefs → يُكتب في SQL (lazy).
3. كل `writeString` يذهب إلى SQL فقط (مصدر الحقيقة الجديد).
4. `remove` يمسح SQL + prefs legacy.
5. `MovitPlatformBindings.readCache/writeCache` **تبقى** لـ auth prefs وغيرها خارج `MovitCacheKeys`.

## API لـ WS-2 / WS-3

```kotlin
// كاش JSON (موحّد عبر MovitCachePolicy)
store.readString(namespace, key)
store.writeString(namespace, key, value)
store.remove(namespace, key)

// Outbox (WS-2)
suspend fun insertOutbox(entry: OutboxEntry)
suspend fun listPendingOutbox(): List<OutboxEntry>
suspend fun updateOutboxStatus(id, status, attempts)
suspend fun countOutboxByStatus(status): Long

// Sync metadata (WS-3 drift)
store.readSyncMetadata(scope)
store.writeSyncMetadata(scope, version, lastSyncAt)
```

الوصول من التطبيق: `MovitData.localStore` بعد `install()`.
