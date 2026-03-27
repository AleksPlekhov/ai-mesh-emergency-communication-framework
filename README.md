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
    │     ├── KeywordMessageClassifier   (~90 FEMA/ICS keywords across 9 categories, always available)
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
    └── ai/voice/           — Offline STT (Vosk), voice recording, waveform tools
```

The `:resqmesh-ai` module is an Android library with no dependency on `:app`, keeping AI code independently testable and reusable.

---

## Core Modules

### M1 — AI Message Priority Classifier

Classifies incoming mesh messages into priority tiers and emergency categories using a two-stage on-device pipeline. In a disaster zone, not all messages are equal — a medical emergency must reach rescue coordinators before routine check-in messages.

**Priority levels:**
- `CRITICAL` — Medical emergencies, SOS signals, structural collapse reports
- `HIGH` — Fire, flood, active security threats
- `NORMAL` — Infrastructure issues, weather hazards, missing persons, resource requests
- `LOW` — Routine check-ins, test messages, false alarms

**9 recognised emergency categories:**

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
- CRITICAL packets are dequeued ahead of all NORMAL and LOW packets, regardless of arrival order
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
- Power mode change notifications shown as toasts; fully localised across all 35 supported languages

**Planned (Phase 2):**
- Adaptive BLE scan interval based on network traffic patterns
- Dynamic switching between BLE and Wi-Fi Direct based on bandwidth requirements
- Protocol-level battery metadata broadcasting so coordinators see node health across the mesh

**Why it matters:** In prolonged disaster scenarios (multi-day events), battery life is a critical constraint. Extending network lifetime by even 20-30% can be the difference between maintaining communication during critical rescue windows.

---

## Roadmap

**Phase 1 — Core AI Platform (current, 0–3 months):**
- ✅ Project forked from BitChat Android (GPL-3.0)
- ✅ `:resqmesh-ai` Gradle module — dedicated AI library module, independent of `:app`
- ✅ M1: Keyword classifier — ~90 FEMA/ICS keyword rules across 9 emergency categories
- ✅ M1: TFLite classifier — `emergency_model.tflite` included in assets; CompositeClassifier active out of the box
- ✅ M1: `CompositeMessageClassifier` — keyword-first, TFLite fallback two-stage pipeline
- ✅ M1: Real-time visual indicators — category-coloured left stripes and emoji badges on every classified message; theme-aware (Material3 light/dark)
- ✅ M1: Emergency Feed — always-visible feed button opens a priority-sorted category sheet; category detail view shows filtered messages with system back navigation
- ✅ M2: Offline Speech Recognition — voice-to-text input via Vosk Android (no internet required)
- ✅ BLE Priority Queue — CRITICAL packets preempt NORMAL/LOW at the radio layer; 1001× faster delivery proven by benchmark
- ✅ Emergency Location Attachment — optional GPS / manual address appended to CRITICAL and HIGH messages via `LocationAttachSheet`
- ✅ M3: FEMA ICS-213 Report Generator — Compose-rendered report with share/print support; pure-Kotlin HTML generator in `:resqmesh-ai`
- ✅ M4: AI Energy Optimizer (first iteration) — `EnergyRelayPolicy` adapts relay probability to battery state; CRITICAL packets always relay even at ULTRA_LOW_POWER


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

- **[Published — March 25, 2026]** "[When the Internet Dies, Your Phone Can Still Be Smart: Building AI-Powered Disaster Communication](https://hackernoon.com/when-the-internet-dies-your-phone-can-still-be-smart-building-ai-powered-disaster-communication)" — HackerNoon
- *[Preprint — coming soon]* "AI-Driven Message Prioritization for Offline BLE Mesh Networks in Disaster Response Scenarios" — arXiv

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
- FEMA Incident Command System — ICS-213 standard reference

---

*ResQMesh AI Platform is an independent research project. It is not affiliated with FEMA, NSF, or any U.S. government agency.*
