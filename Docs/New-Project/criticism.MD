الخطة قوية كاتجاه، لكن بصورتها الحالية أقرب لتصميم نظام ممتاز على الورق أكثر من كونها مسار تنفيذ منتج جاهز يعطي “تقدم ملموس قابل للقياس” من أول إصداراته.
Findings (مرتبة حسب الخطورة)
[Critical] الحلقة الأساسية Assess → Prescribe → Train → Reassess مكسورة فعليًا الآن: الأندرويد يحسب نتيجة التقييم محليًا ثم يعرضها فقط، بدون رفعها للباك إند أو ربطها تلقائيًا ببرنامج.

val engine = AssessmentEngine()
val result = engine.process(
    reports = completedReports,
    userId = getUserId(),
    assessmentType = assessmentType,
    parqPassed = parqPassed,
    parqFlags = parqFlags,
    painFlags = painFlags,
    previousResult = null,
    durationMs = System.currentTimeMillis() - startTimeMs
)

val intent = AssessmentResultActivity.createIntent(this, result)
startActivity(intent)
finish()

[Critical] الخطة تفترض ActivePlan متعدد البرامج، لكن الواقع الحالي يدعم برنامجًا نشطًا واحدًا فقط (UserProgram.isActive): هذا يمنع سلاسل مثل “Therapeutic → Training → Reassessment”.

async enrollUser(userId: string, programId: string, name?: LocalizedText) {
  const prisma = await getPrisma();

  return prisma.$transaction(async (tx) => {
    await tx.userProgram.updateMany({
      where: { userId, isActive: true },
      data: { isActive: false },
    });

    return tx.userProgram.create({
      data: {
        userId,
        programId,
        name: (name as object) || undefined,
        isActive: true,
      },
    });
  });
}

[High] التقارير مرتبطة بالبرنامج النشط فقط: بمجرد انتقال المستخدم لبرنامج جديد، تتعقد قراءة تاريخ البرنامج السابق عبر API الحالي.

const userProgram = await prisma.userProgram.findFirst({
  where: { userId, programId, isActive: true },
  include: { ... }
});

if (!userProgram || !userProgram.program) {
  return { success: false, scope, summary: {} as never, error: 'No active enrollment found for this program' };
}

[High] تناقض داخلي في منطق الـ Levels داخل الخطة نفسها: عندك جدول عتبات مختلف عن دالة التحويل، وهذا يضرب الثقة والاتساق.

| Level | Number | FitnessLevel | BodyScore Range | Description |
|-------|--------|-------------|-----------------|-------------|
| Foundation | 1 | needs_rehab / limited | 0–35 | Safety first. Corrective focus. Bodyweight only. |
| Building | 2 | limited / average | 25–55 | Basic movement patterns. Light resistance. |
| Intermediate | 3 | average / good | 45–75 | Progressive overload. Moderate intensity. |
| Advanced | 4 | good / excellent | 65–90 | Complex movements. Heavy loads. |
| Elite | 5 | excellent | 85–100 | Performance optimization. Peak training. |

score 0-25   → Level 1 (Foundation)
score 25-45  → Level 2 (Building)
score 45-65  → Level 3 (Intermediate)
score 65-85  → Level 4 (Advanced)
score 85-100 → Level 5 (Elite)

[High] واجهة المنتج ما زالت POC في نقاط حساسة للثقة: إحصائيات الـ Home Mock، الاشتراك/الالتحاق غير مكتمل، ولغة الـ PAR-Q ثابتة.

// Mock stats - Replace with real data
binding.tvWeeklyWorkouts.text = "5"
binding.tvFormScore.text = "87%"
binding.tvStreak.text = "12🔥"

// Last exercise
binding.tvLastExercise.text = getString(R.string.sample_exercise_squats)
binding.tvLastProgress.text = getString(R.string.sets_completed_format, 2, 3)

holder.btnSubscribe.setOnClickListener {
    // TODO: Implement actual subscription via backend API
    // For now, navigate to program detail for enrollment
    openProgramDetail(program)
}

private val language: String get() = "en" // TODO: Get from user preferences

[Medium] دلالات المقاييس غير موحدة بين الجداول (avgFormScore أحيانًا x10 وأحيانًا 0-100): هذا خطر مباشر على أي Progression Engine.

avgAccuracy       Float?   // Completion rate (reps done / reps planned)
avgFormScore      Float?   // Form quality score (0-100, from rep-level scoring)
report            Json?

avgFormScore          Int      // Average form score × 10
avgAlignmentAccuracy  Int      // Average alignment × 10

[Medium] جاهزية production الأمنية/التشغيلية أقل من المطلوب لمنتج “حقيقي” (أسرار JWT الافتراضية + analytics processor شبه فارغ).

const JWT_SECRET = process.env.JWT_SECRET || 'your-super-secret-jwt-key-change-in-production';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'your-super-secret-refresh-key-change-in-production';

case 'session_analytics':
  this.logger.log(`Processing session analytics: ${JSON.stringify(job.data)}`);
  break;
case 'daily_summary':
  this.logger.log(`Processing daily summary: ${JSON.stringify(job.data)}`);
  break;
default:
  this.logger.warn(`Unknown analytics job: ${job.name}`);

  [Medium] maintainability risk واضح: TrainingActivity (~2711 سطر)، ProgramSessionActivity (~1322)، TrainingEngine (~1274)، مع تغطية اختبارية محدودة جدًا (عمليًا ملفات اختبار قليلة جدًا في backend ولا يوجد مجلدات test/androidTest في android-poc).

الخلاصة الموضوعية
الأساس التقني ممتاز في القياس الحركي، التقارير، والـ offline sync.
لكن القيمة المنتجية “تقدم قابل للقياس + مسار واضح” لن تظهر للمستخدم قبل إغلاق فجوة الربط بين التقييم، الوصفة، والبرنامج الفعلي.
أخطر مشكلة ليست “غياب AI”، بل غياب تكامل lifecycle end-to-end.
تعديل الخطة (عملي + تجاري)
Phase 0 (لازم قبل كل شيء): إغلاق الحلقة: رفع BodyScanResult للباك إند، جلب آخر تقييم/المقارنة، وربط CTA مباشر “نتيجتك → برنامجك الموصى”.
Phase 1: توحيد قاموس المقاييس والوحدات (خصوصًا avgFormScore) وإنشاء Contract واحد بين Android/Backend.
Phase 2: UserLevelProfile فقط كـ vertical slice صغير (بدون تعقيد كامل ActivePlan)، مع شاشة واضحة “مستواك + العامل المقيِّد + الخطوة القادمة”.
Phase 3: Prescription V1 rule-based بسيط (برنامج رئيسي + بديل آمن) بدل القفز مباشرة لنظام قواعد ضخم.
Phase 4: ActivePlan + transitions + reassessment triggers (بعد إثبات أن V1 يحسن الالتزام).
Phase 5: Progression Engine تدريجيًا (3 قواعد فقط أولًا: زيادة حمل، زيادة تكرار، deload safety).
مقاييس منتج لازم تتقاس من الآن
North Star: نسبة المستخدمين الذين يحققون تحسنًا ملموسًا خلال 28 يومًا (مثلا +5 نقاط موثوقة).
Leading indicators: معدل إكمال جلسات الأسبوع، الالتزام D7/D30، ونسبة إكمال إعادة التقييم.
Safety guardrails: تكرار pain flags، وتراجع جودة الأداء مع زيادة الحمل.

