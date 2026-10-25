# 2Embed integration

The admin studio includes a 2Embed catalogue section with Movie/TV selection, trending lists, title search, ID lookup and similar-title search. Metadata is read from the fixed `https://api.2embed.cc` origin. Preview opens the documented player URL in the existing sandboxed viewer without publishing anything. Select up to ten titles and Import selected to publish them into Vert TV.

## Import behavior

- Stored metadata is reduced to title, provider/type/ID, optimized HTTPS poster URL, year, genres, overview, rating and a bounded season list. Cast/reviews and arbitrary upstream embed URLs are discarded.
- Player URLs are generated from validated IDs and season/episode numbers, never trusted from API responses.
- Movie URL: `https://www.2embed.cc/embed/{id}`.
- TV episode URL: `https://www.2embed.cc/embedtv/{id}&s={season}&e={episode}`. The ampersands are intentionally part of this provider's documented path, not replaced with a question mark.
- Imports use deterministic IDs, preserve existing records, skip duplicates and report per-title failures. There is no automatic background publication or replacement of Dulo/VidSrc/R2 entries.
- Provider JSON requests use bounded bodies, timeouts, fixed host/paths, no forwarded login credentials, and a five-minute public metadata cache when available.
- Editing an imported record's playback URL clears provider metadata, so the episode picker does not continue opening an old provider after a source change.

## TV playback

Imported TV records are complete series metadata. The web app and Android TV episode picker use the stored season list and request `/api/content/{contentId}/episodes?season={number}`. This endpoint requires active account/device access, checks the published series and selected season, and retrieves the provider's episode names and generated playback URLs. Users choose an episode inside Vert TV before the external player is shown.

The supplied `/embedtvfull/60735` and `/embedtvfull/tt3107288` URLs both returned HTTP 404 during verification; they are not offered as working full-season players. The individual-episode route and metadata API returned HTTP 200. Successful HTML and metadata responses do not establish actual video availability or successful playback; use Preview to check a title.

## Admin API

- `GET /api/admin/2embed/catalog?mediaType=movie|tv&mode=trending|search|id|similar&page=1&query=...&mediaId=...`
- `POST /api/admin/2embed/preview` with mediaType/mediaId and TV season/episode as applicable.
- `POST /api/admin/2embed/import` with entries `[{mediaType,mediaId}]` (1–10) and optional category.

All admin routes require the existing email/password administrator session, with origin checks for mutations. Metadata endpoints were checked on 2026-10-25: movie lookup, trending movies, TV lookup, trending TV, movie search, TV search and season lookup returned JSON. No movie files were downloaded during endpoint checks.

The source documentation supplied by the owner is https://www.2embed.skin/ . The exact API/player origins supplied by the owner are used; the documentation site's accessibility is not treated as proof of playback availability. Existing popup/top-navigation restrictions and limited native ad filtering remain unchanged. Fees and payment exemptions remain unchanged.

## Default customer catalogue

`CATALOGUE_SOURCE=2embed` now makes `/api/get_links.json` include current movie and TV trending results. Dulo URLs (including the manually added Movie Box link) are hidden from customers while all database records remain available to admins. Other custom links and R2 uploads remain visible. Change the variable to `curated` to restore the previous library view.

Paid/exempt accounts can browse/search paginated results at `GET /api/catalogue?mediaType=movie|tv&mode=trending|search&query=...&page=1`. This does not write titles to D1. Web controls and Android's Browse row/search button expose movies, TV shows and pagination. Clicking a live TV title loads full metadata via `/api/catalogue/tv/{id}` before choosing seasons; `/api/content/2embed-live-tv-{id}/episodes` checks the selected season against provider metadata. Admin-curated imports still work independently.

Provider metadata is cached for five minutes. Failed home feeds produce visible warnings and preserve custom content; search errors allow retry. There is no silent fallback to Dulo. Identical availability to Dulo and actual stream playback have not been established.

Desktop and 390px mobile browser checks passed using real public catalogue metadata and mocked player media: home, movie search, pagination, TV search, complete season choices, episode opening and return to home. Actual movie playback remains a manual check.
