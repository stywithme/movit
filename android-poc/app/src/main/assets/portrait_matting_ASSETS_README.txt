Portrait matting ONNX models (report hero background effect only).
NOTE (F8): these assets ship in debug APK only; release excludes them until D9 dynamic delivery.


  modnet_photographic.onnx   (~25 MB) — default, best portrait edges
  u2net_human_seg.onnx       (~4.4 MB) — lighter human segmentation fallback

Download (if missing):
  curl -L -o modnet_photographic.onnx https://github.com/yakhyo/modnet/releases/download/weights/modnet_photographic.onnx
  curl -L -o u2net_human_seg.onnx https://huggingface.co/BritishWerewolf/U-2-Net-Human-Seg/resolve/main/onnx/model.onnx

Configure in app_settings.json → backgroundEffect.mattingEngine:
  modnet | u2net | mediapipe

RVM (Robust Video Matting) is not bundled — it targets video streams and is heavier than needed for a single report image.
