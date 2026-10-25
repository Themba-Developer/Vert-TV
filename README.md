# Vert TV

Native Kotlin Android TV app plus a separate web app, email/password accounts, a Cloudflare Worker, D1 database, private R2 movies and Payfast once-off account activation.

Current customer source: Dulo. The 2Embed default catalogue was rolled back; its admin tools and records remain available.

## Links

- Welcome page: https://vert-tv.mahlangu843.workers.dev
- Web app (no Android TV required): https://vert-tv.mahlangu843.workers.dev/app
- Create account: https://vert-tv.mahlangu843.workers.dev/signup
- User login: https://vert-tv.mahlangu843.workers.dev/login
- Admin login: https://vert-tv.mahlangu843.workers.dev/login?next=/admin
- Admin dashboard: https://vert-tv.mahlangu843.workers.dev/admin
- Android APK: https://vert-tv.mahlangu843.workers.dev/downloads/vert-tv-debug.apk

The welcome URL and cinematic design are preserved. `/app` provides the real account/payment/library/player functionality in a browser. It uses the same backend and account entitlement as Android TV. Uploaded R2 movies play in the first-party player; third-party pages may prohibit embedding. Playback stays inside the app with no external-source link or popup/top-navigation permissions; a site that refuses embedding cannot be forced to display by the browser shell. Native WebView ad filtering does not apply to the browser web app.

## Accounts and free admins

Users create an account using email and a password of 12–128 characters. Unpaid accounts can activate once for R399 with Payfast. After a verified ITN, the same account can sign in on another device without another payment. Navigating a payment return URL alone never unlocks access.

These two reserved emails receive the admin role and automatic payment exemption:

- `vertcorpgroup@gmail.com`
- `mahlangu843@gmail.com`

Their first signup also requires the private `EXEMPT_ACCESS_CODE` from the ignored `backend/.dev.vars` file. This proves authorization to claim a reserved admin identity. After signup, login uses only email/password. Arbitrary signup fields cannot grant roles or payment exemption. Cloudflare is hosting infrastructure only; Cloudflare Access login has been retired. Do not run the legacy Access setup helper to re-enable the old gate.

Passwords are HMAC-peppered and hashed with salted PBKDF2-SHA512 (100,000 iterations, within the Workers WebCrypto cap). The pepper is a Worker secret, separate from D1. Preserve `AUTH_PEPPER`; changing it invalidates password verification for existing accounts. Passwords, session tokens and setup tokens must never be committed or printed.

Browser sessions use `__Host-vert_session` Secure, HttpOnly, SameSite=Lax cookies. Admin browser sessions expire in 12 hours, user browser sessions in 30 days, and TV bearer sessions in 180 days. Only token hashes are stored in D1. Logout revokes the session server-side. Authentication attempts are limited by IP and account email in D1. Mutations reject cross-origin browser requests. Email delivery, email verification for ordinary users and forgotten-password delivery are not yet configured; reserved administrator accounts are protected by the private setup code.

Existing legacy device activation tokens remain accepted so earlier installations are not abruptly revoked. When creating an account from a legacy paid TV, the existing bearer token can prove and carry forward that device entitlement. New installations use email/password accounts. Older free activation by private code remains for compatibility; it does not grant admin session access.

## Android Studio / VS Code

Open this root in Android Studio, install JDK 17 and Android SDK 35, and sync Gradle. Gradle 8.11.1 is included through the wrapper. AGP is 8.9.2; the default API URL already points to the test deployment.

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

VS Code uses the same source and Gradle build. The manifest declares Leanback and standard launcher categories, no required touchscreen, Internet and network state. D-pad controls have focus states. `MainActivity.kt` handles signup/login and account activation; `PayfastActivity.kt` polls verified server entitlement; `DashboardFragment.kt` supplies native rows and sign-out; `ViewerActivity.kt` supplies secure HTTPS playback. Preferences persist email and bearer token with backup disabled, never passwords.

The welcome screen uses original poster art and slow local motion, with an Activate action and a Sign in action. The website supports paused/reduced motion. Artwork paths, provenance and the final image-generation prompt are in `docs/welcome-art.md`.

## Backend provisioning

D1 `vert-tv` and private R2 `vert-tv-movies` are provisioned. Apply all migrations in order; they preserve the existing content and activation records.

```sh
cd backend
npm ci
npx wrangler d1 migrations apply vert-tv --remote
npx wrangler secret put PAYFAST_MERCHANT_ID
npx wrangler secret put PAYFAST_MERCHANT_KEY
npx wrangler secret put PAYFAST_PASSPHRASE
npx wrangler secret put AUTH_PEPPER
npx wrangler secret put EXEMPT_CODE_HASH
npm test
npx wrangler deploy
```

`EXEMPT_CODE_HASH` is the SHA-256 hash of the private registration code, not its plaintext. The owner’s ignored editor file already contains the private code and password pepper. Use unique high-entropy values for another installation. `PUBLIC_BASE_URL` must match the deployed origin. No Cloudflare Access team or AUD is required for app login. Keep API setup tokens in the ignored editor file and revoke temporary tokens after use.

For local development copy `backend/.dev.vars.example` to `.dev.vars`, fill the required values, apply local D1 migrations with `--local`, and run `npm run dev`. Use localhost development credentials and sandbox Payfast for local testing. The current production config intentionally sets `PAYFAST_SANDBOX=false` at the owner’s request. A real live payment/ITN is still pending owner verification.

The APK download asset is ignored by Git. Before deploying a clean checkout, build or download its APK and copy it to `backend/public/downloads/vert-tv-debug.apk`. The GitHub Actions workflow compiles the APK, uploads an artifact, runs backend tests and bundles the Worker without deploying it.

## Payfast and storage

The backend creates the R399 order and signed checkout form. Merchant credentials and passphrase remain server-side. ITN verifies signature, merchant ID, Payfast source DNS/IP, server-to-server validation, order, exact gross amount and COMPLETE status. Unique payment IDs and duplicate checks provide idempotency. Authenticated browser and TV sessions read the shared activation anchor, so verified payment unlocks the whole account. Live merchant credential validity and real payment delivery require actual merchant testing.

The admin can create/edit/remove links and upload MP4/WebM movies in 8 MiB multipart chunks, up to 50 GB. Completion checks object size and publishes into D1. R2 remains private; active accounts receive six-hour playback URLs with range support. Keep the upload tab open; cross-restart upload resume is not implemented. Configure an R2 lifecycle rule for abandoned multipart uploads. Removing a title retains its R2 object for deliberate storage retention; remove the object separately when desired.

An hourly scheduled handler removes expired playback/checkout tickets, sessions and stale login rate-limit counters. Avoid logging bearer credentials or playback query tickets in external logging systems.

## Ad filtering

`AdBlockWebViewClient.kt` provides exact/subdomain ad host filtering and cosmetic CSS in the native viewer. It is a limited built-in filter, not Brave Shields. Brave uses the separate Rust engine https://github.com/brave/adblock-rust; that engine is not bundled. First-party/server-inserted ads and service-worker requests can bypass WebView interception. Ad-free YouTube cannot be guaranteed. Payment uses an unfiltered client. File/content access, mixed content and non-HTTPS navigation are disabled; no native JavaScript bridge is exposed.

## Verification

`cd backend && npm test` runs 51 tests. Account tests use SQLite with the actual D1 migrations, including role restrictions, free admin signup proof, password verification, cookies/logout, expiry, CSRF, login throttling and shared paid entitlement after verified mock ITN. Payment signatures, duplicate notifications, wrong amounts and legacy activation checks are also covered. `npx wrangler deploy --dry-run` validates the Worker bundle. Browser checks validate the welcome page and account UI. Android build success does not replace TV-device playback and remote-control testing.

Current verification: Android version 1.7 compiled in GitHub Actions run 40230897722. Live desktop/phone browser checks passed for signup, password login, logout/session revocation, unpaid paywall and denial of non-admin API access. A temporary test account was removed after verification. The welcome `/` URL and web app `/app` both return 200; unauthenticated `/admin` redirects to `/login?next=/admin`, and admin APIs return 401 without a valid session.

Current APK SHA-256: `785982c24b26c8888fed0959cef4ae90f44c362274555e1923c86488087edb04`.

The browser player fills the viewport, requests browser fullscreen on selection where supported, and shows only a small Back to library overlay. The Back overlay returns to the library and stops the embedded page; Escape and parent-page Back navigation are also handled. The iframe sandbox blocks popup windows and top-level navigation; source-site branding remains controlled by that site.

## Linked library collections

The admin library supports `kind=collection` entries as well as ordinary links. Migration 0004 adds ten editable collection tiles pointing to Dulo's published Movies, TV Shows, animation shows, animated movies, Anime, Family Movies, Documentary, Action & Adventure, Comedy and Live TV sections. The source navigation/genre index is https://dulo.mov/genres/ . Category paths were checked on 2026-10-25; filter query parameters are preserved. Cartoons & Animation maps to `/shows/?shelf=genre-16`; this is a general animation category, not a kids-only filter.

Collection tiles open the section directly in the existing edge-to-edge internal viewer. The upstream catalogue and its branding remain controlled by Dulo. No movie files or individual-title catalogue are copied. Existing Movie Box and News records are preserved. The admin can edit/delete these entries and add other providers using Entry type → Library collection. Ordinary title/website entries and R2 uploads continue working. Existing Android builds read the same endpoint and automatically receive the new collection links.

The owner explicitly retained R399 activation for normal accounts; free access for the two reserved admin accounts is unchanged. The welcome `/` URL and separate `/app` URL are unchanged.

Pricing is R399 for all newly generated checkout sessions. All application order inserts explicitly store `amount_cents=39900`; the historical default in migration 0001 is retained unchanged. An already issued, unexpired R199 ticket remains at its stored quoted amount, and verified ITNs are checked against that exact order amount. Starting or resuming checkout after the price update creates/reuses an R399 order. Previous payments and free-admin exemptions are preserved.

R399 verification: 37 backend tests pass. Live welcome/account pages and the generated signed Payfast form were checked at R399.00 using a temporary unpaid account, without submitting payment. The temporary account, order, ticket and sessions were removed. Android 1.4 includes the R399 welcome copy. Ad filtering remains limited to the native content viewer; browser iframe playback blocks popups/top navigation but does not filter in-page ads.

## VidSrc title publishing

Admin studio now supports previewing and publishing individual VidSrc movie/episode embeds by IMDb/TMDB ID, with optional subtitle JSON. Existing Dulo collections remain. The advertised catalogue feeds returned 404 during testing, so automatic catalogue import is unavailable; use Check catalogue feeds to recheck. Previewing a page does not prove video playback. See `docs/vidsrc-integration.md` for endpoints, provider observations and usage.

## 2Embed metadata catalogue

Admin studio now includes a 2Embed catalogue browser with search, trending, ID lookup, similar titles, preview and selected imports. Imported movies/shows include posters and metadata; TV users choose a season/episode in Vert TV on web or Android TV. Imports preserve existing content and skip duplicates. The supplied complete-season player endpoints returned 404, so individual episode embeds are used. See `docs/2embed-integration.md`.

2Embed verification: 51 backend tests pass; Android 1.5 compiled successfully. Desktop/mobile browser checks covered selected imports, real provider metadata/posters, season/episode selection and generated playback URLs with mocked player media. No movie playback or live payment was performed.

The default customer catalogue is now 2Embed: live trending movies/shows, search and pagination work without admin imports. Dulo entries are hidden from customer responses and retained in admin storage for rollback. Custom non-Dulo links and uploads remain. Set `CATALOGUE_SOURCE=curated` to restore curated-only behaviour. The welcome URL is unchanged. See `docs/2embed-integration.md`.

Default catalogue verification: 52 backend tests pass; Android 1.6 compiled successfully. Live Worker checks returned 40 trending movie/show titles without Dulo links, movie/TV searches, full TV seasons and 23 episodes for the sample first season. Temporary paid test activation was removed. No video playback or payment was performed.

Current source: Dulo (`CATALOGUE_SOURCE=dulo`). The 2Embed default catalogue experiment was rolled back at the owner’s request after reported playback failures and limited selection. Dulo collections are restored and 2Embed customer search controls removed; its imported records remain stored but hidden. R399 activation, admin exemptions and the welcome URL are unchanged.

Dulo rollback verification: 53 tests pass. Live paid-viewer check returned 10 Dulo entries with no 2Embed catalogue requests; the web page no longer displays 2Embed search controls. R399 activation remains. Temporary test activation was removed. Android 1.7 restores the previous dashboard and refresh behaviour.

Payfast checkout fix: live `form-action` permits both `https://www.payfast.co.za` and its hosted-payment redirect `https://payment.payfast.io`; sandbox permits only its own host. The old policy blocked the redirect in Chrome/Brave, leaving the customer on the Continue screen. All 54 tests pass. Live browser verification reached the Payfast payment-method page showing R399.00 for Vert Corp Group; no payment was made. This backend fix applies to existing web/Android checkout pages without an APK update. Payfast lists its hosted payment domain in https://support.payfast.help/portal/en/kb/articles/whitelisting-ip-addresses-2-7-2025 .
