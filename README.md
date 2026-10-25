# Vert TV

Native Kotlin Android TV shell plus a Cloudflare Worker, D1 activation database, private R2 movie storage, and a Cloudflare Access admin studio.

## Live test backend

https://vert-tv.mahlangu843.workers.dev

Test APK download: https://vert-tv.mahlangu843.workers.dev/downloads/vert-tv-debug.apk

D1 and private R2 are provisioned and migrations applied. Admin authentication configuration and Payfast credentials are still required. Cloudflare Access is enabled with team domain `cold-sunset-0330.cloudflareaccess.com`. The current CLI login cannot create Access applications (403); create the admin application in the dashboard and set its Application Audience tag in `CF_ACCESS_AUD`.

## Status

Android debug APK compiled successfully in GitHub Actions (run 40211654811). Device/emulator playback and Payfast end-to-end verification are still pending. Local Android compilation requires JDK 17 and Android SDK 35. Payfast must be configured and tested in sandbox before accepting payments. No production credentials are checked in. A browser test URL serves the backend/landing page and admin studio; it cannot run a native Android APK.

## Android Studio / VS Code

Open this root folder in Android Studio, install JDK 17 and SDK 35, and sync Gradle. The project uses Android Gradle Plugin 8.9.2 and Gradle 8.11.1. If the wrapper is absent, generate it with `gradle wrapper --gradle-version 8.11.1`. Set the API URL in your ignored user Gradle properties or pass it when building:

```sh
./gradlew assembleDebug -PVERT_API_URL=https://vert-tv.YOUR-SUBDOMAIN.workers.dev
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

VS Code can edit the same project; build through Gradle. Install Java/Kotlin extensions. The manifest declares both launcher categories, Leanback, touchscreen optional, Internet and network state permissions. `activity_main.xml` is activation, `MainActivity.kt` routes based on server activation, `PayfastActivity.kt` displays checkout and polls, `DashboardFragment.kt` provides native Leanback rows, and `ViewerActivity.kt` hosts HTTPS-only WebView playback with full-screen video support. D-pad cards use visible focus borders. Device token/email are persisted in private SharedPreferences with backup disabled. Every dashboard fetch checks the server; offline access is not granted.

## Cloudflare provisioning and public test link

```sh
cd backend
npm ci
npx wrangler whoami
# If necessary: npx wrangler login
npx wrangler d1 create vert-tv
npx wrangler r2 bucket create vert-tv-movies
```

Paste the returned D1 `database_id` into `wrangler.jsonc`. Set `PUBLIC_BASE_URL` to your Worker hostname. Set `CF_ACCESS_TEAM_DOMAIN` to your team domain such as `yourteam.cloudflareaccess.com` and `CF_ACCESS_AUD` to the application audience tag. Never put secrets into `vars`.

```sh
npx wrangler d1 migrations apply vert-tv --remote
npx wrangler secret put PAYFAST_MERCHANT_ID
npx wrangler secret put PAYFAST_MERCHANT_KEY
npx wrangler secret put PAYFAST_PASSPHRASE
npm test
npx wrangler deploy
```

The APK download asset is intentionally ignored by Git. Before deploying from a clean checkout, build/download the debug APK and copy it to `backend/public/downloads/vert-tv-debug.apk` to preserve the homepage download link. The currently deployed APK SHA-256 is `e71365ad87c1b3f088dcaaa6bbe87b69fa2213864794d6a9fc04da3e1db8d9f9`.

Wrangler returns your real public `https://vert-tv.<subdomain>.workers.dev` link. Open `/` for the landing page, `/api/health` for health, and `/admin` for the admin studio. Do not invent a hostname before deployment. For local development use `.dev.vars` copied from `.dev.vars.example`, local D1 migrations (`npx wrangler d1 migrations apply vert-tv --local`) and `npm run dev`.

Configure Cloudflare Access self-hosted application policies for `/admin`, `/admin/*`, and `/api/admin/*`, allowing only your admin email/identity group. Configure Access protection on the workers.dev hostname if supported for your account, or use a custom domain for these routes. Leave payment notification, activation, catalog and media routes reachable by devices/Payfast. Worker code independently validates Access JWT issuer, audience, expiry and signature; without configuration the admin fails closed. Do not protect the entire hostname with Access, since TVs and Payfast cannot complete the admin sign-in flow.

Set Cloudflare rate-limiting rules for `/api/activation/start` and API traffic before launch. Protect against checkout spam. Use a scheduled trigger (for example hourly) for the Worker's `scheduled` handler to remove expired playback/checkout tickets. Add an R2 bucket lifecycle rule to abort abandoned multipart uploads. R2 stays private; videos are served through time-limited six-hour playback URLs with byte-range support. Avoid logging ticket query strings in any external logging pipeline.

## Payfast

Enter sandbox credentials in the ignored `backend/.dev.vars` file. After saving, run `node scripts/publish-payfast-secrets.mjs` from the root to transfer the three fields through stdin to Cloudflare Worker secrets without printing their values. Keep `PAYFAST_SANDBOX=true`.

R199 is a once-off **device activation**, not a recurring subscription. Sandbox is enabled by default. Secrets belong only in Worker secrets or ignored `.dev.vars`. Checkout is a server-signed form, with a separate expiring checkout ticket. The app never sees merchant credentials or passphrase. ITN checks ordered signature, merchant ID, Payfast source DNS/IP, Payfast server validation, exact amount, matching server-created order and COMPLETE status. The payment ID is unique and duplicate notifications are idempotent. Return-page navigation never grants activation.

A freshly entered email does not restore another device's subscription: that would let anyone claim access by knowing a paid email address. Preserve the stored device token to resume an existing pending checkout/payment. Account recovery or multi-device licensing needs a verified email OTP or device-linking policy before release. Admin authentication uses Cloudflare Access; TV activation uses Cloudflare-hosted device tokens.

Test successful payment, cancellation, delayed ITN, forged/duplicate ITN, incorrect merchant/amount, network loss, app restart and token rejection in sandbox. Then set `PAYFAST_SANDBOX` to `false` and use production secrets. Confirm Payfast's current allowed notification source addresses and validation behavior with your merchant integration during sandbox testing; DNS source verification is intentionally strict.

## Admin movie workflow

Sign in at `/admin`, add an HTTPS link with title/category, or choose MP4/WebM with title/category. Movies upload in 8 MiB multipart chunks up to 50 GB. Completion checks total object size and publishes into D1. Cancellation on errors attempts to abort incomplete uploads. Keep the tab open during upload; resumable uploads after browser restart are not implemented. External YouTube/embed URLs open inside the secure viewer; uploaded movies use a first-party HTML5 player. Codec compatibility depends on the Android TV device (H.264/AAC MP4 is generally the broadest option). Removing a title unpublishes it by deleting its D1 record; the original R2 object remains for deliberate storage retention and can be deleted separately in Cloudflare.

## Ad filtering and Brave

`AdBlockWebViewClient.kt` intercepts exact/subdomain ad and tracking hosts, returns empty resource responses, and applies cosmetic CSS on page load. It does not block shared video delivery domains such as `googlevideo.com`, which would break playback. Payment uses a separate unfiltered client. WebView file/content access, mixed content and non-HTTPS top-level navigation are disabled. No JavaScript-to-native bridge is exposed.

Brave's actual blocker is the Rust `adblock-rust` engine, not a JavaScript snippet: https://github.com/brave/adblock-rust . It is not vendored or represented as integrated in this project. Shipping it in Android WebView requires a separately built Android NDK/JNI library and maintained filter/resource lists, plus applicable license compliance. Current blocking is a limited built-in alternative. It cannot guarantee ad-free YouTube playback or reproduce Brave Shields. Service-worker requests and first-party/server-inserted ads can bypass this implementation. Arbitrary third-party page compatibility and TV web-player controls require device testing.

## Verification

`cd backend && npm test` runs 16 tests covering signature canonicalization, token hashing, safe URL handling, HTML escaping, successful verified ITN activation, duplicate ITN idempotency and wrong-amount rejection. Worker request tests cover authorization and rejection paths. Use `npx wrangler deploy --dry-run` to validate the Worker bundle. Schema is in `backend/migrations/`. No APK or successful live payment should be claimed until Android build/device and Payfast sandbox tests pass.
