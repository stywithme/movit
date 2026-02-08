import type { PrismaClient } from '@prisma/client';

export async function seedCameraPositions(prisma: PrismaClient) {
  const leftKnee = await prisma.attributeValue.findUnique({ where: { code: 'left_knee' } });
  const rightKnee = await prisma.attributeValue.findUnique({ where: { code: 'right_knee' } });
  const leftHip = await prisma.attributeValue.findUnique({ where: { code: 'left_hip' } });
  const rightHip = await prisma.attributeValue.findUnique({ where: { code: 'right_hip' } });
  const leftShoulder = await prisma.attributeValue.findUnique({ where: { code: 'left_shoulder' } });
  const rightShoulder = await prisma.attributeValue.findUnique({ where: { code: 'right_shoulder' } });
  const leftElbow = await prisma.attributeValue.findUnique({ where: { code: 'left_elbow' } });
  const rightElbow = await prisma.attributeValue.findUnique({ where: { code: 'right_elbow' } });
  const leftAnkle = await prisma.attributeValue.findUnique({ where: { code: 'left_ankle' } });
  const rightAnkle = await prisma.attributeValue.findUnique({ where: { code: 'right_ankle' } });
  const spine = await prisma.attributeValue.findUnique({ where: { code: 'spine' } });

  const sideViewLeft = await prisma.cameraPosition.upsert({
    where: { code: 'side_left' },
    update: { schemaCode: 'side_view' },
    create: {
      code: 'side_left',
      schemaCode: 'side_view',
      name: { ar: 'جانبي أيسر', en: 'Left Side View' },
      description: { ar: 'عرض جانبي للجسم من الناحية اليسرى', en: 'Side view from the left' },
      sortOrder: 1,
    },
  });

  const sideLeftJoints = [leftKnee, leftHip, leftShoulder, leftElbow, leftAnkle, spine];
  for (const joint of sideLeftJoints) {
    if (!joint) continue;
    await prisma.cameraPositionJoint.upsert({
      where: { cameraPositionId_jointId: { cameraPositionId: sideViewLeft.id, jointId: joint.id } },
      update: {},
      create: { cameraPositionId: sideViewLeft.id, jointId: joint.id },
    });
  }

  const sideViewRight = await prisma.cameraPosition.upsert({
    where: { code: 'side_right' },
    update: { schemaCode: 'side_view' },
    create: {
      code: 'side_right',
      schemaCode: 'side_view',
      name: { ar: 'جانبي أيمن', en: 'Right Side View' },
      description: { ar: 'عرض جانبي للجسم من الناحية اليمنى', en: 'Side view from the right' },
      sortOrder: 2,
    },
  });

  const sideRightJoints = [rightKnee, rightHip, rightShoulder, rightElbow, rightAnkle, spine];
  for (const joint of sideRightJoints) {
    if (!joint) continue;
    await prisma.cameraPositionJoint.upsert({
      where: { cameraPositionId_jointId: { cameraPositionId: sideViewRight.id, jointId: joint.id } },
      update: {},
      create: { cameraPositionId: sideViewRight.id, jointId: joint.id },
    });
  }

  const frontView = await prisma.cameraPosition.upsert({
    where: { code: 'front' },
    update: { schemaCode: 'front_view' },
    create: {
      code: 'front',
      schemaCode: 'front_view',
      name: { ar: 'أمامي', en: 'Front View' },
      description: { ar: 'عرض أمامي للجسم', en: 'Front view of the body' },
      sortOrder: 3,
    },
  });

  const frontJoints = [leftKnee, rightKnee, leftHip, rightHip, leftShoulder, rightShoulder, leftElbow, rightElbow];
  for (const joint of frontJoints) {
    if (!joint) continue;
    await prisma.cameraPositionJoint.upsert({
      where: { cameraPositionId_jointId: { cameraPositionId: frontView.id, jointId: joint.id } },
      update: {},
      create: { cameraPositionId: frontView.id, jointId: joint.id },
    });
  }

  const backView = await prisma.cameraPosition.upsert({
    where: { code: 'back' },
    update: { schemaCode: 'back_view' },
    create: {
      code: 'back',
      schemaCode: 'back_view',
      name: { ar: 'خلفي', en: 'Back View' },
      description: { ar: 'عرض خلفي للجسم', en: 'Back view of the body' },
      sortOrder: 4,
    },
  });

  const backJoints = [leftKnee, rightKnee, leftHip, rightHip, leftShoulder, rightShoulder, spine];
  for (const joint of backJoints) {
    if (!joint) continue;
    await prisma.cameraPositionJoint.upsert({
      where: { cameraPositionId_jointId: { cameraPositionId: backView.id, jointId: joint.id } },
      update: {},
      create: { cameraPositionId: backView.id, jointId: joint.id },
    });
  }

  console.log('✅ Camera positions created');
}
