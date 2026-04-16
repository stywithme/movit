/**
 * Generated catalog for mobile system messages (keys must match MobileMessageResolver + SystemMessageRegistry in android-poc).
 */


export type CatalogSysMsg = {
  code: string;
  description: string;
  content: { ar: string; en: string };
  context?: string | null;
};

const DIR_ORDER = ['BACK', 'DIAGONAL', 'FRONT', 'SIDE_ANY', 'SIDE_LEFT', 'SIDE_RIGHT'] as const;

const DIR_AR: Record<(typeof DIR_ORDER)[number], string> = {
  BACK: 'الخلف',
  DIAGONAL: 'بزاوية مائلة',
  FRONT: 'الأمام',
  SIDE_ANY: 'الجانب',
  SIDE_LEFT: 'الجانب الأيسر',
  SIDE_RIGHT: 'الجانب الأيمن',
};

const DIR_EN: Record<(typeof DIR_ORDER)[number], string> = {
  BACK: 'the back',
  DIAGONAL: 'an angle',
  FRONT: 'the front',
  SIDE_ANY: 'the side',
  SIDE_LEFT: 'the left side',
  SIDE_RIGHT: 'the right side',
};

const POST_ORDER = ['LYING_PRONE', 'LYING_SIDE', 'LYING_SUPINE', 'SITTING', 'STANDING'] as const;

const POST_AR: Record<(typeof POST_ORDER)[number], string> = {
  STANDING: 'قف مستقيماً',
  LYING_PRONE: 'استلقِ على وجهك',
  LYING_SUPINE: 'استلقِ على ظهرك',
  LYING_SIDE: 'استلقِ على جنبك',
  SITTING: 'اجلس',
};

const POST_EN: Record<(typeof POST_ORDER)[number], string> = {
  STANDING: 'Stand upright',
  LYING_PRONE: 'Lie face down',
  LYING_SUPINE: 'Lie face up',
  LYING_SIDE: 'Lie on your side',
  SITTING: 'Sit down',
};

const REG_ORDER = ['FULL_BODY', 'LOWER_BODY', 'UPPER_BODY'] as const;

const REG_AR: Record<(typeof REG_ORDER)[number], string> = {
  FULL_BODY: 'الجسم بالكامل',
  UPPER_BODY: 'الجزء العلوي',
  LOWER_BODY: 'الجزء السفلي',
};

const REG_EN: Record<(typeof REG_ORDER)[number], string> = {
  FULL_BODY: 'your full body',
  UPPER_BODY: 'your upper body',
  LOWER_BODY: 'your lower body',
};

function nonEmptySubsets<T extends readonly string[]>(items: T): string[][] {
  const out: string[][] = [];
  const n = items.length;
  for (let mask = 1; mask < 1 << n; mask++) {
    const set: string[] = [];
    for (let i = 0; i < n; i++) {
      if (mask & (1 << i)) set.push(items[i]);
    }
    out.push(set.sort());
  }
  return out;
}

function jointGuidanceRow(
  code: string,
  isRaise: boolean,
  ar: string,
  en: string
): CatalogSysMsg {
  const dir = isRaise ? 'raise' : 'lower';
  return {
    code: `setup_guidance_${code}_${dir}`,
    description: `Setup ANGLES phase: ${code} ${dir}`,
    content: { ar, en },
  };
}

/** Mirrors MobileMessageResolver.defaultJointGuidanceText (side = Arabic الأيسر/الأيمن or English left/right). */
function jointPairs(): CatalogSysMsg[] {
  const rows: CatalogSysMsg[] = [];
  const lateral = (
    base: string,
    arRaiseL: string,
    enRaiseL: string,
    arLowerL: string,
    enLowerL: string
  ) => {
    const sides: [string, string, string][] = [
      ['left', 'الأيسر', 'left'],
      ['right', 'الأيمن', 'right'],
    ];
    for (const [lr, arSide, enSide] of sides) {
      const jc = `${lr}_${base}`;
      rows.push(
        jointGuidanceRow(
          jc,
          true,
          arRaiseL.replace(/\$side/g, arSide),
          enRaiseL.replace(/\$side/g, enSide)
        ),
        jointGuidanceRow(
          jc,
          false,
          arLowerL.replace(/\$side/g, arSide),
          enLowerL.replace(/\$side/g, enSide)
        )
      );
    }
  };

  lateral(
    'elbow',
    'ارفع الكوع $side أكثر',
    'Raise your $side elbow more',
    'اخفض الكوع $side أكثر',
    'Lower your $side elbow more'
  );
  lateral(
    'shoulder',
    'ارفع الكتف $side',
    'Raise your $side shoulder',
    'اخفض الكتف $side',
    'Lower your $side shoulder'
  );
  lateral(
    'knee',
    'افرد الركبة $side أكثر',
    'Straighten your $side knee more',
    'اثني الركبة $side أكثر',
    'Bend your $side knee more'
  );
  lateral(
    'hip',
    'افرد الورك $side',
    'Extend your $side hip',
    'اثني الورك $side أكثر',
    'Bend your $side hip more'
  );
  lateral(
    'ankle',
    'ارفع الكاحل $side',
    'Raise your $side ankle',
    'اخفض الكاحل $side',
    'Lower your $side ankle'
  );
  lateral(
    'wrist',
    'ارفع المعصم $side',
    'Raise your $side wrist',
    'اخفض المعصم $side',
    'Lower your $side wrist'
  );

  rows.push(
    jointGuidanceRow('spine', true, 'افرد ظهرك أكثر', 'Straighten your back more'),
    jointGuidanceRow('spine', false, 'انحني للأمام أكثر', 'Bend forward more'),
    jointGuidanceRow('neck', true, 'ارفع رأسك', 'Lift your head'),
    jointGuidanceRow('neck', false, 'اخفض رأسك', 'Lower your head'),
    jointGuidanceRow('other', true, 'ارفع أكثر', 'Raise more'),
    jointGuidanceRow('other', false, 'اخفض أكثر', 'Lower more')
  );

  const bare: [string, string, string, string, string][] = [
    ['elbow', 'ارفع الكوع أكثر', 'Raise your elbow more', 'اخفض الكوع أكثر', 'Lower your elbow more'],
    ['shoulder', 'ارفع الكتف', 'Raise your shoulder', 'اخفض الكتف', 'Lower your shoulder'],
    ['knee', 'افرد الركبة أكثر', 'Straighten your knee more', 'اثني الركبة أكثر', 'Bend your knee more'],
    ['hip', 'افرد الورك', 'Extend your hip', 'اثني الورك أكثر', 'Bend your hip more'],
    ['ankle', 'ارفع الكاحل', 'Raise your ankle', 'اخفض الكاحل', 'Lower your ankle'],
    ['wrist', 'ارفع المعصم', 'Raise your wrist', 'اخفض المعصم', 'Lower your wrist'],
  ];
  for (const [b, arR, enR, arL, enL] of bare) {
    rows.push(jointGuidanceRow(b, true, arR, enR), jointGuidanceRow(b, false, arL, enL));
  }

  return rows;
}

export function buildCatalogSystemMessages(): CatalogSysMsg[] {
  const out: CatalogSysMsg[] = [];

  out.push({
    code: 'training_setup_scene_to_visibility',
    description: 'Scene check passed → visibility check (setup transition voice/UI)',
    content: {
      ar: 'الوضع صحيح – جاري التحقق من الرؤية',
      en: 'Position correct – checking visibility',
    },
  });

  out.push({
    code: 'setup_guidance_ok',
    description: 'Setup joint angle OK (green)',
    content: { ar: '✓ ممتاز', en: '✓ Good' },
  });

  for (let n = 4; n <= 30; n++) {
    out.push({
      code: `training_countdown_${n}`,
      description: `Spoken numeral ${n} (countdown / rep announcement)`,
      content: { ar: String(n), en: String(n) },
    });
  }

  const dirSubsets = nonEmptySubsets(DIR_ORDER);
  for (const subset of dirSubsets) {
    const arParts = subset.map((x) => DIR_AR[x as (typeof DIR_ORDER)[number]]);
    const enParts = subset.map((x) => DIR_EN[x as (typeof DIR_ORDER)[number]]);
    const suffix = subset.join('_').toLowerCase();
    out.push({
      code: `setup_direction_${suffix}`,
      description: `Setup phase: film direction (${subset.join('+')})`,
      content: {
        ar: `صوّر من ${arParts.join(' أو ')} ↻`,
        en: `Film from ${enParts.join(' or ')} ↻`,
      },
    });
    out.push({
      code: `setup_axis_direction_${suffix}`,
      description: `PositionValidator axis warning: direction (${subset.join('+')})`,
      content: {
        ar: `صوّر من ${arParts.join(' أو ')}`,
        en: `Film from ${enParts.join(' or ')}`,
      },
    });
  }
  out.push({
    code: 'setup_direction_any',
    description: 'Setup phase direction fallback (no mappable direction)',
    content: { ar: 'صوّر ↻', en: 'Film ↻' },
  });
  out.push({
    code: 'setup_axis_direction_any',
    description: 'Validator direction fallback',
    content: { ar: 'صوّر', en: 'Film' },
  });

  const postSubsets = nonEmptySubsets(POST_ORDER);
  for (const subset of postSubsets) {
    const arParts = subset.map((x) => POST_AR[x as (typeof POST_ORDER)[number]]);
    const enParts = subset.map((x) => POST_EN[x as (typeof POST_ORDER)[number]]);
    const suffix = subset.join('_').toLowerCase();
    const row: CatalogSysMsg = {
      code: `setup_posture_${suffix}`,
      description: `Setup posture (${subset.join('+')})`,
      content: {
        ar: arParts.join(' أو '),
        en: enParts.join(' or '),
      },
    };
    out.push(row);
    out.push({
      code: `setup_axis_posture_${suffix}`,
      description: `Axis warning posture (${subset.join('+')})`,
      content: { ...row.content },
    });
  }
  out.push(
    {
      code: 'setup_posture_any',
      description: 'Setup posture fallback',
      content: { ar: '', en: '' },
    },
    {
      code: 'setup_axis_posture_any',
      description: 'Axis posture fallback',
      content: { ar: '', en: '' },
    }
  );

  const regSubsets = nonEmptySubsets(REG_ORDER);
  for (const subset of regSubsets) {
    const arParts = subset.map((x) => REG_AR[x as (typeof REG_ORDER)[number]]);
    const enParts = subset.map((x) => REG_EN[x as (typeof REG_ORDER)[number]]);
    const suffix = subset.join('_').toLowerCase();
    out.push(
      {
        code: `setup_region_${suffix}`,
        description: `Setup region (${subset.join('+')})`,
        content: {
          ar: `أظهر ${arParts.join(' أو ')}`,
          en: `Show ${enParts.join(' or ')}`,
        },
      },
      {
        code: `setup_axis_region_${suffix}`,
        description: `Axis region (${subset.join('+')})`,
        content: {
          ar: `أظهر ${arParts.join(' أو ')}`,
          en: `Show ${enParts.join(' or ')}`,
        },
      }
    );
  }
  out.push(
    {
      code: 'setup_region_any',
      description: 'Setup region fallback',
      content: { ar: 'أظهر', en: 'Show' },
    },
    {
      code: 'setup_axis_region_any',
      description: 'Axis region fallback',
      content: { ar: 'أظهر', en: 'Show' },
    }
  );

  out.push(...jointPairs());

  return out;
}

