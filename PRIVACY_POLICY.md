# bitchat Privacy Policy

*Last updated: March 2026*

## Our Commitment

bitchat is designed with privacy as its foundation. We believe private communication is a fundamental human right. This policy explains how bitchat protects your privacy.

## Summary

**WE DO NOT COLLECT ANY INFORMATION.**

- **No personal data collection** - We don't collect names, emails, or phone numbers
- **No background location collection** - Location is accessed only when you explicitly choose to attach it to an emergency message, or for local BLE/Geohash processing. It is never collected by us or sent to any server
- **Voluntary emergency location sharing** - When the app detects an emergency message, you may optionally attach your GPS coordinates or a typed address. This data is included in the message body and shared only with peers on the mesh — the same way any other message content is shared
- **Hybrid Functionality** - bitchat offers two modes of communication:
  - **Bluetooth Mesh Chat**: This mode is completely offline, using peer-to-peer Bluetooth connections. It does not use any servers or internet connection.
  - **Geohash Chat**: This mode uses an internet connection to communicate with others in a specific geographic area. It relies on Nostr relays for message transport.
- **No tracking** - We have no analytics, telemetry, or user tracking
- **Open source** - You can verify these claims by reading our code

## What Information bitchat Stores

### On Your Device Only

1. **Identity Key** 
   - A cryptographic key generated on first launch
   - Stored locally in your device's secure storage
   - Allows you to maintain "favorite" relationships across app restarts
   - Never leaves your device

2. **Nickname**
   - The display name you choose (or auto-generated)
   - Stored only on your device
   - Shared with peers you communicate with

3. **Message History** (if enabled)
   - When room owners enable retention, messages are saved locally
   - Stored encrypted on your device
   - You can delete this at any time

4. **Favorite Peers**
   - Public keys of peers you mark as favorites
   - Stored only on your device
   - Allows you to recognize these peers in future sessions

### Temporary Session Data

During each session, bitchat temporarily maintains:
- Active peer connections (forgotten when app closes)
- Routing information for message delivery
- Cached messages for offline peers (12 hours max)

## What Information is Shared

### With Other bitchat Users

When you use bitchat, nearby peers can see:
- Your chosen nickname
- Your ephemeral public key (changes each session)
- Messages you send to public rooms or directly to them
- Your approximate Bluetooth signal strength (for connection quality)
- Your location, **if and only if** you chose to attach it to an emergency message (GPS coordinates or typed address become part of the message body)

### With Room Members

When you join a password-protected room:
- Your messages are visible to others with the password
- Your nickname appears in the member list
- Room owners can see you've joined

## What We DON'T Do

bitchat **never**:
- Collects personal information
- Collects or stores location history (any location you attach to a message lives only in that message, not in a separate log)
- Transmits any data to us (the developers)
- Stores data on servers
- Shares data with third parties
- Uses analytics or telemetry
- Creates user profiles
- Requires registration

## Encryption

All private messages use end-to-end encryption:
- **X25519** for key exchange
- **AES-256-GCM** for message encryption
- **Ed25519** for digital signatures
- **Argon2id** for password-protected rooms

## Your Rights

You have complete control:
- **Delete Everything**: Triple-tap the logo to instantly wipe all data
- **Leave Anytime**: Close the app and your presence disappears
- **No Account**: Nothing to delete from servers because there are none
- **Portability**: Your data never leaves your device unless you export it

## Location Data & Permissions

To provide the core functionality of bitchat, we access your device's location data. This access is necessary for the following specific purposes:

### 1. Bluetooth Low Energy (BLE) Scanning
- **Why we need it:** The Android operating system requires Location permission to scan for nearby Bluetooth LE devices (especially on Android 11 and lower). This is a system-level requirement because Bluetooth scans can theoretically be used to derive location.
- **How we use it:** We use this permission strictly to discover other bitchat peers nearby for the "Bluetooth Mesh Chat" mode.
- **Privacy protection:** We do not record or store your location during this process. The data is processed instantaneously by the Android system to facilitate the connection.

### 2. Geohash Chat Functionality
- **Why we need it:** The "Geohash Chat" mode allows you to communicate with others in your approximate geographic area.
- **How we use it:** If you enable this mode, we access your location to calculate a "geohash" (a short alphanumeric string representing a geographic region). This geohash is used to find and subscribe to relevant channels on decentralized Nostr relays.
- **Privacy protection:** 
  - Your precise GPS coordinates are **never** sent to any server or peer.
  - Only the coarse geohash (representing an area, not a pinpoint) is shared with the Nostr network.
  - You can use the "Bluetooth Mesh Chat" mode without this feature if you prefer.

### 3. Emergency Message Location Attachment
- **Why we need it:** When the app's on-device classifier detects that a message you're about to send is an emergency (fire, flood, medical, etc.), it offers to attach your location so that responders can find you.
- **How we use it:** You are always prompted before any location is accessed. You may:
  - Tap **"Use GPS coordinates"** — your device's last known GPS fix is read once and formatted as `latitude,longitude` (e.g. `37.77201,-122.41465`), then appended to your message body as `📍 lat,lon`.
  - Type a location manually (address, landmark) — no GPS access occurs.
  - Tap **"Skip"** — your message is sent without any location.
- **Privacy protection:**
  - Location access is **always on-demand and user-initiated** — there is no background or continuous tracking.
  - The coordinates become part of the message text and travel only over the local Bluetooth mesh to nearby peers (and any Nostr relays if enabled). They are **not** transmitted to us or any third-party server.
  - Each emergency message prompts independently; no location is stored or reused.

**We do not collect, store, or share your location history.** Location data is processed locally on your device to enable these specific features.

## Children's Privacy

bitchat does not knowingly collect information from children. The app has no age verification because it collects no personal information from anyone.

## Data Retention

- **Messages**: Deleted from memory when app closes (unless room retention is enabled)
- **Identity Key**: Persists until you delete the app
- **Favorites**: Persist until you remove them or delete the app
- **Everything Else**: Exists only during active sessions

## Security Measures

- All communication is encrypted
- No data transmitted to servers (there are none)
- Open source code for public audit
- Regular security updates
- Cryptographic signatures prevent tampering

## Changes to This Policy

If we update this policy:
- The "Last updated" date will change
- The updated policy will be included in the app
- No retroactive changes can affect data (since we don't collect any)

## Contact

bitchat is an open source project. For privacy questions:
- Review our code: https://github.com/yourusername/bitchat
- Open an issue on GitHub
- Join the discussion in public rooms

## Philosophy

Privacy isn't just a feature—it's the entire point. bitchat proves that modern communication doesn't require surrendering your privacy. No accounts, no servers, no surveillance. Just people talking freely.

---

*This policy is released into the public domain under The Unlicense, just like bitchat itself.*