/**
 * Enrich description/instructions and differentiate right_hip (trailing / camera-side)
 * messages for side-view squat family JSON in exercises-from-db.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DIR = path.resolve(__dirname, '../Exercise-json/exercises-from-db');

const SLUGS = new Set([
  'squat',
  'wall_sit',
  'lib_tempo_squat',
  'lib_shadow_squat',
  'lib_box_squat',
  'lib_front_squat',
  'lib_goblet_squat',
  'lib_heel_elevated_squat',
  'lib_overhead_squat_pvc',
  'assessment_overhead_squat',
]);

const COPY = {
  squat: {
    description: {
      ar: 'قرفصاء وزن الجسم لتقوية الأرداف والفخذين مع الحفاظ على الحوض والركبتين محاذيين.',
      en: 'A bodyweight squat to strengthen glutes and thighs while keeping hips and knees stacked and controlled.',
    },
    instructions: {
      ar: 'اقفِ بعرض الحوض، أشدّ بطنك قليلاً، انزل كأنك تجلس على كرسي مع دفع الركبتين للخارج قليلاً، ثم ادفع من منتصف القدم للوقوف دون الميل للأمام. تنفّس بإيقاع ثابت.',
      en: 'Stand feet hip-width, lightly brace your core, sit down between your hips with knees tracking over toes, then drive up through mid-foot without folding forward. Breathe steadily.',
    },
  },
  wall_sit: {
    description: {
      ar: 'احتكاك جدارية لتثبيت الفخذين والأرداف تحت حمل ثابت زمنياً.',
      en: 'An isometric wall sit to build thigh and glute endurance with fixed-time support.',
    },
    instructions: {
      ar: 'ظهرك ملاصق للجدار، انزل حتى زاوية تقريبية 90° بين الفخذ والساق، ثبّت الركبتين فوق الكعبين، وافرد ظهرك على الحائط دون انزلاق الحوض للأمام.',
      en: 'Back on the wall, slide down until thighs are near parallel, knees over ankles, and keep your trunk tall against the wall without letting hips slide forward.',
    },
  },
  lib_tempo_squat: {
    description: {
      ar: 'سكوات بإيقاع محدد (نزول/أسفل/صعود) لتحسين التحكم والعمق.',
      en: 'A tempo squat with controlled descent, bottom pause, and drive to improve depth and control.',
    },
    instructions: {
      ar: 'اتبع الإيقاع: نزول بطيء، توقف قصير في الأسفل، ثم صعود قوي دون فقدان ضبط الجذع أو الركبتين.',
      en: 'Follow the tempo: slow down, short pause at the bottom, then a strong stand while keeping knees aligned and ribs stacked over the pelvis.',
    },
  },
  lib_shadow_squat: {
    description: {
      ar: 'سكوات أمام جدار أو مرآة للتحقق من المحاذاة والعمق.',
      en: 'A squat facing a wall or mirror to self-check alignment, depth, and trunk position.',
    },
    instructions: {
      ar: 'اقترب بمسافة آمنة من الجدار، انزل مع إبقاء اليدين للأمام أو للأعلى حسب التمرين، وتجنب لمس الجدار إن كان الهدف مسافة ثابتة.',
      en: 'Set a comfortable distance from the wall, squat while keeping arms forward or overhead as prescribed, and avoid collapsing into the wall if clearance is the goal.',
    },
  },
  lib_box_squat: {
    description: {
      ar: 'سكوات مع لمس صندوق أو مقعد خلفك لتعليم عمق ثابت وآمن.',
      en: 'A squat to a box or bench touch to groove a consistent, safe squat depth.',
    },
    instructions: {
      ar: 'اقف أمام الصندوق، ادفع الوركين للخلف حتى يلمس المقعد بلطف، ثم ادفع للأعلى دون «جلوس» كامل أو استرخاء على الصندوق.',
      en: 'Hinge slightly back until you lightly tap the box, then stand without relaxing onto it or rocking forward at the knees.',
    },
  },
  lib_front_squat: {
    description: {
      ar: 'سكوات بقبضة أمامية (بار أمام الرقبة) يتطلب جذعاً منتصباً وكاحلين مرنين؛ للمتقدمين مع بار.',
      en: 'A front-rack squat demanding an upright torso and solid ankle mobility — typically loaded with a barbell for advanced users.',
    },
    instructions: {
      ar: 'ضع البار على منطقة الكتف الأمامية، أبقِ المرفقين مرتفعين، انزل بين الوركين مع صدر مرتفع، وتجنب انهيار المرفقين للأسفل.',
      en: 'Rest the bar on the shoulders with elbows high, sit between the hips with chest tall, and avoid letting the elbows drop or the torso collapse.',
    },
  },
  lib_goblet_squat: {
    description: {
      ar: 'سكوات جوبليت يعلّم العمق والاستقامة باستخدام وزن أمامي قريب من الصدر.',
      en: 'A goblet squat that teaches depth and upright posture using a front-loaded dumbbell or kettlebell.',
    },
    instructions: {
      ar: 'امسك الوزن أمام الصدر، انزل بين الوركين مع إبقاء المرفقين داخل الركبتين، ثم ادفع للأعلى مع الحفاظ على الوزن قريباً من الجسم.',
      en: 'Hold the weight at chest height, squat between the hips with elbows inside the knees, then drive up while keeping the load close to your body.',
    },
  },
  lib_heel_elevated_squat: {
    description: {
      ar: 'سكوات مع رفع الكعبين لتحسين عمق الورك عند ضيق الكاحل.',
      en: 'A heel-elevated squat to reach comfortable depth when ankle mobility is limited.',
    },
    instructions: {
      ar: 'ضع كعبيك مرتفعين بثبات، انزل مع الحفاظ على الوزن فوق منتصف القدم، وتجنب الدوران للأمام على أصابع القدم.',
      en: 'Set a stable heel lift, keep weight over mid-foot as you squat, and avoid shifting all load onto the toes.',
    },
  },
  lib_overhead_squat_pvc: {
    description: {
      ar: 'سكوات علوي بعصا/PVC لفحص التحكم في الجذع والكتفين والعمق.',
      en: 'An overhead squat with a PVC pipe or stick to screen shoulder, trunk, and squat mechanics.',
    },
    instructions: {
      ar: 'ارفع العصا فوق الرأس بعرض أوسع من الكتفين قليلاً، انزل بين الوركين مع إبقاء العصا فوق منتصف القدم تقريباً من الجانب.',
      en: 'Press the bar overhead slightly wider than shoulders, squat between the hips while keeping the bar stacked over mid-foot from the side view.',
    },
  },
  assessment_overhead_squat: {
    description: {
      ar: 'تقييم حركة سكوات علوي لرصد تقييدات الكتف والعمود الفقري والكاحل.',
      en: 'An overhead squat screen to observe shoulder, trunk, and ankle limitations in one pattern.',
    },
    instructions: {
      ar: 'نفّذ الحركة ببطء ونطاق مريح، راقب من الجانب محاذاة الركبة مع القدم وثبات الذراعين فوق الرأس.',
      en: 'Move slowly through a comfortable range; from the side, check knee-to-foot alignment and stable arms overhead.',
    },
  },
};

const TRAILING_HIP_MESSAGES = {
  stateMessages: {
    danger: {
      ar: 'توقف — انحناء زائد في أسفل الظهر قد يزيد الضغط.',
      en: 'Stop — excessive low-back rounding increases strain.',
    },
    perfect: {
      ar: 'جيد — الحوض والصدر في خط منظم فوق الركبة.',
      en: 'Good — ribs and pelvis stay organized over the knee line.',
    },
    warning: {
      ar: 'قلل الميل الأمامي للحوض؛ فكّر «تفتح بين الوركين».',
      en: 'Reduce forward collapse at the hips; think “spread the hips”.',
    },
  },
  phaseStateMessages: {
    top: {
      perfect: {
        ar: 'وقفة علوية — الحوض تحت الكتف دون ميل قطني زائد.',
        en: 'Top position — hips under shoulders without big lumbar lean.',
      },
      warning: {
        ar: 'تجنب دفع الحوض بعيداً جداً للأمام في الأعلى.',
        en: 'Avoid pushing the hips too far forward at the finish.',
      },
    },
    bottom: {
      perfect: {
        ar: 'قاع متزن — وزن فوق منتصف القدم.',
        en: 'Balanced bottom — weight stays through mid-foot.',
      },
      warning: {
        ar: 'لا تدع الحوض ينهار للداخل أو للأمام بشكل مفاجئ.',
        en: 'Don’t let the hips collapse inward or dive forward.',
      },
    },
  },
};

function patchRightHip(trackedJoints) {
  if (!Array.isArray(trackedJoints)) return false;
  let changed = false;
  for (const j of trackedJoints) {
    if (j?.joint !== 'right_hip' || j.role !== 'secondary') continue;
    j.pairedWith = 'left_hip';
    Object.assign(j, TRAILING_HIP_MESSAGES);
    changed = true;
  }
  return changed;
}

function main() {
  let n = 0;
  for (const f of fs.readdirSync(DIR)) {
    if (!f.endsWith('.json')) continue;
    const fp = path.join(DIR, f);
    const data = JSON.parse(fs.readFileSync(fp, 'utf8'));
    const slug = data.slug;
    if (!SLUGS.has(slug)) continue;

    const copy = COPY[slug];
    if (copy) {
      data.description = copy.description;
      data.instructions = copy.instructions;
    }

    const pvs = data.poseVariants || [];
    for (let i = 0; i < pvs.length; i++) {
      patchRightHip(pvs[i].trackedJoints);
      const dbPv = data._database?.poseVariants?.[i];
      if (dbPv?.trackedJointsConfig) {
        patchRightHip(dbPv.trackedJointsConfig);
      }
    }

    fs.writeFileSync(fp, JSON.stringify(data, null, 2) + '\n', 'utf8');
    n++;
    console.log('Enriched', slug);
  }
  console.log('Files:', n);
}

main();
