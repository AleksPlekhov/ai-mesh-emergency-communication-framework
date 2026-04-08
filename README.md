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
│              ResQMesh AI Platform                │
├─────────────────────────────────────────────────────┤
│  [M1] AI Message        │  [M2] Offline Speech      │
│  Priority Classifier    │  Recognition (STT)        │
│  TFLite on-device      │  Vosk / Whisper Android   │
├─────────────────────────────────────────────────────┤
│  [M3] FEMA ICS-213      │  [M4] AI Energy           │
│  Report Generator       │  Optimizer                │
│  Situation awareness   │  BLE/WiFi adaptive mgmt   │
├─────────────────────────────────────────────────────┤
│  [M5] Photo Scene Analysis — Custom TFLite Vision Model     │
│  MobileNetV2 · 5 categories · 100% accuracy on test sets   │
│  Photo → on-device classification → text report             │
│  Eliminates image transfer over BLE (~500× smaller)         │
├─────────────────────────────────────────────────────┤
│           BitChat BLE Mesh Protocol Layer            │
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
    │     └── EnergyRelayPolicy          (networkFactor × energyMultiplier; critical relay floor)
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
1. **`KeywordMessageClassifier`** — ~90 FEMA/ICS keyword rules run first. If a `CRITICAL` or `HIGH` priority keyword matches, the result is returned immediately with deterministic, high-confidence output.
2. **`TFLiteMessageClassifier`** — Lightweight neural model (MobileBERT-style) runs as fallback for messages that don't match keywords. Activated automatically when `emergency_model.tflite` is present in module assets — included by default. Confidence threshold: 25%.

`MessageClassifierFactory` selects `CompositeMessageClassifier` (keyword + TFLite) when a model asset is present, or `KeywordMessageClassifier` alone otherwise, ensuring the system degrades gracefully on constrained hardware.

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

**Implementation:** [Vosk Android](https://alphacephei.com/vosk/android) — lightweight (~50MB), supports Android 5.0+, works on any device.

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
- **Benchmark result:** 1001× faster delivery for CRITICAL messages vs FIFO with 1,000 queued packets at 100 µs/packet (`PriorityQueueBenchmarkTest`)

**Why it matters:** A CRITICAL SOS message queued behind a routine status update could arrive minutes later in a high-traffic mesh. Radio-level preemption closes that gap to near-zero.

---

### Emergency Location Attachment

Allows users to voluntarily attach their GPS location or a typed address to CRITICAL and HIGH priority messages, enabling coordinators to pinpoint distress signals on a map.

**Implementation:**
- `LocationAttachSheet` — bottom sheet triggered automatically when the classifier detects a CRITICAL or HIGH message
- Offers GPS coordinates (via Android `FusedLocationProviderClient`) or free-text address entry
- Location appended to the message body as `📍 lat,lon`; never pre-filled to preserve user privacy
- ICS-213 report extracts and preserves location data per incident row

**Why it matters:** Knowing *where* an emergency is occurring is as critical as knowing *what* the emergency is. Voluntary location sharing eliminates the need for rescuers to ask follow-up questions under time pressure.

---

### M3 — FEMA ICS-213 Situation Report Generator

Automatically aggregates prioritized messages from across the mesh network and generates structured situation reports compatible with the **FEMA Incident Command System (ICS-213)** General Message standard.

**Implementation:**
- `ICS213ReportData` — structured data classes capturing incident metadata (sender, timestamp, category, priority, location, message body)
- `ICS213ReportGenerator` — pure Kotlin HTML renderer; no Android dependencies; unit-testable in `:resqmesh-ai`
- `ICS213ReportScreen` — full-screen Compose preview rendering the form as a `LazyColumn` (white background, monospace font, priority badges, signature blocks); no WebView dependency, avoiding the Android 11 Trichrome crash
- `ICS213PrintHelper` — loads the generated HTML into a headless `WebView` and triggers the Android `PrintManager` (Save as PDF or physical printer); gracefully falls back to sharing the HTML via `FileProvider` if WebView is unavailable
- "GENERATE ICS-213 REPORT" button pinned at the bottom of the Emergency Feed sheet; hidden when the feed is empty

**Output includes:**
- Incident log grouped by emergency category and priority
- Per-incident: sender, timestamp, full message body, and GPS coordinates (when attached)
- Shareable HTML document compatible with any browser for printing

**Why it matters:** Emergency coordinators need structured, actionable information — not raw message feeds. ICS-213 compatibility ensures reports integrate directly into existing federal and state emergency management workflows.

---

### M4 — AI Energy Optimizer

Intelligently manages radio relay decisions based on battery state to maximize mesh network lifetime under power constraints. The first iteration is complete and active; advanced Wi-Fi Direct switching is planned for Phase 2.

**First iteration — battery-aware relay policy (complete):**
- `EnergyMode` enum (PERFORMANCE / BALANCED / POWER_SAVER / ULTRA_LOW_POWER) mirrors `PowerManager.PowerMode` without creating a cross-module Android dependency
- `EnergyRelayPolicy` — pure Kotlin policy engine: relay probability = `networkFactor(networkSize)` × `energyMultiplier(EnergyMode)`; computed independently for each queued packet
- CRITICAL/SOS packets always relay regardless of battery mode — minimum 0.20 relay probability at ULTRA_LOW_POWER, ensuring last-resort forwarding survives even nearly-dead devices
- `PacketRelayManager` delegates relay decisions entirely to `EnergyRelayPolicy`; energy mode is updated live via `BluetoothConnectionManager.onEnergyModeChanged` callback wired in `BluetoothMeshService`
- Power mode change notifications shown as toasts

**Research foundation — adaptive transport selection:**
- The power optimization strategies documented in Pliekhov (2024) — including energy-aware relay probability, adaptive transmission rates, and duty cycling — form the conceptual foundation for ResQMesh AI's EnergyRelayPolicy, which implements these principles at the radio layer. The hybrid BLE/Wi-Fi Direct approach proposed in that work is planned for Phase 2, where transport selection will be governed by battery state and payload size thresholds.
- The energy-bandwidth tradeoff governing BLE vs. Wi-Fi Direct selection in Phase 2 is grounded in empirical measurements from peer-reviewed research by the project author. Pliekhov & Babii (2025) demonstrated that Wi-Fi Direct outperforms BLE in throughput and range but incurs approximately 20% higher power consumption on Android devices (tested on Pixel 4 and Samsung Galaxy S10). ResQMesh AI's EnergyRelayPolicy will use these benchmarks as the basis for transport switching thresholds — defaulting to BLE under POWER_SAVER and ULTRA_LOW_POWER modes, and enabling Wi-Fi Direct only when battery state is PERFORMANCE or BALANCED and payload size exceeds the BLE MTU threshold.

**Planned (Phase 2):**
- Adaptive BLE scan interval based on network traffic patterns
- Dynamic switching between BLE and Wi-Fi Direct based on bandwidth requirements and battery state — building on empirical performance benchmarks established in Pliekhov & Babii (2025), which demonstrated Wi-Fi Direct's throughput advantage for large payloads at the cost of ~20% higher power consumption compared to BLE
- Protocol-level battery metadata broadcasting so coordinators see node health across the mesh

**Why it matters:** In prolonged disaster scenarios (multi-day events), battery life is a critical constraint. Extending network lifetime by even 20-30% can be the difference between maintaining communication during critical rescue windows.

---

### M5 — Photo Scene Analysis (Image-to-Text for BLE Efficiency)

Converts a photo taken at an incident scene into a compact text emergency report using on-device image classification, eliminating the need to transmit raw image data over the BLE mesh.

**The bandwidth problem:** A typical smartphone photo, even after compression to 512 px, is ~80–150 KB. BLE throughput in a mesh scenario peaks at roughly 20–50 KB/s per hop. A single image can occupy the radio for several seconds, blocking other messages and exhausting battery faster. In a multi-hop mesh under load, image transfer is effectively impractical.

**The solution:** Classify the image locally, discard it, send only the resulting text description — typically 80–120 bytes. This is approximately **500–1000× smaller** than the raw image.

**Implementation:**
- `VisionTFLiteClassifier` (`:resqmesh-ai`, `ai/vision/`) — custom **MobileNetV2** model trained via transfer learning on a curated emergency scene dataset; 5 output categories: **fire, flood, weather, security, normal**; achieves **100% accuracy on held-out test sets** across all categories; input: 224×224 px bitmap normalized to [−1, 1]; inference: ~200–400 ms on modern hardware; fully offline, no Play Services required
- `ImageSceneAnalyzer` (`:app`, `features/media/`) — suspending wrapper; uses `VisionTFLiteClassifier` as primary classifier (confidence threshold: 0.55); falls back to **ML Kit Image Labeling** when TFLite confidence is below threshold; reuses `ImageUtils.loadBitmapWithExifOrientation` for EXIF-corrected input
- `SceneToEmergencyMapper` (`:resqmesh-ai`, `ai/vision/`) — pure-Kotlin mapper: translates ML Kit label strings into one of the 9 app emergency categories and generates a pre-formatted message description; no Android or ML Kit dependencies — independently unit-testable
- `PhotoReportButton` (`:app`, `ui/media/`) — orange `AddAPhoto` icon in the chat input row; visually distinct from the standard image send button; single-click opens gallery, long-click opens camera; shows a `CircularProgressIndicator` during analysis (~200–400 ms on modern hardware)

**Three outcome paths:**
| TFLite / ML Kit result | Sent to mesh |
|---|---|
| TFLite detects emergency category ≥ 55% confidence (FIRE, FLOOD, WEATHER, SECURITY) | Text only: `[📷] 🔥 FIRE: Burning building, smoke. Requires immediate response.` |
| Labels returned, no matching category | Text only: `[📷] Scene: Building, outdoor, road.` — user can edit before sending |
| No labels / ML Kit failure | Image sent as attachment (original behavior, graceful fallback) |

**The `[📷]` prefix** on all generated messages signals to recipients that the report was auto-generated from a photo rather than typed — important for coordinators assessing information reliability.

**Text message flow:**
1. User taps the orange camera button and takes a photo (or picks from gallery)
2. ML Kit analyzes the image on-device (~200–400 ms)
3. The generated description pre-fills the message TextField
4. User reviews, optionally edits, and taps Send
5. A standard text message (~100 bytes) travels the mesh — not an image

**Why it matters:** BLE mesh bandwidth is a shared, scarce resource. Every kilobyte saved extends the effective range of the network and reduces latency for all other messages. In a dense disaster scenario where dozens of nodes are transmitting simultaneously, image-free photo reporting is not a convenience feature — it is a prerequisite for network viability.

---

## Roadmap

**Phase 1 — Core AI Platform (current, 0–3 months):**
- ✅ Project forked from BitChat Android (GPL-3.0)
- ✅ `:resqmesh-ai` Gradle module — dedicated AI library module, independent of `:app`
- ✅ M1: Keyword classifier — ~90 FEMA/ICS keyword rules across 9 emergency categories + NONE
- ✅ M1: TFLite classifier — `emergency_model.tflite` included in assets; CompositeClassifier active out of the box
- ✅ M1: `CompositeMessageClassifier` — keyword-first, TFLite fallback two-stage pipeline
- ✅ M1: Real-time visual indicators — category-coloured left stripes and emoji badges on every classified message; theme-aware (Material3 light/dark)
- ✅ M1: Emergency Feed — always-visible feed button opens a priority-sorted category sheet; category detail view shows filtered messages with system back navigation
- ✅ M2: Offline Speech Recognition — voice-to-text input via Vosk Android (no internet required)
- ✅ BLE Priority Queue — CRITICAL packets preempt NORMAL/NONE at the radio layer; 1001× faster delivery proven by benchmark
- ✅ Emergency Location Attachment — optional GPS / manual address appended to CRITICAL and HIGH messages via `LocationAttachSheet`
- ✅ M3: FEMA ICS-213 Report Generator — Compose-rendered report with share/print support; pure-Kotlin HTML generator in `:resqmesh-ai`
- ✅ M4: AI Energy Optimizer (first iteration) — `EnergyRelayPolicy` adapts relay probability to battery state; CRITICAL packets always relay even at ULTRA_LOW_POWER
- ✅ M5: Photo Scene Analysis — on-device image classification converts photos to compact text emergency reports; ~500–1000× bandwidth reduction vs. raw image transfer over BLE
- ✅ M5: Custom TFLite vision model — MobileNetV2 transfer learning trained on curated emergency scene dataset; 5 categories (fire / flood / weather / security / normal); **100% accuracy** on held-out test sets across all categories; replaces generic ML Kit labeling with domain-specific emergency scene classification



**Phase 2 (6–12 months):**
- Spatial pattern detection — geographic clustering of incident reports with visual heatmap
- Real-time situational awareness map for coordinator dashboard
- Protocol-level battery metadata broadcasting across mesh nodes

**Phase 3 (12-24 months):**
- Federated learning — distributed AI model improvement across mesh nodes without sharing raw data
- LLM-based natural language situation report generation (on-device, quantized models)
- Multi-language support for diverse disaster-affected populations
- Integration with FEMA IPAWS (Integrated Public Alert and Warning System)

---

## National Importance & Research Context

This platform addresses priorities identified by multiple U.S. federal initiatives:

- **White House 2024 Critical and Emerging Technologies List** — *Integrated Communication and Networking Technologies* explicitly includes mesh networks and infrastructure-independent communication technologies
- **NSF RINGS Program** — $37M investment in resilient next-generation networking systems
- **FEMA Strategic Plan 2022-2026** — priority on technology-driven disaster resilience
- **FCC Disaster Information Reporting System** — need for resilient communication during infrastructure failures

---

## Technical Stack

| Component | Technology | Module |
|---|---|---|
| Language | Kotlin | — |
| UI Framework | Jetpack Compose + Material Design 3 | `:app` |
| AI/ML Runtime | TensorFlow Lite 2.14 | `:resqmesh-ai` |
| Speech Recognition | Vosk Android 0.3.47 (offline) | `:resqmesh-ai` |
| Message Classifier | Composite pipeline: keyword rules + TFLite model | `:resqmesh-ai` |
| Image Scene Analysis | Custom MobileNetV2 TFLite vision model (fire / flood / weather / security / normal · 100% accuracy on held-out test sets) + ML Kit as fallback · fully offline | `:app` + `:resqmesh-ai` |
| Mesh Transport | Bluetooth Low Energy (BLE) | `:app` |
| Encryption | Noise Protocol Framework | `:app` |
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

This platform extends **BitChat Android** ([permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android)), an open-source BLE mesh messaging application released under GPL-3.0. The core BLE mesh transport, Noise Protocol encryption, and multi-hop routing are derived from BitChat. The AI inference layer, speech recognition module, FEMA reporting system, and energy optimization components are original contributions of this project.

In accordance with GPL-3.0, this project is released under the same license and all source code is publicly available.

---

## Research & Publications
 - **[Published — December 18, 2024]**
 "Strategies for Minimizing Power Consumption and Optimizing Resources for Android-Based Mesh Networks" — *In Plain English*.
 *Documents energy optimization strategies for Android mesh networks, including energy-aware relay routing and adaptive duty cycling — concepts subsequently implemented in ResQMesh AI's EnergyRelayPolicy. Hybrid BLE/Wi-Fi Direct transport selection proposed in this work is planned for Phase 2.* URL: https://plainenglish.io/technology/strategies-for-minimizing-power-consumption-and-optimizing-resources-for-android-based-mesh-networks
 - **[Published — January 8, 2025]**
  "Optimizing Android Mesh Networks for Mobility: Enhancing
  Connectivity in Moving Peer-to-Peer Systems" — *Medium / Android Development*.
  *Proposes adaptive peer discovery, cross-protocol BLE/Wi-Fi Direct integration, and energy-aware scanning — techniques grounded in CSRMesh production experience at EZLO Innovation and planned for ResQMesh AI Phase 2. Reports 35% energy reduction and 40% connection stability improvement.* URL: https://medium.com/android-networking/optimizing-android-mesh-networks-for-mobility-enhancing-connectivity-in-moving-peer-to-peer-14ec750ae33a
 - **[Published — March 30, 2025]**
  "Disaster-Proof Mobile Networks: How Android-Powered Mesh Tech Can Save Lives" — *Medium*.
  *Establishes the disaster response use case and national importance framework for ResQMesh AI — citing FCC, FEMA, and IFRC data on communication infrastructure failure during major disasters. The AI-driven routing optimization proposed in this work is implemented in ResQMesh AI's composite classifier and BLE Priority Queue.* URL: https://medium.com/@aleks.plekhov/disaster-proof-mobile-networks-how-android-powered-mesh-tech-can-save-lives-75fac09224ba
 - **[Published — October 2025]**
  "Wi-Fi Direct in Android: Creating Seamless Device-to-Device Communication" — *Sustainable Engineering and Innovation*, Vol. 7, No. 2, pp. 477–492. Co-authored with Kateryna Babii. DOI: 10.37868/sei.v7i2.id539.
  *Establishes empirical performance benchmarks for BLE vs. Wi-Fi Direct on Android — foundational research for ResQMesh AI Phase 2 adaptive transport layer.*
 - **[Preprint — March 31, 2026]**
  "AI-Driven Message Prioritization for Offline BLE Mesh
  Networks in Disaster Response Scenarios" — SSRN,
  DOI: 10.2139/ssrn.6428398.
 - **[Published — March 25, 2026]** "[When the Internet Dies, Your Phone Can Still Be Smart: Building AI-Powered Disaster Communication](https://hackernoon.com/when-the-internet-dies-your-phone-can-still-be-smart-building-ai-powered-disaster-communication)" — HackerNoon
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
- Custom emergency vision model — MobileNetV2 transfer learning trained on curated emergency scene dataset (fire, flood, weather, security); 100% accuracy on held-out test sets; fully offline TFLite deployment
- [Google ML Kit Image Labeling](https://developers.google.com/ml-kit/vision/image-labeling) — retained as fallback classifier when custom model confidence is below threshold
- FEMA Incident Command System — ICS-213 standard reference

---

*ResQMesh AI Platform is an independent research project. It is not affiliated with FEMA, NSF, or any U.S. government agency.*
