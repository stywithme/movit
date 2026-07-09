# Movit — فهرس الوثائق



> **آخر تنظيم:** يونيو 2026  

> **المنتج الجوال:** **Movit** — deep links `movit://`  

> فهرس خفيف — التفاصيل في المجلدات المرتبطة، وليس هنا.



---



## ابدأ من هنا



| إذا كنت… | اقرأ أولاً |

|----------|------------|

| مالك منتج / تخطيط تجاري | [`01-Business-Planning/README.md`](01-Business-Planning/README.md) |

| مطوّر Android / Backend | [`00-Active-Reference/README.md`](00-Active-Reference/README.md) |

| مدرب / محتوى | [`05-Guides/Trainer-Guide/Complete-System-Guide-AR.md`](05-Guides/Trainer-Guide/Complete-System-Guide-AR.md) |

| ماذا تغيّر في المنتج مؤخراً؟ | [`03-Evolution/README.md`](03-Evolution/README.md) |

| معيار الكتابة والحالات | [`DOCUMENT-STANDARD.md`](DOCUMENT-STANDARD.md) |



---



## هيكل المجلدات



```

Docs/

├── 00-Active-Reference/     ← as-built (كود + عقود + تشغيل)

├── 01-Business-Planning/    ← منتج، سوق، beta

├── 02-Roadmaps-And-Plans/   ← خطط لم تُنفَّذ

├── 03-Evolution/            ← سجل قرارات هيكلية (ماذا تغيّر ولماذا)

├── 03-Implemented-Archive/  ← خطط نُفِّذت (تاريخ)

├── 04-Research/             ← أوراق، ACSM، برامج

├── 05-Guides/               ← MediaPipe، دليل المدرب

├── 06-Assets/               ← صور، خطوط، عينات — ليست SSOT

├── 98-Redirects/            ← مسارات قديمة (إعادة توجيه فقط)

└── 99-Archive/              ← نسخ قديمة، ميزات ملغاة

```



---



## فهارس SSOT (لا تكرار)



| الموضوع | الفهرس |

|---------|--------|

| كل as-built تقني | [`00-Active-Reference/README.md`](00-Active-Reference/README.md) — جدول Topic → ملف |

| المقاييس | [`00-Active-Reference/Metrics/README.md`](00-Active-Reference/Metrics/README.md) |

| رحلة المستخدم | [`00-Active-Reference/Product-Master/Journey-Index.md`](00-Active-Reference/Product-Master/Journey-Index.md) |

| الدفع (MyFatoorah) | [`00-Active-Reference/Operations/Payment-gateway/README.md`](00-Active-Reference/Operations/Payment-gateway/README.md) — `00`–`06` (لا `PAYMENT-RUNBOOK`؛ كان خاصاً بالحجز المحذوف) |



---



## قواعد صيانة (مختصرة)



1. **مرجع حي واحد لكل موضوع** — لا ملف ثالث للمقاييس أو التقارير.

2. **خطة منفذة** → `03-Implemented-Archive/`؛ **قرار يغيّر المنتج** → `03-Evolution/`.

3. **مسار قديم** → `98-Redirects/` فقط (لا stubs في جذر `Docs/`).

4. **لوج تشخيص** → لا يدخل `Docs/`.



التفاصيل: [`DOCUMENT-STANDARD.md`](DOCUMENT-STANDARD.md).



---



## إحصاء سريع



| النوع | تقريباً |

|-------|---------|

| ملفات Markdown | ~203 |

| PDFs بحثية | ~76 |

| صور تدريب ML | ~458 |

| أصول UI | ~26 |



---



## إعادة التوجيه



انظر [`98-Redirects/README.md`](98-Redirects/README.md).



| المسار القديم | الوجهة |

|---------------|--------|

| `Docs/Exercise-JSON-Schema.md` | `98-Redirects/` → `00-Active-Reference/Contracts/` |

| `Docs/New-Project/Planning/` | `01-Business-Planning/` |

| `Docs/American-College-Prescription/` | `04-Research/ACSM-Prescription/` |


