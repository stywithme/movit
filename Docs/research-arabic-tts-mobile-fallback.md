# Research: Arabic Male TTS as Mobile Fallback (Exercise Feedback)

**Date:** 2026-04-01  
**Goal:** Improve fallback speech when cached exercise audio is unavailable; target **Arabic, male voice**, with awareness of modern neural TTS quality.  
**Scope:** On-device / mobile-first options suitable for a fitness app (latency, offline, APK size).

---

## 1. Current implementation in this repository

| Location | Role |
|----------|------|
| [`android-poc/.../feedback/FeedbackManager.kt`](../android-poc/app/src/main/java/com/trainingvalidator/poc/training/feedback/FeedbackManager.kt) | Legacy `TextToSpeech`; `setLanguage(Locale.forLanguageTag("ar"))` for Arabic; **no voice selection** (gender/engine). |
| [`android-poc/.../feedback/AudioFeedbackPlayer.kt`](../android-poc/app/src/main/java/com/trainingvalidator/poc/training/feedback/AudioFeedbackPlayer.kt) | Preferred path: cached audio; **TTS fallback** with same `setLanguage("ar")` pattern. |

**Observed behavior:** The app relies on the **default Arabic voice** of whatever TTS engine the device uses (often Google “Speech Services” / Samsung / etc.). That default is frequently **female** and quality varies widely. There is no `setVoice()`, no explicit engine (`com.google.android.tts`), and no filtering by `Voice` features (e.g. male).

---

## 2. Categories of TTS relevant to “mobile fallback”

### A. Platform `TextToSpeech` (Android system API)

- **What it is:** Built-in Android API; actual voice quality and list depend on **installed TTS engine** (user-updatable via Play Store).
- **Pros:** No extra model in APK; low integration cost; works offline if language data is installed; familiar UX (“Download language” prompts).
- **Cons:** Inconsistent across OEMs; male Arabic not guaranteed unless you **enumerate voices** and pick one with male gender / preferred locale (`ar-SA`, `ar-XA`, etc.).
- **Quality:** Google’s engine on Pixel / GMS devices often exposes **neural-class** voices; older devices may fall back to lower quality.

**API hooks (Android):**

- `TextToSpeech(context, listener, "com.google.android.tts")` — prefer Google engine when installed.
- After `SUCCESS`: `getVoices()`, filter `Locale` + `features` / `name` (implementation-specific), then `setVoice(Voice)`.
- `Voice.getQuality()` includes `QUALITY_VERY_HIGH` where supported (see [Voice](https://developer.android.com/reference/android/speech/tts/Voice)).

### B. Cloud / API TTS (e.g. Google Cloud Text-to-Speech)

- **What it is:** HTTP/gRPC synthesis; voices like **Chirp 3 HD**, **Neural2**, **WaveNet** for Arabic (`ar-XA`).
- **Pros:** State-of-the-art quality; many **male** Arabic voices (see section 4).
- **Cons:** **Not a pure “fallback”** for offline training unless you **pre-generate audio** server-side (which you already prioritize via cache). Real-time cloud fallback needs network, adds cost, latency, and privacy considerations.

**Use case here:** Benchmark for “how good can Arabic male sound,” or **batch generation** of cached clips—not live fallback during a rep unless product accepts online dependency.

### C. On-device neural engines (bundled or separate APK)

| Option | Notes |
|--------|--------|
| **Sherpa-ONNX + Piper-style models** | Open-source stack; **offline** neural synthesis; project docs mention Arabic builds (e.g. regional variants, quality tiers). Larger APK or downloadable model packs; maintenance burden. |
| **Community wrappers (e.g. VoxSherpa-style apps)** | Illustrate embedding Sherpa-ONNX on Android; not a one-line drop-in—usually a **separate engine** or JNI integration. |
| **SILMA TTS (research / product)** | Reported strong **Arabic** focus, bilingual, on-device positioning; verify **license**, maturity, and Android integration for production. |
| **eSpeak NG** | Lightweight, **robotic** (formant); poor fit for “big improvement” coach voice. |

---

## 3. Google Cloud TTS — Arabic male voices (quality reference)

From Google Cloud’s public voice list (snapshot used for research), **Arabic (`ar-XA`)** includes many **MALE** entries, including:

- **Chirp 3 HD** — multiple male voices (e.g. naming pattern `ar-XA-Chirp3-HD-*`, gender MALE in catalog).
- **WaveNet** — `ar-XA-Wavenet-B`, `ar-XA-Wavenet-C` listed as **MALE**.
- **Standard** — `ar-XA-Standard-B`, `ar-XA-Standard-C` as **MALE**.

**Takeaway:** If the product ever uses **cloud or pre-generated** Arabic male audio, `ar-XA` + Chirp3 / WaveNet male is a strong baseline. This does **not** automatically map to the same voice on **device** `TextToSpeech`.

**Official list:** [Cloud Text-to-Speech supported voices](https://cloud.google.com/text-to-speech/docs/voices)

---

## 4. Practical “best fit” directions for this app

### Option 1 — **Recommended first step (low cost):** Harden Android `TextToSpeech` fallback

1. Instantiate with **Google TTS package** when available: `"com.google.android.tts"`.
2. After init, call `getVoices()` and select:
   - `locale` matching Arabic (`ar`, `ar-SA`, `ar-XA`, etc. — test which exists on target devices).
   - Prefer **male** if exposed via voice name/features (varies by engine).
   - Prefer higher `quality` / `latency` hints per [Voice](https://developer.android.com/reference/android/speech/tts/Voice) API.
3. Keep `setSpeechRate` / pitch tweaks for clarity during exercise.
4. Optional: `ACTION_INSTALL_TTS_DATA` or settings deep-link if Arabic data missing.

**Why:** Stays true “fallback,” no APK explosion, aligns with user expectation that “app uses phone TTS,” but **greatly improves** consistency for users who have Google’s Arabic male voices installed.

### Option 2 — **Pre-generated audio only (quality ceiling)**

- Use **Cloud TTS** (or similar) to generate **male Arabic** clips for all `LocalizedText` strings and ship/cache them—**TTS rarely runs**.
- Fallback TTS then only triggers for **missing** strings (Option 1 still helps).

### Option 3 — **Bundle a neural on-device engine (heavy)**

- **Sherpa-ONNX** (or similar) + Arabic model: best **offline** quality potential, at cost of **binary size**, CPU, integration, and updates.
- Evaluate only if Option 1 + caching still miss quality bar.

---

## 5. Risks and testing matrix

| Risk | Mitigation |
|------|------------|
| No male Arabic voice on device | Graceful fallback to any Arabic voice; optional UI message to install Google TTS / voice pack. |
| Samsung / Huawei / older engines | Test `getVoices()` on real devices; avoid hardcoding voice names where possible; prefer locale + gender heuristics. |
| Latency | Short phrases for feedback; queue modes already used in `FeedbackManager`. |

---

## 6. Summary table

| Approach | Arabic male likely? | Offline | Effort | Fits “fallback” |
|----------|---------------------|---------|--------|------------------|
| Improve `TextToSpeech` + `setVoice` + Google engine | Medium–High (device-dependent) | Yes | Low | **Yes** |
| Cloud TTS (live) | High | No | Medium | Poor as fallback |
| Pre-cache cloud male audio | High | Yes (playback) | Medium | **Yes** (primary audio path) |
| Sherpa-ONNX / Piper on-device | High (if model fits) | Yes | High | Yes, heavy |

---

## 7. Suggested next step (for product/engineering decision)

1. **Implement Option 1** in `FeedbackManager` + `AudioFeedbackPlayer` (shared helper): Google engine + voice selection for Arabic male preferred.  
2. **Measure** on 2–3 physical devices (Pixel + Samsung + one budget phone).  
3. If quality still insufficient for coaches, **Option 2** (expand cached male Arabic from Cloud TTS) before bundling a full on-device neural engine.

---

## 8. References (external)

- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)  
- [Android Voice](https://developer.android.com/reference/android/speech/tts/Voice)  
- [Google Cloud Text-to-Speech voices](https://cloud.google.com/text-to-speech/docs/voices)  
- [Sherpa-ONNX TTS documentation](https://k2-fsa.github.io/sherpa/onnx/tts/index.html)  
- Stack Overflow: [Android TTS male voice selection](https://stackoverflow.com/questions/36681232/android-tts-male-voices) (patterns vary by OS version)

---

## 9. SILMA / other proprietary models

**SILMA TTS** (and similar) were cited in general web search as modern Arabic-capable, on-device–oriented offerings. Before adoption: verify **license**, **Android SDK**, **model size**, and **commercial terms** independently; not evaluated in depth in this document.

---

*End of research log. Next step: choose Option 1 vs hybrid with pre-generated clips, then implement.*
