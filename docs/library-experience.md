# Library experience

The web app has a cinematic hero, compact search bar, card artwork, per-card Loved heart buttons, and Discover/Recent/Loved/Trending/New tabs. Account identity, membership, security-question setup/update, password change and sign-out live in a top Settings dialog. The welcome URL, public admin hiding, signup confirmation, recovery, Dulo source and R399 fee are preserved.

After two characters and a 650ms pause, search displays Dulo's built-in search in a live preview beneath the bar. Expand/Search opens the same query in the existing fullscreen viewer. Clear/Escape/outside clicks cancel pending previews and unload the iframe. These are Dulo-rendered results; Vert TV neither reads the cross-origin results nor copies its API key. Trending maps to Dulo's `shelf=popular`, New movies to `shelf=in-theaters`, and New series to `shelf=new-seasons`. Switching away unloads the discovery iframe.

Migration 0007 adds per-account `library_activity`. Paid/exempt accounts can GET/POST `/api/library/activity`. Only published content IDs are accepted; no arbitrary URLs or titles can be injected. Loved and last-opened are independent, scoped to the signed-in account, and work across devices. **Recent means library entries opened from Vert TV. It is not watched progress or individual-title history inside Dulo collections.** Search/discovery previews and fullscreen searches are not recorded as fictional movies. Dulo's own watch history remains inside Dulo.

`POST /api/auth/password/change` requires an authenticated session, current password, new password and matching confirmation. It validates origin, applies rate limits and uses an atomic compare-and-update before revoking all sessions. The client returns to sign-in. Payment exemption, activation and recovery answers are preserved.

Android 2.0 adds Settings at the top (also available in the navigation row), secure password changes, live Dulo search preview, Recent/Loved rows and Trending/New shortcuts. Hold OK on a saved library card to add/remove Loved. Existing remote focus styling and internal viewer protections remain. Real-device TV navigation/playback still requires device testing.
