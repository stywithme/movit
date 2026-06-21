Optional posture classifier (3 classes: standing / sitting / lying)

After training, copy these files from tools/posture-mlp output into this folder:
  - posture_mlp.tflite
  - posture_mlp_norm.json

See tools/posture-mlp/README.md for training instructions.

If these files are absent, the app uses BodyPostureDetector (rule-based) only.
