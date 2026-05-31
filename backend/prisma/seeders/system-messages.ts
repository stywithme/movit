import type { PrismaClient } from '@prisma/client';
import { buildCatalogSystemMessages } from './system-messages-catalog';
import { normalizeMessageContent } from './utils';

type SysMsg = {
  code: string;
  description: string;
  content: { ar: string; en: string };
  context?: string | null;
};

/**
 * Fixed-code system messages for mobile training UI / TTS (synced via mobile sync).
 * Keys and descriptions are immutable in the dashboard; text and audio are editable.
 */
const CORE_SYSTEM_MESSAGES: SysMsg[] = [
  {
    code: 'training_countdown_3',
    description: 'Spoken digit for countdown step 3 before exercise start',
    content: { ar: '3', en: '3' },
  },
  {
    code: 'training_countdown_2',
    description: 'Spoken digit for countdown step 2 before exercise start',
    content: { ar: '2', en: '2' },
  },
  {
    code: 'training_countdown_1',
    description: 'Spoken digit for countdown step 1 before exercise start',
    content: { ar: '1', en: '1' },
  },
  {
    code: 'training_countdown_go',
    description: 'Start signal after countdown (TTS / audio)',
    content: { ar: 'ابدأ!', en: 'Go!' },
  },
  {
    code: 'training_go_overlay',
    description: 'Large on-screen text during GO animation overlay',
    content: { ar: 'انطلق!', en: 'GO!' },
  },
  {
    code: 'training_pose_confirmed',
    description: 'After pose validation passes, before countdown',
    content: { ar: 'ممتاز، استعد!', en: 'Great, get ready!' },
  },
  {
    code: 'training_target_reached',
    description: 'Rep goal completed; use {n} for rep count',
    content: { ar: 'أحسنت! اكتملت {n} تكرار', en: 'Great job! {n} reps completed' },
  },
  {
    code: 'training_streak_excellent',
    description: 'Motivation after long correct-rep streak',
    content: { ar: 'ممتاز! استمر!', en: 'Excellent! Keep going!' },
  },
  {
    code: 'training_streak_great',
    description: 'Motivation after medium correct-rep streak',
    content: { ar: 'أداء رائع!', en: 'Great form!' },
  },
  {
    code: 'training_streak_good',
    description: 'Motivation after short correct-rep streak',
    content: { ar: 'جيد!', en: 'Good!' },
  },
  {
    code: 'training_hold_stay',
    description: 'Hold exercise: grace period warning',
    content: { ar: 'ابق ثابتاً!', en: 'Stay in position!' },
  },
  {
    code: 'training_hold_resumed',
    description: 'Hold exercise: user recovered position',
    content: { ar: 'أحسنت، استمر', en: 'Good, keep holding' },
  },
  {
    code: 'training_hold_completed',
    description: 'Hold completed; use {n} for seconds',
    content: { ar: 'أحسنت! ثبات {n} ثانية', en: 'Great job! Held for {n} seconds' },
  },
  {
    code: 'training_hold_failed',
    description: 'Hold failed — position lost',
    content: { ar: 'فقدت الوضعية. حاول مجدداً', en: 'Position lost. Try again' },
  },
  {
    code: 'visibility_joints_not_visible',
    description: 'Warning when joints invisible; use {joints} for list',
    content: { ar: '⚠️ {joints} غير مرئية', en: '⚠️ {joints} not visible' },
  },
  {
    code: 'visibility_pause_full_body',
    description: 'Pause overlay — ask user to show full body',
    content: {
      ar: '⏸️ تأكد من ظهور جسمك بالكامل في الإطار',
      en: '⏸️ Make sure your full body is visible in frame',
    },
  },
  {
    code: 'training_pause_visibility_joints',
    description: 'Auto-pause reason: required joints not visible',
    content: { ar: 'المفاصل المطلوبة غير مرئية', en: 'Required joints not visible' },
  },
  {
    code: 'training_pause_no_pose_long',
    description: 'Auto-pause reason: no pose detected too long',
    content: { ar: 'لم يتم اكتشاف وضعية لفترة طويلة', en: 'No pose detected for too long' },
  },
  {
    code: 'training_pause_manual',
    description: 'User paused training',
    content: { ar: 'تم إيقاف التمرين مؤقتاً', en: 'Training paused' },
  },
  {
    code: 'training_no_pose_return_camera',
    description: 'TTS when no pose — return to camera',
    content: { ar: 'عد إلى الكاميرا', en: 'Return to the camera' },
  },
  {
    code: 'training_no_pose_countdown_warning',
    description: 'Video mode warning; use {seconds} for remaining seconds',
    content: {
      ar: 'لم يتم اكتشاف وضعية! العودة خلال {seconds}ث...',
      en: 'No pose detected! Return in {seconds}s...',
    },
  },
  {
    code: 'training_setup_return_start_pose',
    description: 'Setup countdown frozen — return to start pose',
    content: { ar: 'عد إلى وضعية البداية', en: 'Return to the start pose' },
  },
  // Joint display names for visibility warnings (optional server override)
  { code: 'visibility_joint_left_elbow', description: 'Joint label: left elbow', content: { ar: 'الكوع الأيسر', en: 'Left Elbow' } },
  { code: 'visibility_joint_right_elbow', description: 'Joint label: right elbow', content: { ar: 'الكوع الأيمن', en: 'Right Elbow' } },
  { code: 'visibility_joint_left_shoulder', description: 'Joint label: left shoulder', content: { ar: 'الكتف الأيسر', en: 'Left Shoulder' } },
  { code: 'visibility_joint_right_shoulder', description: 'Joint label: right shoulder', content: { ar: 'الكتف الأيمن', en: 'Right Shoulder' } },
  { code: 'visibility_joint_left_hip', description: 'Joint label: left hip', content: { ar: 'الورك الأيسر', en: 'Left Hip' } },
  { code: 'visibility_joint_right_hip', description: 'Joint label: right hip', content: { ar: 'الورك الأيمن', en: 'Right Hip' } },
  { code: 'visibility_joint_left_shoulder_cross', description: 'Joint label: left shoulder cross', content: { ar: 'الكتف الأيسر (تقاطع)', en: 'Left Shoulder Cross' } },
  { code: 'visibility_joint_right_shoulder_cross', description: 'Joint label: right shoulder cross', content: { ar: 'الكتف الأيمن (تقاطع)', en: 'Right Shoulder Cross' } },
  { code: 'visibility_joint_left_hip_cross', description: 'Joint label: left hip cross', content: { ar: 'الورك الأيسر (تقاطع)', en: 'Left Hip Cross' } },
  { code: 'visibility_joint_right_hip_cross', description: 'Joint label: right hip cross', content: { ar: 'الورك الأيمن (تقاطع)', en: 'Right Hip Cross' } },
  { code: 'visibility_joint_left_knee', description: 'Joint label: left knee', content: { ar: 'الركبة اليسرى', en: 'Left Knee' } },
  { code: 'visibility_joint_right_knee', description: 'Joint label: right knee', content: { ar: 'الركبة اليمنى', en: 'Right Knee' } },
  { code: 'visibility_joint_left_wrist', description: 'Joint label: left wrist', content: { ar: 'المعصم الأيسر', en: 'Left Wrist' } },
  { code: 'visibility_joint_right_wrist', description: 'Joint label: right wrist', content: { ar: 'المعصم الأيمن', en: 'Right Wrist' } },
  { code: 'visibility_joint_left_ankle', description: 'Joint label: left ankle', content: { ar: 'الكاحل الأيسر', en: 'Left Ankle' } },
  { code: 'visibility_joint_right_ankle', description: 'Joint label: right ankle', content: { ar: 'الكاحل الأيمن', en: 'Right Ankle' } },
];

/** Core training/visibility strings + generated catalog (setup directions/postures/regions/joints, numerals 4–30). */
const SYSTEM_MESSAGES: SysMsg[] = [...CORE_SYSTEM_MESSAGES, ...buildCatalogSystemMessages()];

export async function seedSystemMessages(prisma: PrismaClient): Promise<void> {
  for (const m of SYSTEM_MESSAGES) {
    const normalized = normalizeMessageContent(m.content);
    await prisma.feedbackMessageTemplate.upsert({
      where: { code: m.code },
      create: {
        code: m.code,
        category: 'system',
        context: m.context ?? 'general',
        description: m.description,
        content: normalized as object,
        tags: ['system', 'mobile'],
        isSystem: true,
        isActive: true,
      },
      // Preserve dashboard edits to text/audio; only refresh metadata
      update: {
        description: m.description,
        category: 'system',
        isSystem: true,
        isActive: true,
      },
    });
  }
  console.log(`✅ System message templates upserted (${SYSTEM_MESSAGES.length})`);
}
