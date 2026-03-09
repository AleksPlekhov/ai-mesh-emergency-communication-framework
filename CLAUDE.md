# Claude Code Instructions — DisasterMesh AI / BitChat Android

> **Effective: March 2026**  
> **Project:** Decentralized BLE mesh messenger with on-device AI for disaster response.  
> **Repository:** `/Users/oleksandr/Documents/_Projects/_DMAF`

---

## 1. Project Overview

### What This Is
A privacy-focused, off-grid communication Android app combining:
- **BLE mesh networking** — peer-to-peer without internet or servers
- **Noise Protocol encryption** — secure channels without PKI
- **Nostr relay integration** — bridge to wider internet when available
- **On-device AI** — message prioritization, offline speech recognition, FEMA-style reporting

### Multi-Module Structure
```
:app                    — Main Android app (UI, mesh, crypto, Nostr)
:disastermesh-ai        — AI library module (TFLite, Vosk STT)
docs/                   — Architecture specs (file_transfer.md, SOURCE_ROUTING.md, etc.)
```

---

## 2. Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin (JVM 1.8) |
| UI | Jetpack Compose (Material 3) |
| Build | Gradle (Kotlin DSL), `libs.versions.toml` |
| Async | Coroutines + Flow |
| BLE | Nordic Semiconductor BLE library (2.6.1) |
| Crypto | BouncyCastle, Tink, Noise Protocol Framework |
| AI/ML | TensorFlow Lite (2.14.0), Vosk (0.3.47) |
| Networking | OkHttp (WebSocket for Nostr), Tor/Arti (planned) |
| Location | Google Play Services Location |

---

## 3. Directory Map

### App Module (`app/src/main/java/com/bitchat/android/`)

| Package | Responsibility |
|---------|----------------|
| `ui/` | Compose screens, ViewModels, theme |
| `ui/media/` | Image viewer, audio player, file picker |
| `ui/debug/` | Mesh graph visualization, debug settings |
| `mesh/` | **BLE core**: scanning, advertising, GATT, packet relay, security |
| `protocol/` | Binary wire protocol, compression, padding |
| `crypto/` | Encryption primitives |
| `noise/` | Noise Protocol implementation (handshake + transport) |
| `nostr/` | Nostr protocol: events, relays, Bech32, PoW, DMs |
| `service/` | `MeshForegroundService` — keeps mesh alive in background |
| `services/` | `MessageRouter`, `VerificationService`, `MeshGraphService` |
| `geohash/` | Location channels, geocoding (OSM + Android fallback) |
| `identity/` | Key management, identity announcements |
| `model/` | Data classes: `BitchatMessage`, `FragmentPayload`, etc. |
| `sync/` | Gossip sync, bloom filters, packet deduplication |
| `onboarding/` | Permission flows, battery optimization, BT setup |
| `features/file/` | File transfer logic |
| `features/media/` | Media handling |
| `util/` | Extensions, constants, binary encoding |

### AI Module (`disastermesh-ai/src/main/java/com/bitchat/android/ai/`)

| Package | Responsibility |
|---------|----------------|
| `classifier/` | Message priority classification (TFLite + keyword fallback) |
| `voice/` | Vosk STT, voice recording, waveform visualization |

---

## 4. Critical Patterns & Conventions

### State Management
- **ViewModels** expose `StateFlow` to Composables
- **Business logic** lives in services (not ViewModels)
- **Service-to-UI communication** via `BroadcastReceiver` or Flow from `MeshForegroundService`

### Permissions (CRITICAL)
The app requires dangerous runtime permissions:
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
- `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `RECORD_AUDIO` (voice input)
- `POST_NOTIFICATIONS` (Android 13+)
- `READ_MEDIA_*` / `READ_EXTERNAL_STORAGE` (file sharing)

**Always** verify permission patterns in `MainActivity.kt` or `onboarding/` before adding new hardware features.

### Background Execution
- **Never** start BLE operations without binding to `MeshForegroundService`
- Background limits are strict; the service must display a notification to stay alive
- Use `MeshServicePreferences` to check if background mode is enabled

### Threading
- **I/O operations** → `Dispatchers.IO`
- **BLE callbacks** → handled on dedicated threads, marshal to coroutines via `CoroutineScope`
- **UI updates** → use `collectAsStateWithLifecycle()`

### Naming
- ViewModels: `*ViewModel`
- Services: `*Service` (or `*Manager` for utility services)
- Screens: `*Screen`
- Composables: `PascalCase`, descriptive (e.g., `MessageBubble`, not `MsgBbl`)

---

## 5. Key Files You Must Know

### Entry Points
```
MainActivity.kt           — Single Activity, onboarding orchestration
MeshForegroundService.kt  — Persistent background service
BitchatApplication.kt     — App initialization
```

### Core Protocol
```
mesh/BluetoothMeshService.kt      — Main BLE coordinator
mesh/BluetoothGattServerManager.kt — GATT server for mesh
mesh/BluetoothGattClientManager.kt — GATT client connections
mesh/PacketRelayManager.kt        — Multi-hop routing
protocol/BinaryProtocol.kt         — Wire format
```

### Security
```
crypto/EncryptionService.kt   — High-level encryption API
noise/NoiseEncryptionService.kt — Noise Protocol handshake
noise/NoiseSession.kt          — Session state machine
identity/SecureIdentityStateManager.kt — Key storage (encrypted prefs)
```

### Nostr Integration
```
nostr/NostrRelayManager.kt  — WebSocket relay connections
nostr/NostrTransport.kt     — Bridge between mesh and Nostr
nostr/NostrCrypto.kt        — Event signing/verification
```

### UI
```
ui/ChatScreen.kt        — Main chat UI (refactored, component-based)
ui/ChatViewModel.kt     — Primary ViewModel for chat state
ui/CategoryMessagesScreen.kt — Category-filtered message view
ui/theme/Theme.kt       — Material 3 color scheme
```

---

## 6. Build & Development

### Common Commands
```bash
./gradlew :app:assembleDebug              # Build debug APK
./gradlew :disastermesh-ai:test           # Run AI module unit tests
./gradlew :app:test                       # Run app unit tests
./gradlew :app:connectedAndroidTest       # Instrumented tests (device required)
./gradlew lint                            # Lint check
./gradlew clean                           # Clean build
```

### Version Catalog
All dependencies are in `gradle/libs.versions.toml`.  
Add new dependencies there, reference in build scripts as `implementation(libs.xxx)`.

### Testing Strategy
- **Unit tests**: `app/src/test/kotlin/` — business logic, crypto, protocols
- **AI tests**: `disastermesh-ai/src/test/` — classifier tests with Robolectric
- **Integration tests**: `app/src/androidTest/` — minimal, requires BLE hardware

**Note**: BLE is difficult to mock. Focus tests on protocol parsing, crypto, and state machines. Use `Robolectric` for tests needing Android resources.

---

## 7. Architecture Decisions

### Why Noise Protocol?
Zero-RTT encryption with mutual authentication, no certificate infrastructure required. Perfect for ephemeral disaster scenarios.

### Why Nostr?
Decentralized relay network provides internet fallback without central servers. Messages are signed and verifiable.

### Why On-Device AI?
Cloud dependency is fatal in disasters. TFLite + Vosk run entirely offline.

---

## 8. Debugging & Development Tips

### Logs
All mesh components use `android.util.Log` with consistent tags:
- `BluetoothMeshService`
- `MeshForegroundService`
- `NostrRelayManager`

### Debug Features
- `DebugSettingsSheet` — enable mesh graph visualization, verbose logging
- `MeshGraphService` — generates graph data for network topology debugging
- Shake device to open debug panel (if enabled)

### Common Pitfalls
1. **Android 12+ (API 31+)**: BLUETOOTH_SCAN/CONNECT/ADVERTISE are runtime permissions
2. **Location**: BLE scanning requires location permission on all Android versions
3. **Background**: `startForegroundService()` requires notification permission on Android 13+
4. **Memory**: Large file transfers use chunking; check `FragmentManager.kt` for limits

---

## 9. AI Module Specifics

### Module Placement Rule (MANDATORY)

> **New code with no Android framework or Jetpack Compose dependencies MUST go in `:disastermesh-ai`.**

| Code type | Module |
|-----------|--------|
| Pure Kotlin logic, data classes, algorithms | `:disastermesh-ai` |
| Functions that use `ClassificationResult`, `MessagePriority` | `:disastermesh-ai` |
| HTML/report generators with no Android deps | `:disastermesh-ai` |
| Compose UI, `Context`, `View`, Android SDK types | `:app` |
| BLE, crypto, Nostr, mesh networking | `:app` |

Examples already in `:disastermesh-ai`: `ICS213ReportData`, `ICS213ReportGenerator`, `shouldShowEmergencyBadge`, `categoryEmojiAndLabel`.

### Classifier System
```
MessageClassifierFactory    — Selects TFLite or keyword backend
TFLiteMessageClassifier       — Neural classification (requires .tflite asset)
KeywordMessageClassifier      — Rule-based fallback (always works)
```

### Emergency Classification
```
ai/emergency/EmergencyClassification.kt  — shouldShowEmergencyBadge(), categoryEmojiAndLabel()
ai/report/ICS213ReportData.kt            — Data classes for FEMA ICS-213 report
ai/report/ICS213ReportGenerator.kt       — Pure HTML generator (no Android deps)
```

### Voice Input
```
VoskManager       — Model loading, recognition lifecycle
VoiceRecorder     — PCM audio capture
VoiceVisualizer   — Compose waveform display
```

**Model Location**: `app/src/main/assets/vosk-model-small-en-us-0.15/` (not in repo, downloaded at runtime)

---

## 10. Protocol & Wire Format

### Message Types (BinaryProtocol.kt)
- `MESSAGE` (0x01) — Standard chat message
- `IDENTITY_ANNOUNCEMENT` (0x02) — Public key broadcast
- `FRAGMENT` (0x03) — Large message chunking
- `ACK` (0x04) — Delivery acknowledgment
- `ROUTE_REQUEST` (0x05) — Source routing discovery

### Packet Structure
```
[1 byte: type][2 bytes: payload length][variable: payload][optional: padding]
```

See `docs/file_transfer.md` for file transfer protocol spec.  
See `docs/SOURCE_ROUTING.md` for multi-hop routing spec.

---

## 11. Contributing Guidelines

### Git Workflow (MANDATORY)

> These rules apply to every task, no exceptions.

**Branch naming — always create a new branch from develop before touching any code:**
- New feature → `git checkout -b feature/short-description`
- Bug fix → `git checkout -b fix/short-description`

**Commits — NEVER commit on behalf of the user:**
- After completing work, leave all changes **uncommitted**.
- The user reviews the diff and commits manually.
- Do not run `git add`, `git commit`, or `git push` at the end of a task.

### Before Making Changes
1. Read relevant `docs/*.md` files
2. Check `AGENTS.md` for architectural constraints
3. Verify permission requirements for new features
4. Test with both BLE enabled and disabled
5. **Module placement**: place new code in `:disastermesh-ai` if it has no Android/Compose deps (see Section 9)

### After Making Changes
1. Update ChangeLog file with made changes

### Code Style
- Kotlin official conventions
- Compose: hoist state, use `remember` judiciously
- Prefer `Flow` over callbacks for async streams
- Document public APIs with KDoc

### Security Considerations
- Never log private keys or decrypted message content
- All crypto operations must use `SecureIdentityStateManager`
- Key material must not traverse the heap as `String`

---

## 12. Emergency Contacts

- Architecture questions: See `AGENTS.md`
- Protocol specs: See `docs/` directory
- Build issues: Check `gradle/libs.versions.toml` for version conflicts

---

*Last updated: March 2026 — added Git workflow rules (branch naming + no auto-commits)*
