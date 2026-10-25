# VidSrc integration

The admin studio has an Add a VidSrc title section. It creates ordinary Vert TV content entries pointing directly to the documented provider embed URL, so the existing web full-screen viewer and Android TV viewer both work without a client update.

## Admin workflow

1. Choose Movie or TV episode.
2. Enter an IMDb ID (`tt17048514`) or positive numeric TMDB ID (`927085`). TV entries also require season and episode. Specials season zero is allowed.
3. Optionally supply a public HTTPS subtitle JSON URL. It is encoded in `sub.info`; the backend never fetches arbitrary subtitle URLs.
4. Choose Preview player. This validates and opens the embed without writing a library entry. Confirm actual playback in your browser.
5. Enter your catalogue title/category, then Publish to Vert TV. It appears after refreshing the web or TV library.

Fill movie example and Fill TV example reproduce the supplied examples. They fill the form only and never publish automatically. Movie titles/posters/genres are not supplied by the example feed schema; this integration uses manually entered titles/categories.

## Endpoints

- `POST /api/admin/vidsrc/preview`: validate input and return `{url}` without publishing.
- `POST /api/admin/vidsrc/publish`: publish `{title,category,mediaType,mediaId,season,episode,subtitlesUrl}` as appropriate.
- `GET /api/admin/vidsrc/feeds`: inspect the four fixed provider feed endpoints with bounded timeouts and no forwarded user credentials. It reports status/JSON availability; it never imports HTML error responses as titles.

All routes require an authenticated administrator. Publish/preview POSTs enforce the existing origin checks. ID/episode validation prevents path injection; subtitle URLs must use HTTPS and contain no embedded credentials. Embed URLs are generated server-side with the fixed provider origin.

## Observed provider state — 2026-10-25

Documentation: https://vidsrc.mov/

The supplied movie and TV embed endpoints returned HTTP 200 HTML wrappers without frame-denial headers. The wrappers contain nested players on `vsembed.ru`. This confirms embed-page availability only, not successful video playback, content availability, ad-free operation or any relationship to Dulo.

The advertised `/vapi/movie/new`, `/vapi/movie/add`, `/vapi/tv/new`, and `/vapi/episode/latest` feeds all returned HTTP 404. Checked pagination examples (`/vapi/movie/new/15`, `/vapi/movie/new/1`, `/vapi/tv/add/15`, `/vapi/episode/latest/1`) also returned 404. Bulk catalogue import is not implemented against these unavailable feeds. The Check catalogue feeds button rechecks the fixed endpoints when requested.

Current Dulo collections and uploaded R2 content are preserved. No sample movie or episode is published automatically. Existing activation pricing, accounts and payment exemptions are unchanged. The iframe keeps the same popup/top-navigation sandbox restrictions, which some third-party players may not support. Provider playback and advertising behavior remain external to Vert TV.
