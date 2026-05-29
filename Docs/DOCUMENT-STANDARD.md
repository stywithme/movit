# معيار توثيق POSE

> **Status:** `ACTIVE` — يصف كيفية كتابة وصيانة كل وثائق `Docs/`.

| | |
|---|---|
| **Applies to** | جميع ملفات `.md` تحت `Docs/` |
| **Verified** | 2026-05-29 |

---

## 1. قيم الحالة (Status)

| Status | المعنى | أين |
|--------|--------|-----|
| `ACTIVE` | يطابق الكود اليوم أو عقداً ملزماً | `00-Active-Reference/` |
| `ROADMAP` | قرار/خطة لم تُثبت في الكود بعد | `02-Roadmaps-And-Plans/` |
| `BUSINESS` | تخطيط منتج/سوق — ليس مواصفة تقنية | `01-Business-Planning/` |
| `ARCHIVED` | منفّذ أو مستبدل — للتاريخ فقط | `03-Implemented-Archive/`, `99-Archive/` |
| `RESEARCH` | مرجع علمي/استكشافي | `04-Research/` |

---

## 2. الترويسة الإلزامية

### وثائق حية (`ACTIVE`)

```markdown
| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | وصف موجز (مثلاً: Exercise JSON contract) |
| **Code** | `path/to/File.kt`, `backend/...` |
| **Supersedes** | روابط أرشيف إن وُجدت |
| **Verified** | YYYY-MM-DD |
```

### وثائق مؤرشفة (`ARCHIVED`)

```markdown
> **Status:** `ARCHIVED` — not current product truth.
> **Current SSOT:** [path](path)
> **Archived:** YYYY-MM-DD
```

### خطط (`ROADMAP`)

```markdown
| | |
|---|---|
| **Status** | `ROADMAP` |
| **SSOT for** | — (ليست as-built) |
| **As-built** | رابط للمرجع الحي إن وُجد |
| **Verified** | YYYY-MM-DD |
```

---

## 3. قواعد المحتوى

1. **فصل as-built عن المستقبل** — قسمان واضحان أو ملفان (مثل المقاييس).
2. **لا تكرار** — إن وُجد كتالوج، لا تُعاد تعريف كل مقياس في خطة التحسين.
3. **Code map** — كل مقياس/مسار حرج يربط بملف Kotlin/TS/Prisma.
4. **اللغة** — عربي للمنتج، إنجليزي لأسماء الكود والمسارات.
5. **التحقق** — عند تغيير الكود، حدّث `Verified` في الوثيقة المتأثرة.

---

## 4. أين أضع وثيقة جديدة؟

| نوع المحتوى | المجلد |
|-------------|--------|
| عقد API/JSON، محرك، as-built | `00-Active-Reference/` |
| قرار تجاري، beta، retention | `01-Business-Planning/` |
| ميزة لم تُبنَ بعد | `02-Roadmaps-And-Plans/` |
| خطة نُفّذت | `03-Implemented-Archive/` + بانر |
| ورقة/بحث | `04-Research/` |
| لوج تشخيص | **لا** — استخدم issues / أدوات التتبع |

---

## 5. فهارس SSOT (لا تُنشئ ملفاً رابعاً لنفس الموضوع)

| الموضوع | الفهرس |
|---------|--------|
| المقاييس | [`00-Active-Reference/Metrics/README.md`](00-Active-Reference/Metrics/README.md) |
| رحلة المستخدم | [`00-Active-Reference/Product-Master/Journey-Index.md`](00-Active-Reference/Product-Master/Journey-Index.md) |
| كل الوثائق | [`README.md`](README.md) |
