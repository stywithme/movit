"""
Posture MLP feature vector — MUST stay in sync with:
  android-poc/.../training/engine/PostureMlpFeatureExtractor.kt

16 features (float32), order fixed.
"""
from __future__ import annotations

import math
from typing import List, Optional, Sequence, Tuple

import numpy as np

# BlazePose indices (same as BodyLandmarks.kt)
NOSE = 0
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_KNEE = 25
RIGHT_KNEE = 26
LEFT_ANKLE = 27
RIGHT_ANKLE = 28

FEATURE_COUNT = 16
EPS = 1e-6


def _angle_3pt(
    ax: float, ay: float, bx: float, by: float, cx: float, cy: float
) -> float:
    """Angle ABC in degrees, 0..180."""
    v1x, v1y = ax - bx, ay - by
    v2x, v2y = cx - bx, cy - by
    n1 = math.hypot(v1x, v1y)
    n2 = math.hypot(v2x, v2y)
    if n1 < EPS or n2 < EPS:
        return 90.0
    c = (v1x * v2x + v1y * v2y) / (n1 * n2)
    c = max(-1.0, min(1.0, c))
    return math.degrees(math.acos(c))


def compute_features_from_landmarks(
    landmarks: Sequence[object],
) -> Optional[np.ndarray]:
    """
    landmarks: sequence of objects with .x, .y, .z, .visibility as floats
    (MediaPipe NormalizedLandmark).
    Returns shape (FEATURE_COUNT,) or None if insufficient data.
    """
    if len(landmarks) < 33:
        return None

    def lm(i: int) -> Tuple[float, float, float, float]:
        p = landmarks[i]
        return (float(p.x), float(p.y), float(p.z), float(p.visibility))

    ls = lm(LEFT_SHOULDER)
    rs = lm(RIGHT_SHOULDER)
    lh = lm(LEFT_HIP)
    rh = lm(RIGHT_HIP)
    lk = lm(LEFT_KNEE)
    rk = lm(RIGHT_KNEE)
    la = lm(LEFT_ANKLE)
    ra = lm(RIGHT_ANKLE)
    nose = lm(NOSE)

    # Torso: shoulder mid -> hip mid
    scx = (ls[0] + rs[0]) * 0.5
    scy = (ls[1] + rs[1]) * 0.5
    hcx = (lh[0] + rh[0]) * 0.5
    hcy = (lh[1] + rh[1]) * 0.5
    dx = hcx - scx
    dy = hcy - scy
    torso_len = math.hypot(dx, dy)
    if torso_len < 0.02:
        return None

    abs_angle = abs(math.degrees(math.atan2(dy, dx)))
    # 0..180
    spine_angle_deg = abs_angle if abs_angle <= 180.0 else 360.0 - abs_angle

    # Thigh mid vector (hip -> knee)
    kcx = (lk[0] + rk[0]) * 0.5
    kcy = (lk[1] + rk[1]) * 0.5
    thigh_dx = kcx - hcx
    thigh_dy = kcy - hcy
    thigh_len = math.hypot(thigh_dx, thigh_dy)
    if thigh_len < EPS:
        cos_torso_thigh = 0.0
    else:
        cos_torso_thigh = (dx * thigh_dx + dy * thigh_dy) / (torso_len * thigh_len)
        cos_torso_thigh = max(-1.0, min(1.0, cos_torso_thigh))

    ang_l = _angle_3pt(lh[0], lh[1], lk[0], lk[1], la[0], la[1]) / 180.0
    ang_r = _angle_3pt(rh[0], rh[1], rk[0], rk[1], ra[0], ra[1]) / 180.0

    shoulder_w = abs(ls[0] - rs[0])
    hip_w = abs(lh[0] - rh[0])
    shoulder_w_n = shoulder_w / torso_len
    hip_w_n = hip_w / torso_len

    knee_drop = (kcy - hcy) / torso_len
    ankle_drop = ((la[1] + ra[1]) * 0.5 - kcy) / torso_len

    mid_torso_y = (scy + hcy) * 0.5
    if nose[3] > 0.3:
        nose_off = (nose[1] - mid_torso_y) / torso_len
    else:
        nose_off = 0.0

    sh_v_sep = abs(ls[1] - rs[1]) / torso_len
    hip_v_sep = abs(lh[1] - rh[1]) / torso_len

    vis_knee = min(lk[3], rk[3])
    vis_hip = min(lh[3], rh[3])
    vis_sh = min(ls[3], rs[3])

    z_torso = abs((ls[2] + rs[2]) * 0.5 - (lh[2] + rh[2]) * 0.5)

    feats = np.array(
        [
            spine_angle_deg / 180.0,
            torso_len,
            cos_torso_thigh,
            ang_l,
            ang_r,
            shoulder_w_n,
            hip_w_n,
            knee_drop,
            ankle_drop,
            nose_off,
            sh_v_sep,
            hip_v_sep,
            vis_knee,
            vis_hip,
            vis_sh,
            z_torso,
        ],
        dtype=np.float32,
    )
    assert feats.shape == (FEATURE_COUNT,)
    return feats


FEATURE_NAMES: List[str] = [
    "spine_angle_norm",
    "torso_len",
    "cos_torso_thigh",
    "hip_knee_ankle_angle_l",
    "hip_knee_ankle_angle_r",
    "shoulder_width_over_torso",
    "hip_width_over_torso",
    "knee_drop_over_torso",
    "ankle_drop_over_torso",
    "nose_offset_over_torso",
    "shoulder_y_sep_over_torso",
    "hip_y_sep_over_torso",
    "vis_knee_min",
    "vis_hip_min",
    "vis_shoulder_min",
    "spine_z_depth",
]
