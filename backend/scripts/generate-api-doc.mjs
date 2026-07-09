import fs from 'fs';
import path from 'path';

const OUT = path.resolve('../Docs/00-Active-Reference/Contracts/API_ENDPOINTS.md');

/** @type {Record<string, string>} */
const PURPOSE = {
  'GET /': 'Health check',
  'POST /admin/auth/login': 'Admin login (session cookies)',
  'POST /admin/auth/logout': 'Admin logout',
  'GET /admin/auth/profile': 'Current admin profile',
  'PUT /admin/auth/profile': 'Update admin profile',
  'POST /admin/auth/request-reset': 'Request password reset email',
  'POST /admin/auth/reset-password': 'Reset password with token',
  'GET /admins': 'List admins (search, status, pagination)',
  'POST /admins': 'Create admin',
  'GET /admins/:id': 'Admin details',
  'PUT /admins/:id': 'Update admin',
  'PUT /admins/:id/password': 'Change admin password',
  'DELETE /admins/:id': 'Soft-delete admin',
  'GET /admin/permissions': 'List all permissions',
  'GET /admin/permissions/roles': 'List roles',
  'GET /admin/permissions/roles/:id': 'Role with assigned permissions',
  'POST /admin/permissions/roles': 'Create role',
  'PUT /admin/permissions/roles/:id': 'Update role name/permissions',
  'DELETE /admin/permissions/roles/:id': 'Delete role',
  'GET /admin/analytics/overview': 'Platform overview KPIs',
  'GET /admin/analytics/users': 'User growth and engagement',
  'GET /admin/analytics/activation': 'Activation funnel metrics',
  'GET /admin/analytics/retention': 'Retention cohorts',
  'GET /admin/analytics/training': 'Training activity metrics',
  'GET /admin/analytics/progression': 'Progression analytics',
  'GET /admin/analytics/revenue': 'Subscription revenue',
  'GET /admin/analytics/safety': 'Safety incident metrics',
  'GET /admin/analytics/content': 'Content usage analytics',
  'GET /admin/analytics/programs/:id': 'Single program analytics',
  'GET /admin/analytics/users/:id/report': 'Per-user analytics report',
  'GET /admin/analytics/workout-executions/:id/report': 'Workout execution detail report',
  'GET /admin/analytics/rules': 'Progression rule analytics',
  'GET /admin/analytics/programs': 'Program catalog analytics',
  'GET /admin/analytics/user-trends': 'User trend time series',
  'GET /admin/analytics/platform': 'Platform-wide summary',
  'GET /admin/analytics/levels': 'Level distribution analytics',
  'GET /admin/analytics/assessments': 'Assessment completion analytics',
  'GET /admin/analytics/level-transitions': 'Level transition analytics',
  'GET /admin/assessment-templates': 'List assessment templates',
  'POST /admin/assessment-templates': 'Create assessment template',
  'GET /admin/assessment-templates/:id': 'Assessment template details',
  'PUT /admin/assessment-templates/:id': 'Update assessment template',
  'DELETE /admin/assessment-templates/:id': 'Delete assessment template',
  'POST /admin/assessment-templates/:id/publish': 'Publish assessment template',
  'DELETE /admin/assessment-templates/:id/publish': 'Unpublish assessment template',
  'POST /admin/assessment-templates/:id/exercises': 'Add exercise to template',
  'PUT /admin/assessment-templates/:id/exercises/reorder': 'Reorder template exercises',
  'PUT /admin/assessment-templates/:id/exercises/:entryId': 'Update template exercise entry',
  'DELETE /admin/assessment-templates/:id/exercises/:entryId': 'Remove exercise from template',
  'GET /mobile/assessment-templates/resolve': 'Resolve assessment template for user',
  'POST /assessment': 'Submit assessment session',
  'GET /assessment/latest': 'Latest assessment result',
  'GET /assessment/history': 'Assessment history',
  'GET /assessment/progress': 'Assessment progress summary',
  'GET /assessment/:id': 'Assessment session details',
  'DELETE /assessment/:id': 'Delete assessment session',
  'GET /attributes': 'List attribute types',
  'GET /attributes/lookup': 'All lookup data for admin dropdowns',
  'POST /attributes': 'Create attribute type',
  'GET /attributes/:code/values': 'Values for attribute code (e.g. muscles)',
  'POST /attributes/:code/values': 'Add value to attribute type',
  'GET /attributes/:id': 'Attribute type details',
  'PUT /attributes/:id': 'Update attribute type',
  'DELETE /attributes/:id': 'Delete attribute type',
  'PUT /attributes/values/:id': 'Update attribute value',
  'DELETE /attributes/values/:id': 'Delete attribute value',
  'POST /mobile/auth/register': 'Register new user',
  'POST /mobile/auth/login': 'Login with email/password',
  'POST /mobile/auth/google': 'Google social login/register',
  'POST /mobile/auth/refresh': 'Refresh access token',
  'POST /mobile/auth/forgot-password': 'Request password reset email',
  'POST /mobile/auth/reset-password': 'Reset password with token',
  'POST /mobile/auth/logout': 'Logout (revoke refresh token)',
  'DELETE /mobile/auth/logout': 'Logout all devices',
  'GET /mobile/auth/profile': 'Current user profile',
  'PATCH /mobile/auth/profile': 'Update profile (name, avatar)',
  'PATCH /mobile/auth/settings': 'Update app settings (language, voice)',
  'POST /mobile/auth/change-password': 'Change password',
  'DELETE /mobile/auth/account': 'Permanently delete account',
  'GET /exercises': 'List exercises (search, filters, pagination)',
  'POST /exercises': 'Create exercise',
  'GET /exercises/published': 'List published exercises only',
  'POST /exercises/bulk/unpublish': 'Bulk unpublish exercises',
  'POST /exercises/bulk/delete': 'Bulk delete exercises',
  'GET /exercises/:id': 'Exercise details',
  'PUT /exercises/:id': 'Update exercise',
  'DELETE /exercises/:id': 'Delete exercise',
  'PUT /exercises/:id/publish': 'Publish exercise',
  'DELETE /exercises/:id/publish': 'Unpublish exercise',
  'GET /exercises/:id/config': 'Android-compatible exercise config JSON',
  'GET /exercises/:id/substitutions': 'Admin substitution mappings',
  'GET /mobile/level-profile': 'Current user level profile',
  'GET /mobile/level-profile/history': 'Level change history',
  'GET /mobile/level-profile/levels': 'Available levels reference',
  'GET /admin/levels': 'List training levels',
  'POST /admin/levels': 'Create level',
  'PUT /admin/levels/reorder': 'Reorder levels',
  'PUT /admin/levels/:id': 'Update level',
  'DELETE /admin/levels/:id': 'Delete level',
  'GET /messages': 'List voice/UI messages',
  'POST /messages': 'Create message',
  'POST /messages/bulk-audio': 'Generate bulk TTS audio',
  'POST /messages/bulk-audio/preview': 'Preview bulk TTS generation',
  'POST /messages/ai/translate': 'AI translate message text',
  'POST /messages/ai/tts': 'Generate TTS for message',
  'DELETE /messages/ai/tts': 'Clear TTS cache',
  'GET /messages/:id': 'Message details',
  'PUT /messages/:id': 'Update message',
  'DELETE /messages/:id': 'Delete message',
  'GET /mobile/exercises/substitutions': 'User exercise substitutions',
  'GET /mobile/exercises/:slug/audio-manifest': 'Exercise audio manifest',
  'GET /mobile/explore': 'Explore screen data',
  'GET /mobile/home': 'Home screen (today workout, recent executions)',
  'GET /mobile/sync': 'Full mobile sync payload',
  'GET /mobile/plans': 'Active subscription plans (mobile)',
  'POST /admin/plans': 'Create subscription plan',
  'GET /admin/plans': 'List subscription plans',
  'GET /admin/plans/:id': 'Plan details',
  'PATCH /admin/plans/:id': 'Update plan',
  'DELETE /admin/plans/:id': 'Delete plan',
  'GET /pose-positions': 'List pose/camera positions (`/camera-positions` alias)',
  'GET /pose-positions/:id': 'Pose position details',
  'PUT /pose-positions/:id': 'Update pose position',
  'POST /mobile/prescription/recommend': 'Recommend program from assessment',
  'GET /mobile/programs': 'Published programs catalog',
  'GET /mobile/programs/:id/preview': 'Program preview before enroll',
  'GET /mobile/programs/:id': 'Program details',
  'POST /mobile/programs/:id/enroll': 'Enroll in program',
  'PUT /mobile/user-programs/:id': 'Update enrollment settings',
  'GET /mobile/user-programs/:id/progress-metrics': 'Enrollment progress metrics',
  'GET /mobile/user-programs/:id/effective-plan': 'Merged plan with overrides',
  'GET /mobile/user-programs/:id/overrides': 'List day overrides',
  'POST /mobile/user-programs/:id/overrides': 'Create day override',
  'POST /mobile/user-programs/:id/complete': 'Mark program complete',
  'DELETE /mobile/user-programs/:id/overrides/:overrideId': 'Delete day override',
  'GET /mobile/user-programs/:id/today': "Today's planned workouts",
  'GET /programs': 'List programs (admin)',
  'GET /programs/map': 'Program ID/slug map',
  'POST /programs': 'Create program',
  'GET /programs/:id': 'Program with weeks, days, planned workouts',
  'PUT /programs/:id': 'Update program metadata',
  'DELETE /programs/:id': 'Delete program',
  'POST /programs/:id/publish': 'Publish program',
  'DELETE /programs/:id/publish': 'Unpublish program',
  'POST /programs/:id/duplicate': 'Duplicate program',
  'POST /programs/:id/weeks': 'Add week',
  'PUT /programs/:id/weeks/:weekId': 'Update week',
  'DELETE /programs/:id/weeks/:weekId': 'Delete week',
  'POST /programs/:id/weeks/:weekId/copy-to/:targetWeek': 'Copy week structure',
  'POST /programs/:programId/weeks/:weekId/days': 'Add day to week',
  'PUT /programs/:programId/weeks/:weekId/days/:dayId': 'Update day',
  'DELETE /programs/:programId/weeks/:weekId/days/:dayId': 'Delete day',
  'POST /programs/:programId/weeks/:weekId/days/:dayId/planned-workouts': 'Add planned workout to day',
  'PUT /programs/:programId/planned-workouts/:plannedWorkoutId': 'Update planned workout',
  'DELETE /programs/:programId/planned-workouts/:plannedWorkoutId': 'Delete planned workout',
  'POST /programs/:programId/planned-workouts/:plannedWorkoutId/items': 'Add item to planned workout',
  'PUT /programs/:programId/planned-workouts/:plannedWorkoutId/items/:itemId': 'Update planned workout item',
  'DELETE /programs/:programId/planned-workouts/:plannedWorkoutId/items/:itemId': 'Delete planned workout item',
  'POST /programs/:programId/planned-workouts/:plannedWorkoutId/import-workout-template/:workoutTemplateId':
    'Import workout template into planned workout',
  'GET /admin/exercise-families': 'List exercise families',
  'GET /admin/exercise-families/:familyKey': 'Family details',
  'PATCH /admin/exercise-families/:familyKey/order': 'Reorder family',
  'PATCH /admin/exercise-families/:familyKey/rename': 'Rename family',
  'GET /admin/exercise-progression/archetypes': 'Progression archetypes',
  'GET /admin/exercise-progression/exercises': 'Exercises with progression profiles',
  'GET /admin/exercise-progression/profiles': 'All progression profiles',
  'GET /admin/exercise-progression/:exerciseId': 'Exercise progression profile',
  'POST /admin/exercise-progression/:exerciseId/generate': 'Auto-generate progression profile',
  'PUT /admin/exercise-progression/:exerciseId': 'Update progression profile',
  'PUT /admin/exercise-progression/:exerciseId/archetype': 'Set progression archetype',
  'POST /admin/exercise-progression/bulk-generate': 'Bulk generate profiles',
  'GET /admin/exercise-progression/:exerciseId/validate': 'Validate progression profile',
  'GET /admin/progression-rules': 'List progression rules',
  'POST /admin/progression-rules': 'Create progression rule',
  'GET /admin/progression-rules/:id': 'Progression rule details',
  'PUT /admin/progression-rules/:id': 'Update progression rule',
  'DELETE /admin/progression-rules/:id': 'Delete progression rule',
  'PUT /admin/progression-rules/:id/toggle': 'Enable/disable rule',
  'GET /mobile/progression/history': 'Progression change history',
  'GET /mobile/progression/recent': 'Recent progression events',
  'GET /mobile/progression/planned-workout/:plannedWorkoutId': 'Progression for completed block',
  'GET /mobile/progression/session/:sessionId': 'Progression for assessment session',
  'POST /mobile/progression/mark-seen': 'Mark progression notification seen',
  'GET /mobile/reassessment/upcoming': 'Upcoming reassessment',
  'GET /mobile/reassessment/history': 'Reassessment history',
  'POST /mobile/reassessment/request': 'Request reassessment',
  'GET /mobile/reports/dashboard': 'User dashboard report',
  'GET /mobile/reports/metrics': 'User metrics report',
  'GET /mobile/subscriptions/mine': 'Current subscription',
  'GET /mobile/subscriptions/status': 'Subscription status check',
  'POST /mobile/subscriptions/checkout': 'Start MyFatoorah checkout',
  'GET /mobile/subscriptions/checkout/:id': 'Checkout session status',
  'POST /mobile/subscriptions/google-play/verify': 'Verify Google Play purchase',
  'POST /mobile/subscriptions/app-store/verify': 'Verify App Store purchase',
  'POST /mobile/subscriptions/cancel': 'Cancel subscription',
  'GET /payments/myfatoorah/subscriptions/result': 'MyFatoorah payment return URL',
  'POST /payments/myfatoorah/subscriptions/webhook': 'MyFatoorah webhook handler',
  'POST /admin/subscriptions': 'Create manual subscription',
  'GET /admin/subscriptions': 'List subscriptions',
  'GET /admin/subscriptions/:id': 'Subscription details',
  'PATCH /admin/subscriptions/:id': 'Update subscription',
  'DELETE /admin/subscriptions/:id': 'Delete subscription',
  'GET /admin/system': 'System settings',
  'PUT /admin/system': 'Bulk update settings',
  'PUT /admin/system/:key': 'Update single setting',
  'GET /mobile/training-profile': 'User training profile',
  'PUT /mobile/training-profile': 'Update training profile',
  'POST /uploads': 'Upload file (image/audio)',
  'DELETE /uploads': 'Delete uploaded file',
  'GET /mobile/exercise-preferences': 'User exercise preferences',
  'PUT /mobile/exercise-preferences/:exerciseId': 'Set exercise preference',
  'DELETE /mobile/exercise-preferences/:exerciseId': 'Clear exercise preference',
  'GET /users': 'List app users',
  'POST /users': 'Create user manually',
  'PUT /users/:id': 'Update user (incl. Pro status)',
  'DELETE /users/:id': 'Delete user',
  'POST /mobile/planned-workouts/:id/start': 'Start planned workout',
  'POST /mobile/planned-workouts/:id/complete': 'Complete planned workout',
  'POST /mobile/planned-workouts/:id/report': 'Complete planned workout (legacy alias)',
  'POST /mobile/workout-executions': 'Upload single exercise execution',
  'GET /mobile/workout-executions': 'List execution history',
  'POST /mobile/workout-executions/explore': 'Upload explore/quick-start workout',
  'GET /mobile/workout-executions/stats': 'Aggregate execution stats',
  'GET /mobile/workout-executions/:exerciseId': 'Per-exercise history and aggregates',
  'GET /workout-phases': 'List workout phases',
  'POST /workout-phases': 'Create workout phase',
  'GET /workout-phases/:id': 'Workout phase details',
  'PUT /workout-phases/:id': 'Update workout phase',
  'DELETE /workout-phases/:id': 'Delete workout phase',
  'GET /mobile/workout-templates': 'Sync workout template catalog',
  'GET /mobile/workout-templates/:slug/audio-manifest': 'Template audio manifest',
  'GET /mobile/workout-templates/:id/training-config': 'Training engine config JSON',
  'GET /workout-templates': 'List workout templates',
  'POST /workout-templates': 'Create workout template',
  'GET /workout-templates/:id': 'Template details with exercises',
  'PUT /workout-templates/:id': 'Update template',
  'DELETE /workout-templates/:id': 'Delete template',
  'POST /workout-templates/:id/publish': 'Publish template',
  'DELETE /workout-templates/:id/publish': 'Unpublish template',
  'POST /workout-templates/:id/duplicate': 'Duplicate template',
  'GET /mobile/plan': 'Active plan summary',
  'GET /mobile/plan/today': "Today's plan slice",
  'GET /mobile/plan/enrollment-check': 'Check enrollment eligibility',
  'POST /mobile/plan/enroll': 'Enroll in active plan flow',
  'POST /mobile/plan/complete': 'Complete active plan milestone',
  'POST /ai/translate': 'Translate text (admin AI)',
  'GET /ai/tts/config': 'TTS provider configuration',
  'POST /ai/tts': 'Generate TTS audio',
  'DELETE /ai/tts': 'Clear TTS cache',
};

const METHOD_ORDER = { GET: 0, POST: 1, PUT: 2, PATCH: 3, DELETE: 4 };

function purpose(method, routePath) {
  const key = `${method} ${routePath}`;
  if (PURPOSE[key]) return PURPOSE[key];
  throw new Error(`Missing purpose for ${key}`);
}

function categorize(routePath, file) {
  if (routePath === '/' || routePath.startsWith('/admin/system')) return 'system';
  if (routePath.startsWith('/admin/auth') || routePath.startsWith('/admins') || routePath.startsWith('/admin/permissions'))
    return 'admin-auth';
  if (routePath.startsWith('/admin/analytics')) return 'admin-analytics';
  if (
    routePath.startsWith('/payments/') ||
    routePath.startsWith('/mobile/subscriptions') ||
    routePath.startsWith('/admin/subscriptions') ||
    routePath === '/mobile/plans' ||
    routePath.startsWith('/admin/plans')
  )
    return 'payments';
  if (routePath.startsWith('/mobile/auth')) return 'mobile-auth';
  if (
    file.includes('workout-executions') ||
    file.includes('mobile-workout-templates') ||
    file.includes('mobile-exercises.controller') ||
    (file.includes('progression.controller') && routePath.startsWith('/mobile/progression'))
  )
    return 'mobile-training';
  if (routePath.startsWith('/mobile/') || routePath.startsWith('/assessment')) return 'mobile-journey';
  return 'admin-content';
}

const sectionMeta = {
  'admin-auth': { title: 'Admin Auth', order: 1 },
  'admin-content': { title: 'Admin Content', order: 2 },
  'admin-analytics': { title: 'Admin Analytics', order: 3 },
  'mobile-auth': { title: 'Mobile Auth', order: 4 },
  'mobile-journey': { title: 'Mobile Journey', order: 5 },
  'mobile-training': { title: 'Mobile Training', order: 6 },
  payments: { title: 'Payments', order: 7 },
  system: { title: 'System', order: 8 },
};

const modulesDir = 'src/modules';
const appModule = fs.readFileSync('src/app.module.ts', 'utf8');
const importedModules = [
  ...new Set([...appModule.matchAll(/from '\.\/modules\/([^']+)/g)].map((m) => m[1].split('/')[0])),
];

function walk(dir, files = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, files);
    else if (e.name.endsWith('.controller.ts')) files.push(p);
  }
  return files;
}

const registered = walk(modulesDir).filter((f) => {
  const mod = f.replace(/\\/g, '/').match(/modules\/([^/]+)/)[1];
  return importedModules.includes(mod);
});
if (fs.existsSync('src/app.controller.ts')) registered.push('src/app.controller.ts');

const methodMap = { Get: 'GET', Post: 'POST', Put: 'PUT', Patch: 'PATCH', Delete: 'DELETE' };
const routes = [];

for (const file of registered.sort()) {
  const content = fs.readFileSync(file, 'utf8');
  const ctrlMatch = content.match(/@Controller\(([^)]*)\)/);
  let base = '';
  if (ctrlMatch) {
    const raw = ctrlMatch[1].trim();
    if (raw.startsWith('[')) {
      base = [...raw.matchAll(/['`]([^'`]+)['`]/g)].map((m) => m[1])[0];
    } else if (raw.startsWith('{')) {
      const pathM = raw.match(/path:\s*['`]([^'`]+)['`]/);
      base = pathM ? pathM[1] : '';
    } else {
      const strM = raw.match(/['`]([^'`]+)['`]/);
      base = strM ? strM[1] : '';
    }
  }
  const re = /@(Get|Post|Put|Patch|Delete)\(([^)]*)\)/g;
  let m;
  while ((m = re.exec(content)) !== null) {
    let routePath = '';
    const arg = m[2].trim();
    if (arg) {
      const strM = arg.match(/['`]([^'`]*?)['`]/);
      routePath = strM ? strM[1] : '';
    }
    const full = (`/${base}/${routePath}`).replace(/\/+/g, '/').replace(/\/$/, '') || '/';
    routes.push({
      method: methodMap[m[1]],
      path: full,
      file: file.replace(/\\/g, '/'),
      category: categorize(full, file.replace(/\\/g, '/')),
    });
  }
}

const seen = new Set();
const uniqueRoutes = routes.filter((r) => {
  const k = `${r.method} ${r.path}`;
  if (seen.has(k)) return false;
  seen.add(k);
  return true;
});

for (const r of uniqueRoutes) {
  r.purpose = purpose(r.method, r.path);
}

const byCategory = {};
for (const r of uniqueRoutes) {
  (byCategory[r.category] ??= []).push(r);
}
for (const cat of Object.keys(byCategory)) {
  byCategory[cat].sort(
    (a, b) =>
      a.path.localeCompare(b.path) || (METHOD_ORDER[a.method] ?? 9) - (METHOD_ORDER[b.method] ?? 9),
  );
}

function subsectionName(routePath, file) {
  if (routePath.startsWith('/admin/auth')) return 'Admin session';
  if (routePath.startsWith('/admins')) return 'Admins';
  if (routePath.startsWith('/admin/permissions')) return 'Permissions & roles';
  if (routePath.startsWith('/exercises')) return 'Exercises';
  if (routePath.startsWith('/workout-templates')) return 'Workout templates';
  if (routePath.startsWith('/workout-phases')) return 'Workout phases';
  if (routePath.startsWith('/programs') && !routePath.startsWith('/programs/map')) return 'Programs';
  if (routePath === '/programs/map') return 'Programs';
  if (routePath.startsWith('/messages')) return 'Messages & TTS';
  if (routePath.startsWith('/attributes')) return 'Attributes';
  if (routePath.startsWith('/pose-positions')) return 'Pose positions';
  if (routePath.startsWith('/users')) return 'App users';
  if (routePath.startsWith('/uploads')) return 'Uploads';
  if (routePath.startsWith('/ai')) return 'AI utilities';
  if (routePath.startsWith('/admin/plans')) return 'Subscription plans';
  if (routePath.startsWith('/admin/levels')) return 'Levels';
  if (routePath.startsWith('/admin/assessment-templates')) return 'Assessment templates';
  if (routePath.startsWith('/admin/exercise-families') || routePath.startsWith('/admin/exercise-progression'))
    return 'Exercise progression';
  if (routePath.startsWith('/admin/progression-rules')) return 'Progression rules';
  if (routePath.startsWith('/admin/subscriptions')) return 'Subscriptions (admin)';
  if (routePath.startsWith('/mobile/auth')) return 'Mobile session';
  if (routePath.startsWith('/mobile/home') || routePath.startsWith('/mobile/explore') || routePath.startsWith('/mobile/sync'))
    return 'Home & sync';
  if (
    routePath === '/mobile/plan' ||
    routePath.startsWith('/mobile/plan/') ||
    routePath.startsWith('/mobile/user-programs') ||
    routePath.startsWith('/mobile/programs')
  )
    return 'Programs & active plan';
  if (routePath.startsWith('/assessment') || routePath.startsWith('/mobile/assessment')) return 'Assessment';
  if (routePath.startsWith('/mobile/level-profile') || routePath.startsWith('/mobile/reassessment')) return 'Levels & reassessment';
  if (routePath.startsWith('/mobile/prescription')) return 'Prescription';
  if (routePath.startsWith('/mobile/training-profile') || routePath.startsWith('/mobile/exercise-preferences'))
    return 'Profile & preferences';
  if (routePath.startsWith('/mobile/reports')) return 'Reports';
  if (routePath.startsWith('/mobile/workout-executions') || routePath.startsWith('/mobile/planned-workouts'))
    return 'Workout executions & planned workouts';
  if (routePath.startsWith('/mobile/workout-templates') || routePath.startsWith('/mobile/exercises'))
    return 'Templates & exercise assets';
  if (routePath.startsWith('/mobile/progression')) return 'Progression';
  if (routePath.startsWith('/mobile/subscriptions') || routePath.startsWith('/payments/') || routePath === '/mobile/plans')
    return 'Subscriptions & billing';
  if (routePath.startsWith('/admin/system') || routePath === '/') return 'Health & settings';
  if (routePath.startsWith('/admin/analytics')) return 'Dashboards & reports';
  return 'Other';
}

const subsectionOrder = [
  'Admin session',
  'Admins',
  'Permissions & roles',
  'Exercises',
  'Workout templates',
  'Workout phases',
  'Programs',
  'Messages & TTS',
  'Attributes',
  'Pose positions',
  'App users',
  'Uploads',
  'AI utilities',
  'Assessment templates',
  'Levels',
  'Exercise progression',
  'Progression rules',
  'Subscription plans',
  'Subscriptions (admin)',
  'Dashboards & reports',
  'Mobile session',
  'Home & sync',
  'Programs & active plan',
  'Assessment',
  'Levels & reassessment',
  'Prescription',
  'Profile & preferences',
  'Reports',
  'Workout executions & planned workouts',
  'Templates & exercise assets',
  'Progression',
  'Subscriptions & billing',
  'Health & settings',
];

let md = `# Movit REST API

| | |
|---|---|
| **Status** | \`ACTIVE\` |
| **SSOT for** | All NestJS REST routes registered in \`app.module.ts\` |
| **Code** | \`backend/src/modules/**/*.controller.ts\`, \`backend/src/app.module.ts\` |
| **Supersedes** | \`backend/API_ENDPOINTS.md\` (redirect stub) |
| **Verified** | 2026-06-22 |

**Base URL:** \`/api\` (global prefix in \`main.ts\`)

**Interactive docs:** Swagger UI at \`/api/docs\` — OpenAPI generated from controllers at runtime.

**Route count:** ${uniqueRoutes.length} (verified from registered controllers)

**Auth:** Admin routes use session cookies or bearer JWT + CASL permissions. Mobile routes use bearer JWT (\`Authorization: Bearer <accessToken>\`).

**Naming:** Training domain terms follow [Workout-Domain-Naming.md](Workout-Domain-Naming.md) (\`PlannedWorkout\`, \`WorkoutTemplate\`, \`WorkoutExecution\`).

---

## Contents

`;

const orderedCats = Object.entries(sectionMeta).sort((a, b) => a[1].order - b[1].order);
orderedCats.forEach(([cat, meta], i) => {
  const count = byCategory[cat]?.length ?? 0;
  md += `${i + 1}. [${meta.title}](#${meta.title.toLowerCase().replace(/ & /g, '-').replace(/ /g, '-')}) (${count})\n`;
});
md += '\n---\n\n';

for (const [cat, meta] of orderedCats) {
  const items = byCategory[cat] ?? [];
  if (!items.length) continue;
  md += `## ${meta.title}\n\n`;

  const groups = new Map();
  for (const r of items) {
    const sub = subsectionName(r.path, r.file);
    if (!groups.has(sub)) groups.set(sub, []);
    groups.get(sub).push(r);
  }

  const sortedSubs = [...groups.keys()].sort(
    (a, b) => (subsectionOrder.indexOf(a) === -1 ? 999 : subsectionOrder.indexOf(a)) -
      (subsectionOrder.indexOf(b) === -1 ? 999 : subsectionOrder.indexOf(b)),
  );

  for (const sub of sortedSubs) {
    const rs = groups.get(sub);
    md += `### ${sub}\n\n`;
    md += '| Method | Path | Purpose |\n';
    md += '|--------|------|--------|\n';
    for (const r of rs) {
      md += `| ${r.method} | \`${r.path}\` | ${r.purpose} |\n`;
    }
    md += '\n';
  }
}

md += `---

## Maintenance

Verify route inventory:

\`\`\`bash
cd backend && node scripts/extract-routes.mjs
node scripts/generate-api-doc.mjs
\`\`\`

When adding or removing controllers, update \`app.module.ts\` and re-run the generator. Cancelled features (booking, doctor schedules, Google Meet) are **not** registered — no routes documented.

`;

fs.writeFileSync(OUT, md);
console.log(`Wrote ${uniqueRoutes.length} routes to ${OUT}`);
