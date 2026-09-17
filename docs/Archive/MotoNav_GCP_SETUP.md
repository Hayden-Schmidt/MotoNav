# Google Cloud / Navigation SDK account setup — Hayden-only steps

These steps require your own Google account, billing details, and Cloud Console access — they can't be done by Claude (no login, and entering payment details is off-limits regardless). This is the one blocking prerequisite before any Navigation SDK code will actually run. Everything else (gradle config, dependency wiring, data model) is done in the repo already or tracked separately.

## 1. Create/select a Google Cloud project

1. Go to [console.cloud.google.com](https://console.cloud.google.com).
2. Create a new project (suggest naming it `motonav` or similar — this name is just for your own organization, not user-facing).
3. Note the **Project ID** — you'll need it later for the API key restriction step.

## 2. Enable billing

1. In the Cloud Console, go to **Billing** and link a payment method to the project.
2. This is required even to stay within the free tier (1,000 destinations/month free — see `docs/RESEARCH_NOTES.md` for the pricing breakdown). For solo personal riding use, you're very unlikely to ever be billed, but Google requires a card on file regardless.
3. Optional but recommended: set a budget alert (e.g. $1) in **Billing → Budgets & alerts** so you get notified if usage ever unexpectedly climbs — cheap insurance against a bug that spams route requests.

## 3. Enable the required APIs

In the Cloud Console, go to **APIs & Services → Library** and **Enable** each of:

1. **Navigation SDK** (sometimes listed as "Navigation SDK for Android")
2. **Places API (New)** — needed for the destination-search screen (type an address, get suggestions, pick one, route to it). Pricing checked against Google's current price list (`docs/RESEARCH_NOTES.md` "Places API (New) — destination search pricing") — 10,000 free searches/month, not a realistic cap for solo use, so this is a routine enable, not a cost decision.

(Maps SDK for Android doesn't need a separate manual enable — Navigation SDK bundles it, and it showed up automatically in the key's available-API list once Navigation SDK was enabled.)

Enable both **before** creating the key (see note in step 4 below on why).

## 4. Create an API key

1. Go to **APIs & Services → Credentials**.
2. **Create Credentials → API key**.
3. **Restrict the key** (important — an unrestricted key is a real liability):
   - Under **Application restrictions**, choose **Android apps**.
   - Add MotoNav's package name (`com.motonav.app`, per `app/build.gradle.kts`) and its SHA-1 signing certificate fingerprint. You can get the debug SHA-1 by running `./gradlew signingReport` from the project root and copying the `SHA1` line under the `debug` variant.
   - Under **API restrictions**, select all three in one pass: **Navigation SDK**, **Maps SDK for Android**, **Places API (New)**.
4. Copy the generated key.

**Note (learned the hard way, Sept 2026):** adding an API to a key's restriction list *after* the key already exists didn't reliably take for Places API (New) — a retrofit attempt on the original key wouldn't apply properly. Cleaner path if this happens again: create a fresh key with all needed APIs selected at creation time (as above) rather than editing an existing key's restrictions, and delete the old one once the new one's confirmed working. Not fully diagnosed why the retrofit didn't stick — Console propagation lag is the likely cause, but creating fresh sidesteps it either way.

**Actual root cause found (Sept 2026):** the real issue wasn't propagation lag — **Places API (New) had never actually been enabled** on the project. Google's Library search for "Places API" returns several near-identical entries (Places API, Places API (New), Places API (Legacy), Places SDK for Android, Places Aggregate API, Places Insights) and it's easy to enable the wrong one. Confirmed by checking the product's own page directly (`console.cloud.google.com/apis/library/places.googleapis.com` — service name must read exactly `places.googleapis.com`) and clicking **Enable** there specifically; it showed "Enable" (not "Manage") beforehand, confirming it genuinely wasn't on. Once actually enabled, it appeared correctly in a fresh key's API-restriction picker.

**Live key (current):** Navigation SDK, Maps SDK for Android, Maps SDK for iOS, Places API (New), Maps Elevation API. Two of these are deliberate, not leftovers — see below. Delete any other/older keys left over from the earlier retrofit attempts so only one live credential remains on the project.

- **Maps SDK for iOS** — kept intentionally (Sept 2026 decision, not an oversight from the earlier trimming suggestion in this doc's history). Google's Navigation SDK ships an iOS variant too (`Navigation SDK for iOS`), which means an iOS MotoNav build is now a real, reachable option using the same routing/turn-by-turn backend as Android — not true back when the architecture was notification-listening, which had no iOS equivalent (no iOS API for reading another app's navigation notifications). No iOS app exists yet and none is scheduled in the current phase roadmap, but keeping the key's Maps SDK for iOS restriction pre-enabled means a future iOS build doesn't need its own separate GCP round-trip. Purely a restriction-list convenience; doesn't commit engineering time.
- **Maps Elevation API** — kept intentionally, held in reserve for a possible future elevation/grade feature (e.g. route-grade display, relevant to the "current speed" style metrics band in `docs/MotoNav_UI_SPEC.md` §4). Not used by any code today — same "not built, not wired up" status as before — but no longer flagged for removal, since keeping it costs nothing at this usage tier and Hayden's specifically holding the door open for it.

Target restriction list going forward: Navigation SDK, Maps SDK for Android, Maps SDK for iOS, Places API (New), Maps Elevation API — all five are the intended set now, not a temporary broader-than-needed state.

## 5. Add the key to the project (not committed to git)

Add this line to `local.properties` (already gitignored — same pattern as the existing `MOTONAV_KEYSTORE_*` entries):

```
MOTONAV_MAPS_API_KEY=your_key_here
```

Once this is in place, tell me (or just start the next session) and I'll wire the Gradle/manifest side to read it — that part doesn't need your login, just this key existing in `local.properties` first.

## Why this order

Steps 1–4 need your Google account and a payment method — genuinely can't be automated or done on your behalf. Step 5 only needs the key to exist locally; once it does, the rest of the SDK integration (dependency, manifest metadata, Navigator setup) is normal code work.
