# Veritas — Visual Specification
**Version:** 1.0
**Companion to:** `01_ARCHITECTURE.md`, `veritas_mockup.html`
**Purpose:** Complete written specification of every screen, state, and user-facing surface in Veritas v1.

---

## 0. How to use this document

This spec describes every screen in Veritas. For each screen you get:

- **Purpose** — what this screen exists to do
- **Entry points** — how the user gets here
- **Exit points** — where they can go next
- **Layout** — top-to-bottom description of what's on screen
- **States** — every variant (empty, loading, error, populated)
- **Copy** — actual words on screen (not placeholders)
- **Edge cases** — what happens when things go wrong
- **Implementation notes** — Compose-specific guidance where non-obvious

The existing `veritas_mockup.html` gives the agent a pixel-level reference for aesthetic, typography, spacing, and color. This document specifies screens *not* in the mockup and pins down behavior for those that are.

**Aesthetic baseline, reaffirmed:**
- Dark theme is primary. Light theme is v1.1.
- Type: Manrope for UI, JetBrains Mono for metadata and verdicts.
- Verdict color language: green = confirmed authentic (C2PA), amber = uncertain, red = synthetic ensemble agreement. Never use color alone.
- Confidence is always a *range*, never a point estimate.
- Instrumentation feel: grids, corner brackets, mono metadata, calm motion.

---

## 1. Screen inventory

Complete list. Each screen is fully specified in the sections that follow.

### First-run & onboarding
- 1.1 Splash / cold start
- 1.2 Welcome
- 1.3 What Veritas does (3-step explainer)
- 1.4 How it works (on-device promise)
- 1.5 Entry-point setup (share-sheet confirmation)
- 1.6 Overlay opt-in intro
- 1.7 Overlay permission request
- 1.8 MediaProjection consent education
- 1.9 Notifications permission
- 1.10 Ready screen

### Core flow
- 2.1 App home
- 2.2 File picker entry
- 2.3 Paste-link entry
- 2.4 Scanning state
- 2.5 Verdict — Likely authentic
- 2.6 Verdict — Uncertain
- 2.7 Verdict — Likely synthetic
- 2.8 Forensic expanded view
- 2.9 Per-reason detail sheet

### Overlay mode
- 3.1 Floating bubble (idle)
- 3.2 Floating bubble (scanning)
- 3.3 Verdict toast
- 3.4 Bubble-moved / snap-to-edge
- 3.5 Overlay disabled (persistent notification)

### History & library
- 4.1 History list (populated)
- 4.2 History list (empty)
- 4.3 History item detail
- 4.4 History search / filter

### Settings
- 5.1 Settings home
- 5.2 Overlay settings
- 5.3 Model management
- 5.4 Privacy & data
- 5.5 Diagnostic export
- 5.6 About & licenses
- 5.7 Telemetry opt-in

### Errors & edge states
- 6.1 No network (model update)
- 6.2 Model update failed
- 6.3 Model update required (hard stop)
- 6.4 File too large
- 6.5 Duration too long
- 6.6 Unsupported codec / format
- 6.7 Corrupted file
- 6.8 Analysis crashed / recovery
- 6.9 C2PA signature invalid
- 6.10 C2PA signature revoked
- 6.11 Storage full
- 6.12 NPU unavailable (fallback notice)
- 6.13 Device unsupported

### System & background
- 7.1 Foreground-service notification (idle)
- 7.2 Foreground-service notification (active scan)
- 7.3 Model-update-complete notification
- 7.4 Share-target registration in system share sheet

---

## 2. First-run & onboarding

The onboarding flow is the single highest-leverage UX surface in the app. It sets the trust posture, obtains the permissions that make the product work, and is the first thing Play Store reviewers will look at. Every screen must be crossable in one tap, skippable where legally allowed, and never request permissions without explaining why on the same screen.

### 1.1 Splash / cold start
**Purpose:** Branded loading surface while the app initializes model registry, checks for signed model updates, and decides the first destination.

**Layout:**
- Full-bleed dark background (`--bg`)
- Centered brand mark (18×18 square with inner dot, as per logo in mockup)
- Brand name "VERITAS" in mono, letter-spacing 0.18em, below mark
- Tiny subtitle in `--ink-mute`: "Forensic media verification"
- Activity indicator only appears if cold-start exceeds 400 ms — never flash it instantly

**States:**
- **Normal:** splash visible ≤ 500 ms, fades to welcome (first run) or home (subsequent runs) with a 150 ms crossfade
- **Model-update-required-hard:** see 6.3

**Copy:** `VERITAS` / `Forensic media verification`

**Implementation notes:** Use a real Compose splash, not just the Android system splash — the system splash can't show the subtitle. Hand off from system splash to Compose splash via `installSplashScreen()` and hold for up to 500 ms.

---

### 1.2 Welcome
**Purpose:** Frame what this tool is in one sentence, set expectation that it takes ~6 screens to set up properly.

**Layout:**
- Top: brand mark + name (small, top-left, mono)
- Middle third: large display type, weight 300, letter-spacing -0.03em
- Heading: `Know what's real.`
- Subheading (Manrope, 16sp, `--ink-dim`): `Veritas checks videos, audio, and images for signs they were made by AI — right on your phone, in about 4 seconds.`
- Spacer
- Bottom: primary button `Get started` (full-width, `--ink` on `--bg`)
- Tiny text link below: `I've used Veritas before` → skips to home

**Edge cases:**
- If the user has used Veritas before on this device but cleared data, show the same screen — we can't distinguish.

**Copy:** all above.

---

### 1.3 What Veritas does (3-step explainer)
**Purpose:** Set mental model in three beats. Scroll-paged horizontally with dots, or vertical list — choose vertical for accessibility and speed (no swipe discoverability problem).

**Layout:** vertical list, 3 cards, each:
- Numeric badge (mono, `01` `02` `03`)
- Icon glyph (stroke only, 24dp) — corresponding to: magnifier, shield-check, warning-triangle
- Title (16sp, weight 700)
- Body (14sp, `--ink-dim`, line-height 1.5)

**Card 1 — "01 · Check":**
- Title: `Check any video, audio, or image`
- Body: `Share a file from any app — TikTok, WhatsApp, Instagram, anywhere. Or pick one from your phone.`

**Card 2 — "02 · Verify":**
- Title: `See evidence, not a verdict`
- Body: `Veritas shows you where the signs are — a heatmap, a timeline, and plain-English reasons. You decide what to trust.`

**Card 3 — "03 · Keep it private":**
- Title: `Nothing leaves your phone`
- Body: `All analysis runs on-device. Your media is never uploaded, never shared, never stored longer than needed.`

**Bottom:** primary button `Continue` + `Back` text button.

---

### 1.4 How it works (on-device promise)
**Purpose:** Establish the privacy and trust posture before asking for any permissions. This is the page that earns the right to ask for overlay permission on screen 1.6.

**Layout:**
- Heading (large display, weight 300): `Your phone, not a server.`
- Subhead (`--ink-dim`): `Here's exactly what Veritas does and doesn't do.`
- Divider
- Two-column comparative list (on narrow screens, stack):

**"Veritas does" (with check icon, `--ok`):**
- Run detection models on your device's NPU
- Look up Content Credentials (C2PA) via signed public directories
- Download signed model updates over the internet

**"Veritas does not" (with x icon, `--bad`):**
- Upload, store, or share the media you check
- Keep your media after you close a verdict
- Have user accounts or track you across apps
- Send your verdicts to advertisers or anyone else

- Bottom: primary button `I understand` + text link `Read full privacy policy` (opens 5.4 in modal)

**Copy:** exact text above.

---

### 1.5 Entry-point setup (share-sheet confirmation)
**Purpose:** Confirm that the share-sheet is the primary entry point and teach the user how to use it. No permission needed — share intents are declared in manifest and automatically available.

**Layout:**
- Heading: `You'll find Veritas in the share menu.`
- Illustration: a stylized share sheet (reuse visual from mockup Section 1, first phone) showing Veritas in the top row, highlighted.
- Body: `When you see a suspicious video, tap Share → Veritas. We'll take it from there.`
- Secondary note (`--ink-mute`, smaller): `Some apps only allow sharing as a link. Paste the link into Veritas instead — we'll resolve it for you.`
- Primary button: `Got it`
- Text link: `Show me an example` → plays a 4-second GIF/Lottie demo (deferred to v1.1; v1 just skips this)

---

### 1.6 Overlay opt-in intro
**Purpose:** Pitch the overlay mode as optional, explain what it is, and get the user to either opt in or skip.

**Layout:**
- Heading: `Want a faster way?` (weight 300)
- Subhead: `The Veritas bubble floats over any app. One tap to check what's on screen.`
- Illustration: the floating bubble screen from the mockup, shown at ~70% scale, cropped
- **Key disclosures (bordered box, important for Play Store review):**
  - Icon + `Only captures when you tap.` — No background recording, ever.
  - Icon + `One-second buffer.` — The bubble grabs what's on screen now, analyzes it, shows the verdict.
  - Icon + `You'll see a notification.` — Android requires a persistent notification when screen capture is active. We cannot hide this.
- Two buttons, equal width:
  - Secondary `Skip for now` (returns to 1.9)
  - Primary `Enable bubble` (proceeds to 1.7)

**Copy:** exact above.

**Implementation notes:** The three bullets are load-bearing for Play Store data-safety compliance. They must appear before the permission request. Do not weaken them.

---

### 1.7 Overlay permission request
**Purpose:** Handle the `SYSTEM_ALERT_WINDOW` permission flow, which on Android cannot be requested with a standard runtime dialog — it requires sending the user to system Settings.

**Layout:**
- Heading: `Allow Veritas to display over other apps`
- Body: `Tap the button below, then turn on "Allow display over other apps" in Settings. Come right back when you're done.`
- Visual: annotated screenshot/illustration of the system settings toggle (produce as asset; agent should stub with placeholder and note asset need)
- Primary button: `Open settings` → `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
- Text link: `I changed my mind, skip` → returns to 1.9

**States:**
- **Before grant:** as above
- **After return from settings, not granted:** body changes to `Looks like that didn't get turned on. No worries — you can still use Veritas by sharing files. You can turn this on later in settings.` Primary button becomes `Continue without bubble` → 1.9
- **After return from settings, granted:** automatic advance to 1.8

**Edge cases:**
- User backgrounds the app, re-enables overlay elsewhere, returns. On resume, re-check permission and advance if granted.

---

### 1.8 MediaProjection consent education
**Purpose:** Pre-educate the user about the MediaProjection consent dialog they'll see every time they tap the bubble. Android 14+ requires re-consent per session and this cannot be cached beyond a session on modern Android.

**Layout:**
- Heading: `One more thing about the bubble.`
- Body: `Every time you tap it, Android will ask "Allow Veritas to record or cast your screen?" — tap "Start now". This happens fresh each time for your security.`
- Illustration: stylized version of the Android consent dialog
- Primary button: `Makes sense`

**No permission is requested here.** This is pure education. The actual `MediaProjection` consent happens later, every time the bubble is tapped.

---

### 1.9 Notifications permission
**Purpose:** Request `POST_NOTIFICATIONS` permission (Android 13+). Required for foreground-service notification and model-update-complete notifications.

**Layout:**
- Heading: `Can we send notifications?`
- Body: `We use notifications for two things: to show that the bubble is active (Android requires this), and to tell you when a model update finishes. Nothing else.`
- Primary button: `Allow`
- Secondary: `Skip`

**States:**
- **Granted:** advance to 1.10
- **Denied:** show a small inline warning: `The bubble will still work, but you won't see update completion alerts.` Primary becomes `Continue`.

**Implementation notes:** Call `ActivityCompat.requestPermissions()`. If min-SDK is below 33, skip this screen entirely.

---

### 1.10 Ready screen
**Purpose:** Completion confirmation. Set expectations for what happens next.

**Layout:**
- Brand mark animates in (simple fade + scale)
- Heading: `You're set up.`
- Body (paragraph, `--ink-dim`): `Share a video or image to Veritas from any app. Or open one from your phone using the button below.`
- Primary button: `Open Veritas` → app home (2.1)

**Copy:** exact above.

**Implementation notes:** Also sets a persistent `hasCompletedOnboarding = true` flag. Subsequent cold starts skip to 2.1.

---

## 3. Core flow

### 2.1 App home
**Reference:** Mockup row 3, third phone ("APP HOME").

**Purpose:** Entry point for users who open Veritas directly (not via share sheet or overlay). Offers file pick + paste link, and shows recent history as a prompt to re-open past verdicts.

**Layout:** (exact match to mockup)
- Top: brand header with mark + `VERITAS`, right side has `SETTINGS` text link → 5.1
- Hero section (gradient top-right glow):
  - Mono tag: `READY`
  - Display heading: `Verify anything on your screen.` (weight 300, strong on "anything")
  - Two CTAs: primary `Pick a file`, ghost `Paste link`
- Recent section:
  - Mono label: `RECENT · N` (N = count, up to 3 shown)
  - List of up to 3 history items (see rec-item pattern in mockup)
  - Each: thumbnail, source text (e.g., "Shared from TikTok"), timestamp, verdict pill
- Bottom nav: `VERIFY` (active), `HISTORY`, `ABOUT`

**States:**
- **Empty (no history):** recent section shows single prompt card: `No checks yet. Share a file to get started.` — no list items, no mono label count.
- **Populated:** as above.

**Interactions:**
- Pick a file → 2.2
- Paste link → 2.3
- Tap recent item → 4.3
- Settings → 5.1

**Implementation notes:** Use `ACTION_OPEN_DOCUMENT` with MIME type filter, not `ACTION_PICK`. The Photo Picker is preferred on Android 13+ — use `PickVisualMedia` contract for image/video, fall back to `ACTION_OPEN_DOCUMENT` for audio.

---

### 2.2 File picker entry
**Purpose:** Launches system picker. Not a Veritas-drawn screen — it's the OS photo picker or document picker.

**Flow:**
1. User taps `Pick a file` on 2.1
2. Launch `PickVisualMedia` or `ACTION_OPEN_DOCUMENT`
3. On selection, copy to scoped storage, advance to 2.4 (scanning)
4. On cancel, return to 2.1

**Edge cases:**
- File > 200 MB → 6.4 before copy completes (check via `ContentResolver` cursor, not after copy).
- Duration > 60 s for video, > 180 s for audio → 6.5.
- MIME type not in allow list → 6.6.

---

### 2.3 Paste-link entry
**Purpose:** Fallback for platforms (TikTok) that only let users share a link. Veritas attempts to resolve the link to a media file.

**Layout:**
- Modal sheet, slides up from bottom, 60% height
- Heading: `Paste a link`
- Subhead: `We'll fetch the video and check it on your device.`
- Large text input, paste icon button on right, auto-focus keyboard
- Tiny text under input: `Supports TikTok, Instagram Reels, YouTube Shorts, Twitter/X posts.`
- Primary button at bottom: `Check this` (disabled until URL-shaped input detected)
- Close X in top-right

**States:**
- **Empty:** button disabled.
- **Valid URL:** button enabled.
- **Invalid URL:** inline error under input: `That doesn't look like a link we can open.`
- **Fetching:** spinner on button, text `Fetching…`, button stays disabled.
- **Fetch failed:** error banner: `Couldn't fetch that link. The post might be private or deleted.` Button re-enables.
- **Fetched OK:** advance to 2.4.

**Implementation notes:** Use a first-party resolver service for v1 (simple server that takes a public URL, returns a CDN media URL). Do **not** build platform-specific scrapers that violate ToS. If no resolver, treat this as a v1.1 feature and hide the button until then.

**⚠️ Decision flag for developer:** this feature has real legal and ToS complexity. It's easy to ship, easy to get sued over. Consider deferring to v1.1 and launching v1 with share-sheet + direct-open only.

---

### 2.4 Scanning state
**Reference:** Mockup row 1, third phone ("SCANNING").

**Purpose:** Provide visible, honest feedback that something is happening. Users who can see the pipeline trust it more than users who see a generic spinner.

**Layout:** (exact match to mockup)
- Standard verdict-screen chrome (status bar, brand header, close button)
- Scan preview area (flex: 1): dark background, blurred representation of the media being scanned, corner brackets, animated scan-line
- Below preview: `ANALYSIS · N OF 5 STAGES` mono label
- Five stage rows:
  1. `C2PA manifest check`
  2. `Watermark scan (SynthID)`
  3. `Spatial artifact model` (for image/video)
  4. `Temporal consistency` (video only) / `Spectral analysis` (audio)
  5. `Fusion & calibration`

Each row: dot (neutral → pulsing accent when active → green when done), label, right-aligned meta (`QUEUED` / `0.8s` elapsed / result like `NONE` for C2PA).

**Behavior:**
- Steps run in order, visible. Each step animates in ~100 ms when it starts.
- If a step completes faster than 200 ms, hold its "active" state to at least 200 ms so the animation reads.
- Total target time: ≤ 4 s on mid-range 2024 device. If exceeded, no UI change — just let it continue. Do NOT show a "taking longer than expected" warning unless > 15 s (see 6.8).
- Close button cancels analysis, returns to previous screen.

**States:**
- **Running:** as above.
- **Cancelled:** instant return to previous screen, no confirm dialog (user intent is clear).
- **Failed (any stage errors):** 6.8.

**Implementation notes:** Stage states (`queued/active/done/failed`) are surfaced from the detection pipeline module via Flow. UI is purely reactive.

---

### 2.5 Verdict — Likely authentic
**Reference:** Mockup row 2, first phone.

**Purpose:** Confirm provenance when C2PA verifies or detectors strongly agree on authenticity.

**Layout:** (exact match to mockup)
- Chrome: status bar, brand header, close
- Tag (mono, `--ink-mute`): `VERDICT · VIDEO · 0:47` (media type, duration)
- Verdict (weight 800, 36sp, `--ok`): `Likely authentic.` (two lines allowed)
- Confidence range bar with range segment + mono percentage `78–94%`
- Summary sentence (`--ink-dim`): explains the strongest positive signal. Strong words (device names, issuing authority) in `--ink`
- Evidence chips (up to 3): each has mono code in `--ok` (`C2PA ✓`, `rPPG ✓`, `CODEC ✓`) and plain-language description
- Bottom actions: primary `See details` (→ 2.8), secondary `Done` (closes activity)

**Copy examples:**
- Summary when C2PA present: `Signed by a verified camera. Content Credentials match a [Device Name] recording from [date].`
- Summary when C2PA absent but detectors agree: `No signs of AI generation across all detectors. No Content Credentials are attached — authenticity isn't confirmed, but nothing looks synthetic.`

**Important verdict-language rule:** The second case above should read more softly. Absence of synthetic signal is not the same as verified provenance. Consider a different verdict framing for that case — `Looks authentic` (softer) vs `Likely authentic` (stronger, reserved for C2PA-verified).

**⚠️ Decision flag:** We should probably distinguish these two verdicts in v1. Proposed:
- `Verified authentic` (C2PA valid, green)
- `Looks authentic` (no C2PA, detectors clean, still green but lighter)

Agent: default to two-tier green unless explicitly overridden.

**States:**
- **C2PA verified:** full green, strong language, device name shown.
- **No C2PA, clean scan:** softer green, "Looks authentic" verdict, no device attribution.

---

### 2.6 Verdict — Uncertain
**Reference:** Mockup row 2, second phone.

**Purpose:** First-class verdict for low-quality input, detector disagreement, or out-of-distribution content. **Must not read as alarming.** Amber, calm, explanatory.

**Layout:** (match mockup)
- Chrome as usual
- Tag: `VERDICT · VIDEO · 0:12`
- Verdict: `Uncertain.` (`--warn`)
- Confidence range: wide, straddling middle — e.g. `31–65%`
- Summary: explains *why* it's uncertain. Bolded call-to-action at end: `Don't trust or dismiss this — seek a higher-quality source.`
- Evidence chips (amber border, `--warn` code):
  - `QUALITY` — re-encodes, low resolution
  - `DISAGREE` — which detectors disagreed
  - `NO SIG` — no Content Credentials
- Bottom actions: primary `Find original` (→ 2.9 search help sheet, or web search intent for the media's likely source), secondary `Done`

**Copy rules:**
- Never use the words "fake," "real," "fraud," "lie," or similar in uncertain copy.
- Do use: "unclear," "can't tell," "not enough signal."
- Always suggest a next action.

---

### 2.7 Verdict — Likely synthetic
**Reference:** Mockup row 2, third phone.

**Purpose:** Clearly communicate that ensemble detectors agree this is AI-generated, with calibrated confidence and multiple independent reasons.

**Layout:** (match mockup)
- Chrome as usual
- Tag: `VERDICT · VIDEO · 0:23`
- Verdict: `Likely synthetic.` (`--bad`)
- Confidence range: narrow, high — e.g., `82–96%`
- Summary: names the strongest two indicators explicitly
- Evidence chips (red border, `--bad` code): top 3 contributing reasons, highest-weight first
- Bottom actions: primary `See heatmap` (→ 2.8), secondary `Done`

**Verdict-language rule:** Never display confidence > 96% for synthetic. Cap at 96% because honest state-of-the-art on real-world input doesn't support higher. Cap at 94% for authentic for the same reason.

**Edge case:** If only one detector flags synthetic and others are neutral, the verdict should be Uncertain (2.6), not Likely Synthetic. This is enforced in fusion logic per architecture §6.5, surfaced here for UX clarity.

---

### 2.8 Forensic expanded view
**Reference:** Mockup row 3, first phone ("FORENSIC VIEW").

**Purpose:** For users who want to see *why*. Visual heatmap, temporal timeline, and weighted reason codes.

**Layout:** (match mockup)
- Chrome: brand changes to `FORENSIC` mode, close button
- Header strip: verdict pill (red/amber), frame indicator (e.g., `Frame 127 · 0:04.2`)
- Heatmap viewport (4:3 aspect): shows the flagged frame with Grad-CAM overlay. Red/amber blobs over suspect regions. Labels with callout lines (`EAR ⟶`, `← MOUTH`)
- Temporal confidence timeline bar: 8 segments representing per-second confidence, colored by severity
- Timeline ticks below
- Reason list (scrollable):
  - Label: `REASON CODES · N FLAGS`
  - Each reason: left-border accent color (`--bad`/`--warn`/`--ok`), mono code, weight number (`weight 0.38`), plain description
- Footer: `Export` secondary + `Back to verdict` primary

**Interactions:**
- Tap heatmap → full-screen overlay view with pinch-zoom
- Tap timeline segment → scrubs the heatmap viewport to that time's flagged frame
- Tap reason → 2.9
- Export → 5.5 diagnostic export flow
- Back → returns to verdict card (2.5/2.6/2.7)

**States:**
- **Video:** all features available
- **Audio:** heatmap viewport is replaced by waveform visualization with red-annotated regions; timeline still shows; no frame indicator
- **Image:** heatmap viewport shows image with overlay; no timeline; frame indicator shows region label instead (e.g., `Region: facial`)

**Implementation notes:** Grad-CAM output is a per-frame heatmap tensor from the detection model. Render with `Canvas` using `BlendMode.Screen` for the blob overlays. Labels are standard Compose `Text` with pointer lines as `drawLine` in `Canvas`.

---

### 2.9 Per-reason detail sheet
**Purpose:** When a user taps a reason code, show a longer explanation. This is where we educate non-experts.

**Layout:**
- Modal bottom sheet, 50% height (expandable to full)
- Handle at top
- Mono code header: `LIP_SYNC_DRIFT`
- Weight: `Contribution: 38% of synthetic score`
- Plain title: `Audio-visual misalignment`
- Body (2–3 paragraphs):
  - What this is ("When a person speaks, their lip movements and the audio line up within about 40ms. AI-generated videos often have small mismatches...")
  - What Veritas found (the specific measurement from this video)
  - Why it matters (how reliable this signal is in isolation)
- Timestamps at which flags occurred (tappable, scrubs forensic view)
- Close button

**Content management:** Reason explanations are template-driven, keyed on reason code. Template is filled with per-scan values (measurements, timestamps). Store templates in resource files for translation readiness.

**Full reason-code list** (for agent to generate templates for all):
- `C2PA_VERIFIED_*` (device/tool name substituted)
- `C2PA_INVALID_SIGNATURE`
- `C2PA_REVOKED`
- `SYNTHID_DETECTED_*`
- `LIP_SYNC_DRIFT`
- `RPPG_ABSENT`
- `RPPG_IMPLAUSIBLE`
- `DIFFUSION_SPECTRAL_SIG`
- `GAN_SPECTRAL_SIG`
- `TEMPORAL_INCONSISTENCY`
- `EAR_GEOMETRY_DRIFT`
- `TEETH_ARTIFACTS`
- `JEWELRY_FLICKER`
- `EYE_REFLECTION_MISMATCH`
- `AUDIO_SPECTRAL_NEURAL`
- `AUDIO_PROSODY_UNNATURAL`
- `AUDIO_CODEC_MISMATCH`
- `METADATA_IMPLAUSIBLE`
- `ELA_INCONSISTENT`
- `COMPRESSION_HEAVY` (uncertain-leaning)
- `LOW_RESOLUTION` (uncertain-leaning)
- `DETECTOR_DISAGREEMENT` (uncertain-leaning)

---

## 4. Overlay mode

### 3.1 Floating bubble (idle)
**Reference:** Mockup row 1, second phone.

**Purpose:** Persistent, draggable, tappable trigger that floats over the user's current app.

**Layout:**
- Circle, 54dp diameter
- Dark radial gradient fill (`#1a1d24` → `#0a0b0d`)
- 1.5dp accent border (`--accent`)
- Outer glow (24dp blur, accent 50% alpha)
- Inner dot (10dp, accent, glowing)
- Slight drop shadow for lift

**Behavior:**
- Long-press: enters drag mode. Bubble becomes slightly larger (scale 1.1). Follow finger with light haptic.
- Release: snap to nearest edge (left or right), animate to 16dp from edge.
- Tap: triggers capture flow (3.2).
- If dragged to bottom-center "trash" zone that appears during drag: dismiss bubble (brings up 3.5 in notification).

**States:**
- Idle (as above)
- Dragging (scaled, slight color shift)
- Over dismiss zone (trash zone highlights, bubble tints red)

**Implementation notes:** Use `WindowManager` with `TYPE_APPLICATION_OVERLAY`. Don't use Compose for the bubble surface — use classic `View` + gesture listener, per architecture §16. Compose can be used for the toast (3.3) which is a separate overlay window.

---

### 3.2 Floating bubble (scanning)
**Purpose:** Feedback that the tap was registered and analysis is in progress, without covering the user's content.

**Layout:**
- Bubble stays in place
- Inner dot becomes an animated rotating arc (thin stroke, accent color, spinning 2s per revolution)
- Subtle pulse on outer glow (1.2s interval)

**Behavior:**
- Duration: until verdict ready (typically 2–4 s)
- No toast yet
- If user taps bubble during scan: no-op (ignore second tap to prevent duplicate requests)

**States:**
- Short scan (<2s): go directly from idle → scanning → toast with no additional feedback
- Long scan (>4s): bubble gains a small `...` indicator near it, suggesting more time needed
- Scan failed: return to idle, no toast, small one-shot haptic bump

---

### 3.3 Verdict toast
**Reference:** Mockup row 3, second phone.

**Purpose:** Deliver the verdict over the user's current app without forcing a context switch.

**Layout:** (match mockup)
- Positioned above system navigation, 14dp from sides, 80dp from bottom
- Rounded 18dp corners
- Dark glass effect (backdrop blur, `rgba(10,11,13,0.96)`)
- 1dp border (`--line-2`)
- Top row: status dot (color per verdict) + verdict label (mono, uppercase) + confidence range on right
- Body line: one-sentence summary (ink primary), supporting detail (ink-dim, inline or wrapping)
- Actions row: primary `Why?` (opens full verdict activity 2.5/2.6/2.7), secondary `Dismiss`

**Behavior:**
- Enters with slide-up + fade (200ms)
- Auto-dismisses after 8 seconds unless user interacts
- Tap anywhere on toast body → opens full verdict activity
- `Why?` → opens full verdict activity jumping straight to forensic view (2.8)
- `Dismiss` → fade out (150ms)
- Swipe down on toast → dismiss

**States:**
- Per verdict (ok/warn/bad) — color of dot, color of verdict label
- Long summary: truncate with ellipsis after 2 lines, full text shown in verdict activity

**Implementation notes:** Implement as separate overlay window so it doesn't interfere with bubble touch handling. Bubble and toast are two independent overlay surfaces.

---

### 3.4 Bubble snap-to-edge
**Purpose:** Keep the bubble out of the way when the user isn't actively using it.

**Behavior:**
- On release from drag, animate to 16dp from nearest vertical edge (spring animation, ~300ms)
- Vertical position preserved
- If released in top 80dp or bottom 80dp (reserved for status bar and navigation), clamp to 100dp from top / 100dp from bottom
- Position persisted across sessions via DataStore

**No dedicated screen — this is a behavior spec.**

---

### 3.5 Overlay disabled (persistent notification)
**Purpose:** When the user drags the bubble to the dismiss zone, the bubble is hidden but the foreground service continues (so it can be restored). A notification explains this.

**Notification:**
- Title: `Veritas bubble hidden`
- Body: `Tap to bring it back. Or turn it off in settings.`
- Action 1: `Bring back`
- Action 2: `Turn off`
- Ongoing: yes (cannot be swipe-dismissed — reflects a real foreground service)

**On "Turn off":** service stops, notification clears, overlay toggle in 5.2 is set to off.

---

## 5. History & library

### 4.1 History list (populated)
**Purpose:** Revisit past verdicts. All data is local — clearing app data clears history.

**Layout:**
- App chrome with `HISTORY` active in bottom nav
- Top: search bar (full-width, placeholder `Search history`) — v1.1, stub in v1
- Filter chips row: `All`, `Authentic`, `Uncertain`, `Synthetic` — v1.1, stub in v1
- List of history items, grouped by date (`Today`, `Yesterday`, `This week`, `Earlier`):
  - Group header (mono, `--ink-mute`)
  - Items: thumbnail, source text, timestamp, verdict pill
  - Divider between items

**Data kept per item:**
- Thumbnail (generated at scan time, stored in app private storage)
- Media type and duration
- Source app (from `EXTRA_REFERRER` or share intent)
- Verdict + confidence range
- Top 3 reason codes
- Scan timestamp
- Model version used

**Data NOT kept:** the original media file (auto-purged per architecture §8.5).

**Interactions:**
- Tap item → 4.3
- Long-press → context menu: `Delete`, `Export diagnostic`

**Implementation notes:** Store history in Room. Keep at most 100 items; auto-prune oldest on insert above cap.

---

### 4.2 History list (empty)
**Layout:**
- Centered illustration (simple line-art of a magnifier over a document)
- Heading: `No checks yet.`
- Body (`--ink-dim`): `Verdicts you run appear here. Everything stays on your phone.`
- Primary button: `Check something` → 2.1

---

### 4.3 History item detail
**Purpose:** Show the preserved verdict for a past scan. Behaves like the verdict screen (2.5/2.6/2.7) but with explicit framing that this is historical.

**Layout:**
- Same as corresponding verdict screen
- Add a strip near top: `Checked [date] · [source app]` (mono, `--ink-mute`)
- If the current model version differs from the one used at scan time, add banner: `Detection models have been updated since this scan. Results may differ now.` with button `Re-scan` (only available if original file still cached — usually no for items older than 60s, so typically just informational)

**Interactions:** as per verdict screen, plus Delete in top-right overflow.

---

### 4.4 History search / filter
**Deferred to v1.1.** In v1, search bar and filter chips are hidden. Stub the UI and hide behind feature flag.

---

## 6. Settings

### 5.1 Settings home
**Purpose:** Navigation to all settings sections.

**Layout:**
- Sectioned list (Material-style preference screen)
- Sections with headers:
  - `OVERLAY` → 5.2 (subtitle: current state, "On" or "Off")
  - `MODELS` → 5.3 (subtitle: current model version)
  - `PRIVACY & DATA` → 5.4
  - `DIAGNOSTICS` → 5.5
  - `ABOUT` → 5.6

**Bottom:** Version string (mono, small, `--ink-mute`): `v1.0.0 · build 142`

---

### 5.2 Overlay settings
**Purpose:** Control the floating bubble.

**Rows:**
- **Floating bubble** (toggle) — off = service stops, bubble hidden. On = request overlay permission if needed, start service.
- **Bubble position** — sub-screen: Left / Right / Let me drag (default) — v1.1
- **Haptic feedback** (toggle) — default on
- **Auto-hide toast** — Slider: 4 / 8 / 12 / Never (v1.1, default 8 in v1)

**Bottom disclosure:**
- Text (`--ink-dim`): `The bubble requires two Android permissions: "Display over other apps" and screen recording consent each time you tap it. Veritas never captures unless you tap.`

---

### 5.3 Model management
**Purpose:** Show current models, update status, allow manual refresh.

**Layout:**
- Current model header: `Active models` (mono)
- Three rows, one per model family:
  - `Video detector` · `v2.4.1` · size `12.8 MB` · last updated `2 days ago`
  - `Audio detector` · `v1.8.0` · size `8.1 MB` · last updated `2 days ago`
  - `Image detector` · `v3.1.2` · size `6.4 MB` · last updated `5 days ago`
- Button: `Check for updates` (shows spinner while running)
- Auto-update toggle: `Update over Wi-Fi only` (default on), `Auto-update` (default on)
- Advanced (expandable): shows LiteRT delegate status per model and current inference hardware (GPU / CPU fallback). On vendor-delegate-enabled devices (v1.1), shows the active vendor delegate name.

**States:**
- **Updating:** row shows progress bar, estimated time
- **Update failed:** row shows red icon, tap for details (6.2)
- **Update available:** row highlighted, CTA `Update now` appears

---

### 5.4 Privacy & data
**Layout (scrollable):**
- Heading: `Your data, your device.`
- Sections:
  - **What Veritas stores** (list): Only history items (thumbnails, verdicts, metadata). Not the original media.
  - **What Veritas sends** (list): Signed model updates downloaded. Anonymous telemetry if enabled (see 5.7).
  - **What Veritas never does** (list): Upload your media. Sell your data. Track you across apps. Create user accounts.
- Row: **Clear history** → confirmation dialog → clears all history items
- Row: **Clear all data** → confirmation dialog → resets app to first-run state
- Link row: **Read privacy policy** → opens web view (privacy policy URL)

---

### 5.5 Diagnostic export
**Purpose:** Let users generate a support bundle if something goes wrong. **No media is ever included.**

**Layout:**
- Heading: `Export diagnostic`
- Body: `Creates a text file with your settings, model versions, recent errors, and device info — no media, no personal data. Useful for reporting bugs.`
- Button: `Create export` → generates file, opens share sheet for export
- Expandable: shows what's included (device model, Android version, app version, model versions, last 50 error log entries, feature-flag state, permission state)

**Important:** `VERDICT_EXPORT_VERIFICATION` — when triggered from history item context menu, output includes the reason codes and confidence range for that specific verdict, **still without the original media**.

---

### 5.6 About & licenses
**Rows:**
- App version
- Terms of service → web view
- Privacy policy → web view (same as 5.4)
- Open source licenses → screen with scrollable list (Compose, Kotlin, LiteRT, c2pa-rs, etc.)
- Credits
- Contact support → mailto intent

---

### 5.7 Telemetry opt-in
**Purpose:** Collect aggregate verdict-distribution and crash telemetry to improve detection quality. Strictly opt-in.

**Shown:** once, after first successful scan, as a modal.

**Layout:**
- Heading: `Help improve detection`
- Body: `Veritas can send anonymous, aggregate data to improve our models:`
  - Bullets:
    - Verdict distribution (how often each verdict occurs)
    - Device type and model version
    - Crash reports
  - Never: your media, your history, your identity, the file hashes, the reason codes from specific scans
- Two buttons, equal: `Share anonymous data` (primary) and `No thanks` (secondary)
- Text link: `Decide later in settings` (same as No thanks but without committing)

---

## 7. Errors & edge states

Each error screen follows a common pattern unless noted: icon at top (24dp, stroke only), heading (weight 600, 18sp), body (`--ink-dim`, 14sp), primary action, optional secondary action.

### 6.1 No network (model update)
**Trigger:** Update attempt while offline.
**Layout:** icon: wifi-slash. Heading: `No connection`. Body: `Veritas can't check for model updates right now. Your current models still work — you can scan as normal.` Primary: `OK`.

### 6.2 Model update failed
**Trigger:** Download error, signature verification failed, etc.
**Layout:** icon: alert-triangle. Heading: `Update didn't finish`. Body varies by cause:
- Network error: `The download was interrupted. We'll try again later, or tap below to retry now.`
- Signature error: `The update file didn't pass our security check. We've blocked it. You're still on the previous version.`
- Storage full: `Not enough space to install the update.` (See 6.11)

Primary: `Retry` (if network); `OK` (if signature — no retry for security).

### 6.3 Model update required (hard stop)
**Trigger:** Current models are below a minimum version required for safety (e.g., after a critical accuracy regression). Rare.
**Layout:** Full-screen takeover. Icon: update. Heading: `Update required`. Body: `Veritas needs to update its detection models before you can scan. This keeps your results accurate.` Primary: `Update now` (starts download, shows progress). No "skip" — app is unusable until complete or user exits.

### 6.4 File too large
**Trigger:** File > 200 MB.
**Layout:** Inline on scan screen, replaces scan preview. Icon: file. Heading: `This file is too large`. Body: `Veritas can check videos up to 200 MB. Try a shorter clip or a lower-resolution copy.` Primary: `Done`.

### 6.5 Duration too long
**Trigger:** Video > 60s or audio > 180s.
**Layout:** similar to 6.4. Body: `Veritas v1 handles videos up to 60 seconds and audio up to 3 minutes. Longer support is coming.` Primary: `Done`. Secondary (if video): `Check first 60 seconds` — truncates and proceeds.

### 6.6 Unsupported codec / format
**Layout:** Body: `Veritas doesn't recognize this file format. We support common formats: MP4, WebM for video; MP3, AAC, WAV, M4A for audio; JPEG, PNG, WebP, HEIC for images.`

### 6.7 Corrupted file
**Layout:** Body: `This file didn't decode properly. It might be damaged or only partially downloaded.`

### 6.8 Analysis crashed / recovery
**Trigger:** Detection pipeline threw exception, OOM, etc.
**Layout:** Body: `Something went wrong during the scan. It's not your fault — we've logged the error. Try again, or tap Export below to send us a diagnostic.` Primary: `Try again`. Secondary: `Export diagnostic`.

**Long scan warning** (different, not crash): If scan exceeds 15 seconds, replace scan stages with: Body: `This is taking longer than usual. Large files or heavy compression can slow things down.` Primary: `Keep waiting`. Secondary: `Cancel`.

### 6.9 C2PA signature invalid
**Trigger:** Manifest present but signature fails validation. This is a strong *negative* signal — someone attached a forged manifest.
**Treatment:** Contributes negatively to the synthetic score. In forensic view, shows as dedicated reason `C2PA_INVALID_SIGNATURE` with explanation: `This file claims to be from [claimed origin] but the signature doesn't match. That's a stronger sign of tampering than no signature at all.`

### 6.10 C2PA signature revoked
**Trigger:** Signature valid but the signing key has been revoked.
**Treatment:** Neutral by default (revocation may be for reasons unrelated to this file). Shown as informational reason code `C2PA_REVOKED` with explanation: `The signing authority has been revoked since this file was signed. This doesn't necessarily mean the file is fake, but we can't confirm authenticity.`

### 6.11 Storage full
**Trigger:** Can't copy media to scoped storage, or can't install model update.
**Layout:** Body: `Your phone is low on storage. Free up some space and try again.` Primary: `Open storage settings`. Secondary: `Done`.

### 6.12 NPU unavailable (fallback notice)
**Trigger:** Detection runs on GPU or CPU because NPU delegate failed.
**Treatment:** Not a blocking error. Small badge in forensic view showing inference hardware. In settings model management (5.3), explicit surface. **Do not interrupt the user** — just surface.

### 6.13 Device unsupported
**Trigger:** Install-time or first-run check detects unsupported ABI, or Android below minSdk.
**Layout:** Full-screen. Body: `Veritas needs Android 11 or newer with ARM 64-bit support. Your device can't run this version.` Primary: `OK` (closes app).

**Implementation note:** Add this check early on splash (1.1) before proceeding to welcome.

---

## 8. System & background

### 7.1 Foreground-service notification (idle)
**When shown:** Overlay enabled and bubble is visible, but not actively scanning.
**Content:**
- Title: `Veritas bubble active`
- Body: `Tap the bubble to verify what's on screen.`
- Small icon: Veritas mark, monochrome
- Expanded actions: `Hide bubble`, `Open settings`
- Ongoing: yes, cannot be dismissed
- Priority: low (no sound, no heads-up)

### 7.2 Foreground-service notification (active scan)
**When shown:** User tapped bubble, screen-capture consent granted, analysis running.
**Content:**
- Title: `Checking…`
- Body: `Veritas is analyzing the video.`
- Progress bar (indeterminate)
- Ongoing: yes
- Duration: transient (2–6s typically)

### 7.3 Model-update-complete notification
**When shown:** Successful model update downloaded and verified.
**Content:**
- Title: `Detection models updated`
- Body: `Veritas v2.4.1 video detector is now active.`
- Dismissable: yes
- Shown only if update was background — if user triggered manually, no notification, just in-app feedback.

### 7.4 Share-target registration
**Not a UI screen — manifest configuration.** Ensure Veritas appears in the system share sheet for:
- `video/*` (all subtypes)
- `audio/*`
- `image/*`
- `text/plain` (for URL sharing — handled by paste-link path)

Register with Direct Share (Android 11+) so repeat use makes Veritas more prominent in share targets.

---

## 9. Motion & micro-interactions

Global motion principles:
- Duration standard: 200ms for most transitions, 300ms for screen changes.
- Easing: `FastOutSlowIn` for enter, `LinearOutSlowIn` for exit.
- Reduce motion setting honored (Android accessibility).
- No animation longer than 500ms except the initial scan-line loop (1.8s repeating).

Specific animations worth implementing:
- **Scan line:** 1.8s linear loop, top-to-bottom, accent gradient (see mockup CSS).
- **Verdict arrival:** verdict number fades up from opacity 0 over 250ms; confidence bar range slides in from left over 400ms with slight spring.
- **Bubble pulse:** 1.2s pulse loop, opacity 0.4 ↔ 1.0, accent-colored dot.
- **Toast enter:** slide up from bottom 80dp over 200ms with fade, spring curve.
- **Step complete:** small scale bump (1.0 → 1.08 → 1.0) on the dot when a scan step completes.

Accessibility override: all looping animations pause if `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or if the user has "Remove animations" enabled in system accessibility.

---

## 10. Typography & spacing tokens

Reuse exact tokens from mockup CSS, mirrored as Compose tokens:

```kotlin
object VeritasColors {
  val bg = Color(0xFF0A0B0D)
  val panel = Color(0xFF111318)
  val panel2 = Color(0xFF171A21)
  val line = Color(0xFF22262F)
  val line2 = Color(0xFF2C3140)
  val ink = Color(0xFFE9ECF1)
  val inkDim = Color(0xFF9BA3B4)
  val inkMute = Color(0xFF5C6473)
  val ok = Color(0xFF3FD69B)
  val okDim = Color(0xFF1F6B4F)
  val warn = Color(0xFFF5B642)
  val warnDim = Color(0xFF7A5A1D)
  val bad = Color(0xFFFF5A5A)
  val badDim = Color(0xFF7A2828)
  val accent = Color(0xFF8AB4FF)
}

object VeritasType {
  // Display: Manrope weight 300
  // Heading: Manrope weight 600 / 700
  // Body: Manrope weight 400
  // Mono: JetBrains Mono weight 500 / 700
}

object VeritasSpacing {
  val unit = 4.dp
  // 4, 8, 12, 16, 20, 24, 32, 48, 64
}

object VeritasRadius {
  val sm = 6.dp
  val md = 10.dp
  val lg = 14.dp
  val xl = 22.dp
  val pill = 999.dp
}
```

---

## 11. What's NOT specified here

For the agent's awareness, these are decisions left to implementation that are not in the spec:

- Exact Compose component breakdown (up to the developer, but follow clean architecture: pure-Kotlin domain layer, Compose-only UI)
- Exact navigation library (Navigation Compose is default; could use Voyager or Decompose)
- Image loading library (Coil recommended)
- Persistence (Room recommended for history)
- DI (Hilt recommended)

These should be decided in phase 0 of the phase plan and added to the decision log (document 05).

---

## 12. Visual assets the agent will need

Not producible from this spec alone; treat as external dependencies:

- App launcher icon (adaptive icon, monochrome variant)
- Notification small icon (monochrome, 24dp)
- Onboarding illustrations (1.3 icons, 1.4 device/cloud visual, 1.5 share sheet visual, 1.6 bubble preview, 1.7 settings screenshot, 1.8 consent dialog illustration)
- Empty-state illustration (4.2)
- System share-sheet icon (round version of app icon)
- Any Lottie animations (optional, deferred)

**Agent behavior:** stub these with placeholder boxes containing the asset name (e.g., `[asset: onboarding_bubble_preview]`) until real assets are provided. Do not generate AI-made illustrations inline — they will look inconsistent with the instrumentation aesthetic.
