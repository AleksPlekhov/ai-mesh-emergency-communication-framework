# ResQMesh AI Framework

> **An open-source Android framework extending BLE mesh communication with on-device AI capabilities for disaster response and emergency communication.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org)
[![Status](https://img.shields.io/badge/Status-Active%20Research-yellow.svg)]()

---

## Overview

**ResQMesh AI Framework** is a research-driven Android framework built on top of the [BitChat](https://github.com/permissionlesstech/bitchat-android) open-source BLE mesh messaging protocol. It extends core mesh communication with an **on-device AI layer** specifically designed for disaster response scenarios where internet infrastructure is unavailable or destroyed.

During natural disasters — hurricanes, earthquakes, wildfires — traditional communication infrastructure fails precisely when it is needed most. This framework addresses that critical gap by combining:

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
│              ResQMesh AI Framework               │
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
    ├── ai/voice/           — Offline STT (Vosk), voice recording, waveform tools
    └── ai/classifier/      — Message priority classification
          ├── KeywordMessageClassifier   (~90 FEMA/ICS keywords across 9 categories, always available)
          ├── TFLiteMessageClassifier    (neural model, activated by dropping .tflite asset)
          ├── CompositeMessageClassifier (keyword-first → TFLite fallback pipeline)
          └── MessageClassifierFactory   (selects best available backend at runtime)
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
2. **`TFLiteMessageClassifier`** — Lightweight neural model (MobileBERT-style) runs as fallback for messages that don't match keywords. Activated automatically when `message_classifier.tflite` is placed in module assets. Confidence threshold: 30%.

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

### M3 — FEMA ICS-213 Situation Report Generator

Automatically aggregates prioritized messages from across the mesh network and generates structured situation reports compatible with the **FEMA Incident Command System (ICS-213)** General Message standard.

**Output includes:**
- Active mesh node count and network topology summary
- Incident count by priority category
- Geographic clustering of distress signals (when location sharing enabled)
- Timestamp-indexed event log

**Why it matters:** Emergency coordinators need structured, actionable information — not raw message feeds. ICS-213 compatibility ensures reports integrate directly into existing federal and state emergency management workflows.

---

### M4 — AI Energy Optimizer

Intelligently manages radio interface switching and node role assignment to maximize mesh network lifetime under battery constraints.

**Capabilities:**
- Adaptive BLE scan interval based on network traffic patterns
- Dynamic switching between BLE and Wi-Fi Direct based on bandwidth requirements and battery level
- AI-driven relay node selection — assigns relay responsibility to devices with higher battery reserves
- Passive mode transition for critically low-battery devices while maintaining mesh participation

**Why it matters:** In prolonged disaster scenarios (multi-day events), battery life is a critical constraint. Extending network lifetime by even 20-30% can be the difference between maintaining communication during critical rescue windows.

---

## Roadmap

**Phase 1 — Core AI Framework (current, 0–3 months):**
- ✅ Project forked from BitChat Android (GPL-3.0)
- ✅ `:resqmesh-ai` Gradle module — dedicated AI library module, independent of `:app`
- ✅ M1: Keyword classifier — ~90 FEMA/ICS keyword rules across 9 emergency categories
- ✅ M1: TFLite classifier — neural model stub ready (drop in `.tflite` asset to activate)
- ✅ M1: `CompositeMessageClassifier` — keyword-first, TFLite fallback two-stage pipeline
- ✅ M1: Real-time visual indicators — category-coloured left stripes and emoji badges on every classified message; theme-aware (Material3 light/dark)
- ✅ M1: Emergency Feed — always-visible feed button opens a priority-sorted category sheet; category detail view shows filtered messages with system back navigation
- ✅ M2: Offline Speech Recognition — voice-to-text input via Vosk Android (no internet required)
- 🔄 M3: FEMA ICS-213 Report Generator — automated situation reports compatible with federal emergency standards
- 🔄 M4: AI Energy Optimizer — adaptive BLE/Wi-Fi switching and intelligent relay node management

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

This framework addresses priorities identified by multiple U.S. federal initiatives:

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
git clone https://github.com/AleksPlekhov/ai-mesh-emergency-communication-framework.git

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

This framework extends **BitChat Android** ([permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android)), an open-source BLE mesh messaging application released under GPL-3.0. The core BLE mesh transport, Noise Protocol encryption, and multi-hop routing are derived from BitChat. The AI inference layer, speech recognition module, FEMA reporting system, and energy optimization components are original contributions of this project.

In accordance with GPL-3.0, this project is released under the same license and all source code is publicly available.

---

## Research & Publications

- *[Preprint — coming soon]* "AI-Driven Message Prioritization for Offline BLE Mesh Networks in Disaster Response Scenarios" — arXiv

*This section will be updated as research outputs are published.*

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
Lead Android & Mesh Developer | Researcher
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

*ResQMesh AI Framework is an independent research project. It is not affiliated with FEMA, NSF, or any U.S. government agency.*
