# Dulo search

The owner chose Dulo's built-in search, without supplying a separate TMDB credential. The signed-in active web library includes a movie/show title search box. It opens `https://dulo.mov/search?q=...` in the existing internal fullscreen viewer; the Back control restores the library. Android exposes the same query through the Leanback search icon and Search row, with D-pad Search/Cancel controls and remote-keyboard search support.

Dulo supplies matching and related titles through its own search UI. Its empty-result wording is **No results found.** This means no matching catalogue title, not verified stream unavailability. Search-service errors or blocked embeds must not be described as a missing movie. Parent web JavaScript cannot read or alter the cross-origin iframe's results. No provider key is copied or reused by Vert TV, no catalogue is imported, and no playback check or external video was initiated for this change.

Queries are trimmed and limited to 120 characters; URL/URI builders encode them to keep ampersands, hashes and non-ASCII names within the `q` parameter. Existing iframe popup/top-navigation restrictions, playback controls, payment gating and recovery settings remain. Search controls appear only inside the active library. Actual Dulo media availability remains provider-dependent.
