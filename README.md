# ResQMesh AI Platform

> **An open-source Android platform applying on-device machine learning to emergency communication over offline BLE mesh networks for disaster response.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org)
[![Status](https://img.shields.io/badge/Status-Active%20Research-yellow.svg)]()


<img width="216" height="444" alt="Screen_2" src="https://github.com/user-attachments/assets/168ed689-c291-4f0f-8e14-a34ef299dec4" />
<img width="216" height="444" alt="Screen_2" src="https://github.com/user-attachments/assets/f3040038-c0a7-4957-90d9-21021ed32a67" />
<img width="216" height="456" alt="Screen_3" src="https://github.com/user-attachments/assets/3ce837c9-6de1-4c57-8845-7e464a395f98" />


---

## Overview

**ResQMesh AI Platform** is a research-driven Android platform built on top of the [BitChat](https://github.com/permissionlesstech/bitchat-android) open-source BLE mesh messaging protocol. It extends core mesh communication with an **on-device AI layer** specifically designed for disaster response scenarios where internet infrastructure is unavailable or destroyed.

During natural disasters — hurricanes, earthquakes, wildfires — traditional communication infrastructure fails precisely when it is needed most. This platform addresses that critical gap by combining:

- **Decentralized BLE mesh networking** — no internet, no servers, no single point of failure
- **On-device AI inference** — intelligent decisions made locally, without cloud dependency
- **FEMA-compatible reporting** — outputs aligned with U.S. federal emergency management standards

This project directly supports U.S. national priorities in **emergency preparedness**, **disaster resilience**, and **critical communication infrastructure** as identified in the White House 2024 Critical and Emerging Technologies List under *Integrated Communication and Networking Technologies*.

---

## The Problem

In disaster zones, communication breakdown is one of the leading causes of preventable casualties. Existing solutions have critical limitations:

| Problem | Current State | ResQMesh AI Approach |
|---|---|---|
| Message overload | All messages treated equally | AI prioritizes by criticality |
| Injured/disabled users | Text-only input | Offline speech recognition |
| No situational overview | Manual data collection | Auto-generated FEMA reports |
| Battery drain | Always-on radio | AI-driven adaptive energy management |
| No coordinator tools | Raw message feeds | Structured incident summaries |
| Image reporting over BLE | Full image transfer (~500 KB) saturates BLE bandwidth | On-device scene analysis converts photo to compact text report (~100 bytes) |

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              ResQMesh AI Platform                   │
├─────────────────────────────────────────────────────┤
│  [M1] AI Message        │  [M2] Offline Speech      │
│  Priority Classifier    │  Recognition (STT)        │
│  dual-Conv1D INT8       │  Vosk Android             │
│  1,137 KB · F1=0.92     │  10–15% WER · <300ms      │
├─────────────────────────────────────────────────────┤
│  [M3] FEMA ICS-213      │  [M4] AI Energy           │
│  Report Generator       │  Optimizer                │
│  Situation awareness    │  +16–57% node survival    │
├─────────────────────────────────────────────────────┤
│  [M5] Photo Scene Analysis — MobileNetV2 TFLite     │
│  macro-F1=0.98 · 7,869× BW reduction · 24.5–87.9ms  │
│  Photo → on-device classification → text report     │
│  Eliminates image transfer over BLE                 │
├─────────────────────────────────────────────────────┤
│           BitChat BLE Mesh Protocol Layer           │
│     (Bluetooth LE · Noise Protocol · Multi-hop)     │
└─────────────────────────────────────────────────────┘
```

### Gradle Module Structure

The project uses a multi-module Gradle build to cleanly separate AI concerns from the core application:

```
:app                        — Android application (UI, mesh, crypto, routing)
:resqmesh-ai            — All AI/ML functionality (self-contained library)
    ├── ai/classifier/      — Message priority classification
    │     ├── KeywordMessageClassifier   (~90 FEMA/ICS keywords across 9 categories + NONE, always available)
    │     ├── TFLiteMessageClassifier    (neural model, activated by dropping .tflite asset)
    │     ├── CompositeMessageClassifier (keyword-first → TFLite fallback pipeline)
    │     └── MessageClassifierFactory   (selects best available backend at runtime)
    ├── ai/emergency/       — Emergency badge predicates and category label helpers
    │     └── EmergencyClassification    (shouldShowEmergencyBadge, categoryEmojiAndLabel)
    ├── ai/energy/          — Battery-aware relay policy (no Android SDK dependency)
    │     ├── EnergyMode                 (PERFORMANCE / BALANCED / POWER_SAVER / ULTRA_LOW_POWER)
    │     └── EnergyRelayPolicy          (networkFactor × energyMultiplier; critical relay floor ≥0.20)
    ├── ai/report/          — FEMA ICS-213 report data and HTML generator
    │     ├── ICS213ReportData           (structured data classes for the ICS-213 form)
    │     └── ICS213ReportGenerator      (pure HTML renderer, no Android deps, unit-testable)
    ├── ai/voice/           — Offline STT (Vosk), voice recording, waveform tools
    └── ai/vision/          — Photo scene analysis and label-to-category mapping
          └── SceneToEmergencyMapper   (ML Kit label strings → 9 emergency categories + description text)
```

The `:resqmesh-ai` module is an Android library with no dependency on `:app`, keeping AI code independently testable and reusable.

---

## Core Modules

### M1 — AI Message Priority Classifier

Classifies incoming mesh messages into priority tiers and emergency categories using a two-stage on-device pipeline. In a disaster zone, not all messages are equal — a medical emergency must reach rescue coordinators before routine check-in messages.

> **Language support:** The classifier currently supports **English only**. Both the keyword rules and the TFLite model vocabulary are English-based. Multi-language classification is planned for a future release.

**Priority levels:**
- `CRITICAL` — Medical emergencies, SOS signals, structural collapse reports
- `HIGH` — Fire, flood, active security threats
- `NORMAL` — Infrastructure issues, weather hazards, missing persons, resource requests
- `NONE` — Routine check-ins, test messages, non-emergency content

**9 recognised emergency categories + NONE:**

| Category | Examples |
|---|---|
| 🏥 MEDICAL | "need a doctor", "heart attack", "injured", "SOS" |
| 🏚 COLLAPSE | "building collapsed", "trapped under rubble" |
| 🔥 FIRE | "fire spreading", "smoke", "evacuate now" |
| 🌊 FLOOD | "flooding", "water rising", "dam broke" |
| 🚨 SECURITY | "armed", "shooting", "threat", "danger" |
| ⛈ WEATHER | "tornado", "hurricane", "storm warning" |
| 🔍 MISSING PERSON | "can't find my kid", "missing person", "where is my" |
| 🔧 INFRASTRUCTURE | "power outage", "no water", "bridge down" |
| 📦 RESOURCES | "need food", "no supplies", "starving" |

**Two-stage classifier pipeline (`CompositeMessageClassifier`):**
1. **`KeywordMessageClassifier`** — ~90 FEMA/ICS keyword rules run first. If a `CRITICAL` or `HIGH` priority keyword matches, the result is returned immediately with deterministic, high-confidence output — bypassing the confidence threshold entirely.
2. **`TFLiteMessageClassifier`** — Lightweight **dual-Conv1D** neural model (following Kim 2014 CNN architecture) runs as fallback for messages that don't match keywords. Confidence threshold: 25%.

`MessageClassifierFactory` selects `CompositeMessageClassifier` (keyword + TFLite) when a model asset is present, or `KeywordMessageClassifier` alone otherwise, ensuring the system degrades gracefully on constrained hardware.

**Model specifications:**
- Architecture: parallel Conv1D branches (kernel sizes 3 and 5) with additive fusion + sequential Conv1D layer + GlobalMaxPooling + Dense
- Quantization: INT8 via TFLite concrete function tracing
- Model size: **1,137 KB**
- Vocabulary: 5,000-token cap, **2,979 active tokens**
- Training corpus: **2,700 messages** (270 per category × 10 categories), FEMA/ICS-derived, iteratively cleaned to remove duplicates and cross-category leakage
- Train/val/test split: 70/15/15 stratified, random_state=42
- Optimizer: Adam (lr=5×10⁻⁴), 80 epochs, early stopping patience=15

**Evaluation results (held-out test set, N=405):**

| Category | Precision | Recall | F1 |
|---|---|---|---|
| COLLAPSE | 0.93 | 0.93 | 0.93 |
| FIRE | 0.91 | 0.98 | 0.94 |
| FLOOD | 0.86 | 0.90 | 0.88 |
| INFRASTRUCTURE | 0.90 | 0.85 | 0.88 |
| MEDICAL | 0.88 | 0.88 | 0.88 |
| MISSING PERSON | 0.91 | 0.98 | 0.94 |
| RESOURCE REQ. | 0.97 | 0.93 | 0.95 |
| SECURITY | 0.97 | 0.90 | 0.94 |
| WEATHER | 0.91 | 0.95 | 0.93 |
| **Macro avg** | **0.92** | **0.92** | **0.92** |

**Ablation study:**

| Configuration | Macro-F1 |
|---|---|
| Keyword only | 0.60 |
| Neural only (no keyword stage) | 0.916 |
| **Composite (ours)** | **0.920** |

The keyword stage contributes a 0.004 F1 gain over neural-only, but provides two architectural benefits: (1) deterministic CRITICAL classification without a confidence threshold; (2) graceful degradation to keyword-only mode (F1=0.60) when the TFLite asset is unavailable.

**Out-of-domain generalization:**
Zero-shot evaluation on 120 crisis-style messages (authored to mimic crisis Twitter register per CrisisLex26 taxonomy, unseen during training): **macro-F1=0.87** (mean confidence 0.92) — a 0.05 domain gap versus the held-out test set.

**Inference latency (CPU-only TFLite Interpreter, 200 runs after 10 warm-up discards):**

| Device | SoC | Android | Mean latency | P95 latency | P99 latency | Max latency |
| --- | --- | --- | --- | --- | --- | --- |
| Pixel 9 Pro | Tensor G4 | 16 | 0.19 ms | 0.20 ms | 0.20 ms | 0.26 ms |
| Samsung Galaxy S10 | Snapdragon 855 | 12 | 0.38 ms | 0.40 ms | 0.46 ms | 0.46 ms |
| Pixel 3a | Snapdragon 670 | 11 | 1.97 ms | 2.12 ms | 2.17 ms | 2.19 ms |

All devices achieve sub-2 ms inference — negligible relative to BLE transmission delays across the Android hardware spectrum from 2019 mid-range to 2024 flagship.

**Real-time visual indicators in the chat UI:**
- Each classified message shows a **coloured left stripe** matching its category (e.g., red for MEDICAL, orange for FIRE, blue for FLOOD).
- An **emoji badge** below the message text confirms the detected category and confidence score (e.g., `🏥 MEDICAL · 94%`).
- Classification runs in the background on `Dispatchers.Default` so TFLite never blocks the UI thread.
- Results are cached per message ID — each message is classified exactly once.

**Emergency Feed:**
- A persistent **🚨 button** in the input bar opens the Emergency Feed.
- The **Emergency Feed sheet** groups all detected emergency messages by category, sorted by priority (CRITICAL → HIGH → NORMAL), with a coloured stripe and message count per category.
- Tapping a category opens a **full-screen Category View** showing only messages of that type, with system back-button navigation support.

**Why it matters:** Rescue coordinators receiving hundreds of messages simultaneously cannot manually triage. AI prioritization directly reduces response time for life-threatening situations.

---

### M2 — Offline Speech Recognition (STT)

Enables voice-to-text message input without internet connectivity, using on-device speech recognition.

**Implementation:** [Vosk Android](https://alphacephei.com/vosk/android) — lightweight (~50 MB), supports Android 5.0+, 20+ languages, works fully offline on any device.

**Performance:** 10–15% WER on standard benchmarks, streaming latency under 300 ms.

**Use cases:**
- Users with hand injuries cannot type
- Dark environments where screen visibility is limited
- Panic situations where voice is faster than typing
- Accessibility for users with disabilities

**Why it matters:** In disaster scenarios, physical and cognitive stress significantly impairs fine motor skills. Voice input removes a critical barrier to communication when it matters most.

---

### BLE Priority Queue — Radio-Level Emergency Preemption

Ensures that life-critical messages are transmitted before routine traffic even at the radio layer, eliminating head-of-line blocking caused by large queues of low-priority packets.

**Implementation:**
- `BluetoothPacketBroadcaster` replaced with a `java.util.PriorityQueue` + `Mutex` + `CONFLATED` signal actor
- Every outgoing packet carries a `priority: Int` field on `RoutedPacket` (default 2 = NORMAL); set synchronously by `KeywordMessageClassifier` inside `sendMessage()` before the packet is enqueued
- CRITICAL packets are dequeued ahead of all NORMAL and NONE packets, regardless of arrival order

**Benchmark results (queue-level simulation, not over-the-air BLE):**

| Device | PQ latency | FIFO latency | Improvement |
|---|---|---|---|
| Pixel 9 Pro | 104 µs | 101 ms | **975×** |
| Galaxy S10+ | 102 µs | 101 ms | **983×** |
| Pixel 3a | 102 µs | 100 ms | **981×** |

*N=1,000 packets, 100 µs/packet inter-arrival, 10 runs median. Measurement characterizes the queue scheduling layer in isolation; over-the-air BLE latency includes additional PHY-layer delays not modeled here. Analytical bound: 1,000×.*

**Why it matters:** A CRITICAL SOS message queued behind a routine status update could arrive minutes later in a high-traffic mesh. Radio-level preemption closes that gap to near-zero.

---

### Emergency Location Attachment

Allows users to voluntarily attach their GPS location or a typed address to CRITICAL and HIGH priority messages, enabling coordinators to pinpoint distress signals on a map.

**Implementation:**
- `LocationAttachSheet` — bottom sheet triggered automatically when the classifier detects a CRITICAL or HIGH message
- Offers GPS coordinates (via Android `FusedLocationProviderClient`) or free-text address entry
- Location appended to the message body as `📍 lat,lon`; never pre-filled to preserve user privacy
- ICS-213 report extracts and preserves location data per incident row

---

### M3 — FEMA ICS-213 Situation Report Generator

Automatically aggregates prioritized messages from across the mesh network and generates structured situation reports compatible with the **FEMA Incident Command System (ICS-213)** General Message standard.

**Implementation:**
- `ICS213ReportData` — structured data classes capturing incident metadata (sender, timestamp, category, priority, location, message body)
- `ICS213ReportGenerator` — pure Kotlin HTML renderer; no Android dependencies; unit-testable in `:resqmesh-ai`
- `ICS213ReportScreen` — full-screen Compose preview rendering the form as a `LazyColumn`
- `ICS213PrintHelper` — loads the generated HTML into a headless `WebView` and triggers the Android `PrintManager`

**Output includes:**
- Incident log grouped by emergency category and priority
- Per-incident: sender, timestamp, full message body, and GPS coordinates (when attached)
- Shareable HTML document compatible with any browser for printing

**Why it matters:** Emergency coordinators need structured, actionable information — not raw message feeds. ICS-213 compatibility ensures reports integrate directly into existing federal and state emergency management workflows.

---

### M4 — AI Energy Optimizer

Intelligently manages radio relay decisions based on battery state to maximize mesh network lifetime under power constraints.

**Implementation:**
- `EnergyMode` enum: PERFORMANCE / BALANCED / POWER_SAVER / ULTRA_LOW_POWER
- `EnergyRelayPolicy` — relay probability = `networkFactor(N)` × `energyMultiplier(EnergyMode)`
- CRITICAL floor: **≥0.20 relay probability at ULTRA_LOW_POWER** — ensures last-resort forwarding even on nearly-dead devices

**Analytical model:**
Battery depletion modeled as exponential decay with λ=0.1 h⁻¹ (derived from BLE 4.0 power characterization, treated as conservative worst-case; modern BLE 5.x devices expected to show lower drain rates).

**Projected node survival improvement at t=6 h (Monte Carlo validated, 5,000 runs, <0.3 pp error):**

| Mode | Improvement vs blind relay |
|---|---|
| BALANCED (p=0.75) | +16% |
| POWER_SAVER (p=0.50) | +35% |
| ULTRA_LOW_POWER (p=0.25) | **+57%** |

**λ sensitivity:** at λ=0.05 (20 h battery) BALANCED reaches +33%; at λ=0.20 (5 h battery), +7% — positive across the full realistic range.

**Phase 2 planned:**
- Adaptive BLE/Wi-Fi Direct switching: Wi-Fi Direct activates in PERFORMANCE/BALANCED mode for large payloads (~20% higher power cost), BLE as default
- Protocol-level battery metadata broadcasting across mesh nodes

**Why it matters:** In prolonged disaster scenarios (multi-day events), extending network lifetime by 16–57% can be the difference between maintaining communication during critical rescue windows.

---

### M5 — Photo Scene Analysis (Image-to-Text for BLE Efficiency)

Converts a photo taken at an incident scene into a compact text emergency report using on-device image classification, eliminating the need to transmit raw image data over the BLE mesh.

**The bandwidth problem:** BLE mesh throughput peaks at ~20–50 KB/s per hop. A single smartphone photo (300–900 KB) occupies the shared radio channel for 6–45 seconds per hop, blocking all concurrent delivery.

**The solution:** Classify the image locally, discard it, send only the resulting text description — typically 47–70 bytes. This achieves a mean **7,869× bandwidth reduction** relative to raw JPEG.

**Implementation:**
- `VisionTFLiteClassifier` — custom **MobileNetV2** model trained via transfer learning from ImageNet weights on a curated emergency scene dataset; 5 output categories: **FIRE, FLOOD, WEATHER, SECURITY, NORMAL**; input: 224×224 px normalized to [−1, 1]; confidence threshold: **0.65**
- `ImageSceneAnalyzer` — suspending wrapper; uses `VisionTFLiteClassifier` as primary; falls back to **ML Kit Image Labeling** when TFLite confidence is below threshold
- `SceneToEmergencyMapper` — pure-Kotlin mapper: translates ML Kit label strings into one of the 9 app emergency categories; no Android or ML Kit dependencies

**Evaluation results (disjoint 50-photo test set, preventing data leakage):**

| Category | F1 |
|---|---|
| FIRE | 1.00 |
| FLOOD | 0.95 |
| WEATHER | 0.95 |
| SECURITY | 1.00 |
| NORMAL | 1.00 |
| **Macro-F1** | **0.98** |

Bootstrap 95% CI (1,000 resamples, n=50): **[0.93, 1.00]**

**Full-pipeline latency (physical devices):**
- Pixel 9 Pro: 24.5 ms
- Galaxy S10+: 50.1 ms
- Pixel 3a: 87.9 ms

**Three outcome paths:**

| TFLite / ML Kit result | Sent to mesh |
|---|---|
| TFLite detects emergency category ≥ 65% confidence (FIRE, FLOOD, WEATHER, SECURITY) | Text only: `[📷] 🔥 FIRE: Burning building, smoke. Requires immediate response.` |
| Labels returned, no matching category | Text only: `[📷] Scene: Building, outdoor, road.` — user can edit before sending |
| No labels / ML Kit failure | Image sent as attachment (original behavior, graceful fallback) |

**Why it matters:** BLE mesh bandwidth is a shared, scarce resource. A mean 7,869× reduction enables practical photo-based reporting in multi-hop mesh scenarios where raw image transfer would be infeasible.

---

## Roadmap

**Phase 1 — Core AI Platform (current, 0–3 months):**
- ✅ Project forked from BitChat Android (GPL-3.0)
- ✅ `:resqmesh-ai` Gradle module — dedicated AI library module, independent of `:app`
- ✅ M1: Keyword classifier — ~90 FEMA/ICS keyword rules across 9 emergency categories + NONE
- ✅ M1: TFLite dual-Conv1D classifier — 1,137 KB INT8, macro-F1=0.92, trained on 2,700 messages
- ✅ M1: `CompositeMessageClassifier` — keyword-first, TFLite fallback two-stage pipeline
- ✅ M1: Real-time visual indicators — category-coloured left stripes and emoji badges on every classified message
- ✅ M1: Emergency Feed — always-visible feed button opens a priority-sorted category sheet
- ✅ M2: Offline Speech Recognition — Vosk Android, 10–15% WER, <300ms latency, 20+ languages
- ✅ BLE Priority Queue — CRITICAL packets preempt NORMAL/NONE at the radio layer; 975–983× faster delivery (queue-level simulation on 3 devices)
- ✅ Emergency Location Attachment — optional GPS / manual address appended to CRITICAL and HIGH messages
- ✅ M3: FEMA ICS-213 Report Generator — Compose-rendered report with share/print support
- ✅ M4: AI Energy Optimizer — `EnergyRelayPolicy` projects +16–57% node survival (Monte Carlo, 5,000 runs); CRITICAL floor ≥0.20 at ULTRA_LOW_POWER
- ✅ M5: Photo Scene Analysis — MobileNetV2, macro-F1=0.98, 7,869× bandwidth reduction, 24.5–87.9ms latency

**Phase 2 (6–12 months):**
- Adaptive BLE/Wi-Fi Direct transport switching based on battery state and payload size
- Spatial pattern detection — geographic clustering of incident reports with visual heatmap
- Real-time situational awareness map for coordinator dashboard
- Protocol-level battery metadata broadcasting across mesh nodes

**Phase 3 (12–24 months):**
- Federated learning — distributed AI model improvement across mesh nodes without sharing raw data
- LLM-based natural language situation report generation (on-device, quantized models)
- Multi-language support for diverse disaster-affected populations
- Integration with FEMA IPAWS (Integrated Public Alert and Warning System)

---

## National Importance & Research Context

This platform addresses priorities identified by multiple U.S. federal initiatives:

- **White House 2024 Critical and Emerging Technologies List** — *Integrated Communication and Networking Technologies* explicitly includes mesh networks and infrastructure-independent communication technologies
- **NSF RINGS Program** — $37M investment in resilient next-generation networking systems
- **FEMA Strategic Plan 2022–2026** — priority on technology-driven disaster resilience
- **FCC Disaster Information Reporting System** — need for resilient communication during infrastructure failures

---

## Technical Stack

| Component | Technology | Module |
|---|---|---|
| Language | Kotlin | — |
| UI Framework | Jetpack Compose + Material Design 3 | `:app` |
| AI/ML Runtime | TensorFlow Lite 2.14 | `:resqmesh-ai` |
| M1 Classifier | dual-Conv1D INT8 (1,137 KB, macro-F1=0.92) + keyword rules | `:resqmesh-ai` |
| Speech Recognition | Vosk Android 0.3.47 (offline, 20+ languages) | `:resqmesh-ai` |
| Image Scene Analysis | MobileNetV2 TFLite (macro-F1=0.98, 7,869× BW reduction) + ML Kit fallback | `:app` + `:resqmesh-ai` |
| Mesh Transport | Bluetooth Low Energy (BLE), selected over LoRa (no extra hardware) and Wi-Fi Direct (~20% lower power) | `:app` |
| Encryption | Noise Protocol Framework (mutual auth, forward secrecy, replay protection) | `:app` |
| Mesh Routing | Multi-hop flood routing with Bloom Filter deduplication | `:app` |
| Minimum Android | 8.0 (API 26) | — |

---

## Getting Started

```bash
# Clone the repository
git clone https://github.com/AleksPlekhov/ai-mesh-emergency-communication-platform.git

# Open in Android Studio
# File → Open → select project directory

# Build
./gradlew build

# Install on device
./gradlew installDebug
```

**Requirements:**
- Android Studio Hedgehog or later
- Android device or emulator running API 26+
- Bluetooth LE capable hardware (for mesh testing)
- Minimum 2 devices recommended for mesh testing

---

## Based On

This platform extends **BitChat Android** ([permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android), commit `main` branch, accessed Jan. 2025), an open-source BLE mesh messaging application released under GPL-3.0. The core BLE mesh transport, Noise Protocol encryption, and multi-hop routing are derived from BitChat. The AI inference layer, speech recognition module, FEMA reporting system, and energy optimization components are original contributions of this project.

In accordance with GPL-3.0, this project is released under the same license and all source code is publicly available.

---

## Research & Publications

- **[Published — December 18, 2024]**
  "Strategies for Minimizing Power Consumption and Optimizing Resources for Android-Based Mesh Networks" — *In Plain English*.
  URL: https://plainenglish.io/technology/strategies-for-minimizing-power-consumption-and-optimizing-resources-for-android-based-mesh-networks

- **[Published — January 8, 2025]**
  "Optimizing Android Mesh Networks for Mobility: Enhancing Connectivity in Moving Peer-to-Peer Systems" — *Medium / Android Development*.
  URL: https://medium.com/android-networking/optimizing-android-mesh-networks-for-mobility-enhancing-connectivity-in-moving-peer-to-peer-14ec750ae33a

- **[Published — March 30, 2025]**
  "Disaster-Proof Mobile Networks: How Android-Powered Mesh Tech Can Save Lives" — *Medium*.
  URL: https://medium.com/@aleks.plekhov/disaster-proof-mobile-networks-how-android-powered-mesh-tech-can-save-lives-75fac09224ba

- **[Published — October 2025]**
  "Wi-Fi Direct in Android: Creating Seamless Device-to-Device Communication" — *Sustainable Engineering and Innovation*, Vol. 7, No. 2, pp. 477–492. Co-authored with Kateryna Babii. DOI: 10.37868/sei.v7i2.id539.

- **[Preprint — March 31, 2026]**
  "AI-Driven Message Prioritization for Offline BLE Mesh Networks in Disaster Response Scenarios" — SSRN, DOI: 10.2139/ssrn.6428398. *(Covers M1 classifier only; substantially extended in the FMEC 2026 submission below.)*

- **[Published — March 25, 2026]**
  "[When the Internet Dies, Your Phone Can Still Be Smart: Building AI-Powered Disaster Communication](https://hackernoon.com/when-the-internet-dies-your-phone-can-still-be-smart-building-ai-powered-disaster-communication)" — HackerNoon.

- **[Submitted — April 2026]**
  "ResQMesh AI: On-Device Machine Learning for Bandwidth-Efficient and Energy-Aware Emergency Communication over Offline BLE Mesh Networks" — *11th International Conference on Fog and Mobile Edge Computing (FMEC 2026)*, Abu Dhabi, UAE. IEEE. Paper ID: 2661312400.

---

## Contributing

Contributions are welcome. This is an active research project and we particularly value:

- Testing on diverse Android hardware
- Disaster scenario simulation datasets
- ML model improvements for message classification
- Multilingual speech recognition integration

Please open an issue before submitting a pull request for significant changes.

---

## Author

**Oleksandr Pliekhov**
Android & Mesh Systems Researcher | Lead Developer
Charlotte, North Carolina, USA

*Research focus: AI-enhanced offline communication systems for emergency response*

---

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE) for details.

In accordance with GPL-3.0 requirements, this project is derived from [BitChat Android](https://github.com/permissionlesstech/bitchat-android) and maintains the same open-source license. All modifications and additions are documented in the commit history.

---

## Acknowledgments

- [permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android) — foundational BLE mesh protocol implementation
- [Vosk Speech Recognition](https://alphacephei.com/vosk/) — offline STT engine
- [TensorFlow Lite](https://www.tensorflow.org/lite) — on-device ML inference
- [Google ML Kit Image Labeling](https://developers.google.com/ml-kit/vision/image-labeling) — fallback classifier for M5

---

*ResQMesh AI Platform is an independent research project. It is not affiliated with FEMA, NSF, or any U.S. government agency.*