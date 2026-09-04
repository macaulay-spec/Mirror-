# MovieBox API (ZST LABS) — Full Research Report + Build Prompt

**Base URLs (both work identically):** `https://zstlab.cyou` and `https://api.zstlab.cyou`
**Auth:** `x-api-key: <key>` header **or** `?apikey=<key>` query param.
**Rate limit:** `300 requests / 300s` per key (headers: `ratelimit-limit`, `ratelimit-remaining`, `ratelimit-reset`).
**Test key used:** `zst_sxWpWzOBNhkz3ev6wlnZIkShU3PC0NxJ6AVjOIzp`

> Note: the provider is behind Cloudflare and rejects default programmatic
> user-agents. **Always send a normal browser `User-Agent`** on server-side calls.
> All responses are wrapped: `{ status, statusCode, creator, endpoint, ...echoed params, data }`.

---

## 1. Endpoint inventory (all 16 MovieBox-tagged endpoints, all live-tested)

### Group A — Real MovieBox (aoneroom/hakunaymatata) — **this is the group to build on**

| Endpoint | Params | Verified result |
|---|---|---|
| `GET /api/homepage` | none | 200. Returns `topPickList`, `homeList`, `banner`, `live`, `platformList` (Netflix/PrimeVideo/Disney/AppleTV/Viu/Hulu/Zee5/Vivamax/Hoichoi/Showmax), `operatingList` |
| `GET /api/trending` | `page` (default 0), `perPage` | 200. `data.subjectList[]` |
| `GET /api/hot-movies-series` | none | 200. `data.movie[]` and `data.series[]` |
| `GET /api/popular-searches` | none | 200. `data.everyoneSearch[] = [{title}]` |
| `GET /api/search-suggestion` | `query` (req), `per_page` | 200. `data.items[] = [{type, word, subject}]`, `data.keyword` |
| `GET /api/search` | `query` (req), `subjectType` = `ALL\|MOVIE\|TV_SERIES`, `page` (1), `perPage` (24) | 200. `data.pager {hasMore, nextPage, page, perPage, totalCount}` + `data.items[]` |
| `GET /api/item-details` | `subjectId` (req) | 200. `data.subject`, `data.stars[]`, `data.resource`, `data.seasons[]`, `seasonCount`, `isSeries`, `isForbid`, `watchTimeLimit` |
| `GET /api/recommendations` | `subjectId` (req), `page`, `perPage` | 200. `data.items[]` (note: can include the source title itself — filter it out client-side) |
| `GET /api/media` | `subjectId` + `detailPath` (both req), `season`, `episode` (0/0 = movie) | 200. **`data.downloads` + `data.subtitles` + `data.stream`** — the core playback endpoint |

### Group B — `/api/v1/moviebox/*` — **actually TMDB-backed, NOT MovieBox**

Docs claim YTS/TVMaze; the live service returns `"source": "TMDB"`. These are metadata-only — **no playable streams**. Use for posters/backdrops/overviews enrichment only.

| Endpoint | Params | Verified |
|---|---|---|
| `GET /api/v1/moviebox/movies` | `query`, `page`, `limit`, `genre`, `sort_by`, `min_rating` | **200** — TMDB search; `data.movies[]` with `id, title, year, releaseDate, rating, popularity, poster` (`image.tmdb.org/t/p/w500`), `backdrop`, `overview` |
| `GET /api/v1/moviebox/trending` | none | **200** — TMDB Trending (week), 20 items, `totalPages: 500` |
| `GET /api/v1/moviebox/latest` | none | **200** — TMDB "Now Playing" |
| `GET /api/v1/moviebox/tv` | `query` (req) | **200** — TMDB TV search; `data.shows[]` |
| `GET /api/v1/moviebox/tv/:id` | `id` | **200** — `data.show { name, originalName, firstAirDate, lastAirDate, status, rating, voteCount, episodes, seasons, runtime, genres[], overview }`. **⚠ IDs are TMDB IDs, not TVMaze** (`/tv/169` returns "Fernwood 2 Night", not Breaking Bad; Breaking Bad is `1396`) |
| `GET /api/v1/moviebox/movies/:id` | `id` | **502 BROKEN** — do not use |
| `GET /api/v1/moviebox/schedule` | `country`, `date` | **404 NOT IMPLEMENTED** — `Cannot GET /api/v1/moviebox/schedule` |

### Group C — Media transport (undocumented but essential)

| Endpoint | Params | Verified |
|---|---|---|
| `GET /api/proxy` | `url` (URL-encoded) | **200**, `access-control-allow-origin: *`, `access-control-allow-headers: Content-Type, Authorization, Range`, `access-control-expose-headers: Content-Length, Content-Range, Accept-Ranges`. Honors `Range` (returns `content-range: bytes 0-500/3536512`). Supports `HEAD`. **No API key required.** |
| `GET /api/proxy-download` | `url`, `name`, `quality` | **200**, `content-disposition: attachment; filename="gzmovie_mutiny_360p.mp4"`, `access-control-allow-origin: *`. **No API key required.** |

---

## 2. THE CRITICAL PART — CDN vs Proxy

### What the CDN does
Raw media lives on MovieBox's CDN hosts, with short-lived signatures:
- Video: `https://bcdnxw.hakunaymatata.com/{convert-h264|bt|resource}/<hash>.mp4?sign=<md5>&t=<unix_expiry>`
- Subtitles: `https://cacdn.hakunaymatata.com/subtitle/<hash>.srt?Policy=<b64>&Signature=<...>&Key-Pair-Id=KMHN1LQ1HEUPL` (CloudFront signed)
- Trailers: `https://macdn.aoneroom.com/media/vone/.../<hash>-ld.mp4`
- Images/posters: `https://pbcdnw.aoneroom.com/image/...` (open, embed directly)

**Measured behavior of the raw video CDN:**
- Direct `GET` / ranged `GET` from a non-whitelisted client → **`HTTP 429 Too Many Requests`** (nginx HTML body). Reproduced across 10 different URLs / 5 different titles.
- Adding `Referer: https://h5.aoneroom.com/`, `https://moviebox.ng/`, `https://www.moviebox.ph/` → still **429**.
- Alternate hosts: `valiw.hakunaymatata.com` → **403**; `bcdnw.hakunaymatata.com` → **429**; `vcdn.`/`cdn.` → DNS fail.
- **No CORS headers at all** → a browser `<video src>` pointed at the raw URL fails on CORS even when the byte fetch would succeed.
- `t=` is an expiry timestamp: signed URLs are short-lived (minutes/hours). **Never cache them.**

**Conclusion:** the raw CDN URL is *unusable* from a browser and unreliable from a server. It exists only as the value you feed to the proxy.

### What the proxy does
`/api/proxy` is a server-side streaming relay that adds the CDN's required headers, then re-emits the bytes with permissive CORS and full `Range` support. Verified working end-to-end on `macdn.aoneroom.com` (206-equivalent ranged response with correct `content-range`) and on `cacdn` subtitles (returned real SRT text). When the upstream CDN itself is throttling, the proxy surfaces:

```json
HTTP 426  {"status":false,"statusCode":426,"error":"Failed to fetch resource: ","creator":"Godszeal (ZST LABS)"}
```

**`426` = upstream CDN refused (429/403), not your bug.** Adding an API key does not change it. The correct client behavior is: try the next resolution, then the next mirror, then surface a retry.

### Decision rule (bake this into the code)

```
Field `url`            -> RAW signed CDN URL. NEVER give to <video>, <track>, or an <a download>.
Field `streamUrl`      -> proxied, CORS-safe, Range-capable. THIS is the <video> src.
Field `downloadUrl`    -> proxied + Content-Disposition attachment. THIS is the download button href.
```

`data.downloads.data.downloads[]` items already ship `streamUrl` and `downloadUrl` pre-built.
`data.stream.data.streams[]` and `captions[].url` ship **only raw `url`** — you must wrap them yourself:

```ts
const PROXY = "https://api.zstlab.cyou/api/proxy";
const DL    = "https://api.zstlab.cyou/api/proxy-download";

export const toStream = (raw: string) =>
  `${PROXY}?url=${encodeURIComponent(raw)}`;

export const toDownload = (raw: string, name: string, quality: string) =>
  `${DL}?url=${encodeURIComponent(raw)}&name=${encodeURIComponent(name)}&quality=${encodeURIComponent(quality)}`;
```

`encodeURIComponent` is mandatory — the signature contains `&`, `=`, `~`, `_` and `?`; an unencoded pass-through corrupts the signature and yields 426.

### Verified `/api/media` response shape

```jsonc
{
  "status": true, "statusCode": 200, "endpoint": "/api/media",
  "subjectId": "5859976759130620224", "detailPath": "mutiny-E6TlP0RKSY6",
  "season": 0, "episode": 0,
  "data": {
    "downloads": { "code": 0, "message": "ok", "data": {
      "downloads": [{
        "id": "1885075484426257408",
        "url": "https://bcdnxw.hakunaymatata.com/convert-h264/....mp4?sign=...&t=1787650183",  // RAW
        "resolution": 360,                 // number: 360 | 480 | 720 | 1080
        "size": "253697638",               // string bytes
        "streamUrl": "https://api.zstlab.cyou/api/proxy?url=...",          // PLAY THIS
        "downloadUrl": "https://api.zstlab.cyou/api/proxy-download?url=...&name=Mutiny&quality=360p"
      }],
      "captions": [{
        "id": "...", "lan": "en", "lanName": "English",
        "url": "https://cacdn.hakunaymatata.com/subtitle/....srt?Policy=...&Signature=...&Key-Pair-Id=...", // RAW, wrap it
        "size": "62342", "delay": 0
      }],
      "limited": false, "limitedCode": "", "freeNum": 6, "hasResource": true
    }},
    "subtitles": { /* same shape as downloads (mirror; different sign= values) */ },
    "stream":    { "code": 0, "message": "ok", "data": {
      "streams": [{ "format": "MP4", "id": "...", "url": "<RAW>", "resolutions": "360", // NOTE: string, key is plural
                    "size": "63472112", "duration": 1361, "codecName": "h264" }],
      "dash": [], "hls": [],           // empty in every title tested -> progressive MP4 only, no HLS
      "freeNum": 6, "limited": false, "limitedCode": "", "hasResource": true
    }}
  }
}
```

Key gotchas confirmed by testing:
- `downloads[].resolution` is a **number**; `streams[].resolutions` is a **string**. Normalize both.
- `dash` and `hls` were **empty arrays on every title tested** → build a progressive-MP4 player, not hls.js. Keep an `if (hls.length)` branch for future-proofing.
- `duration` only exists on `stream.data.streams[]` (seconds).
- Invalid season/episode → `200` with `hasResource: false` and empty arrays. **Not an error** — render an "unavailable" state.
- `captions` was populated for the movie (18+ languages incl. ar/bn/en/es/in_id) and **empty for the TV episode** → always fall back to `item-details.subject.subtitles` (comma-joined language names) for display, and hide the subtitle menu when captions are empty.
- `freeNum` (6) and `watchTimeLimit` (15) from item-details are MovieBox's own free-quota fields — display-only.

### Movie vs series call flow

```
Movie:   /api/search?subjectType=MOVIE -> item.subjectId + item.detailPath
         /api/media?subjectId=..&detailPath=..&season=0&episode=0

Series:  /api/search?subjectType=TV_SERIES -> subjectId, detailPath
         /api/item-details?subjectId=..
             -> data.seasons[] = [{ se: 1, maxEp: 260,
                                    allEp: "1,2,3,4,...",      // AUTHORITATIVE episode list, gaps exist (e.g. 20 missing)
                                    resolutions: [{resolution:360, epNum:8}, ...] }]
             -> data.seasonCount, data.isSeries
         /api/media?subjectId=..&detailPath=..&season=1&episode=2
```
**Build the episode grid from `allEp` (split on `,`), never from a `1..maxEp` range** — `maxEp` is 260 while `allEp` has gaps.

### `subjectType` enum
`1` = MOVIE, `2` = TV_SERIES (integer in item payloads; the string form `MOVIE`/`TV_SERIES`/`ALL` is for the `subjectType` query param).

### Free extras discovered
- Every image object carries `blurHash`, `avgHueLight`, `avgHueDark`, `width`, `height` → use for blur-up placeholders and per-card dynamic accent theming. This is a huge design win, use it.
- `trailer.videoAddress.url` on `item-details` (`macdn.aoneroom.com`, **verified playable through the proxy**) → autoplay muted trailer on hover / on detail page.
- `stars[]` with `name`, `character`, `avatarUrl`, `detailPath` → cast rail.
- `ops` is an opaque analytics blob — ignore it.

### Error catalogue (all reproduced)
| Code | Body / meaning | Handling |
|---|---|---|
| 401 | `API key required...` / `Invalid API key...` | key missing/wrong |
| 400 | `query parameter is required` / `subjectId and detailPath are required` | validate before calling |
| 426 | `Failed to fetch resource:` (from proxy) | upstream CDN throttled → fall back to another resolution/retry |
| 429 | nginx HTML (from raw CDN) | you called the CDN directly — you must use the proxy |
| 502 | `/api/v1/moviebox/movies/:id` | endpoint broken, avoid |
| 404 | `/api/v1/moviebox/schedule` | not implemented |
| 200 + `hasResource:false` | no media for that season/episode | empty state, not an error |

---

## 3. BUILD PROMPT — paste this to build the app

> **Build "NEXORA" — a premium streaming web app on the ZST LABS MovieBox API.**
>
> ### Backend (TanStack Start server functions — never call the provider from the browser)
> Create `src/lib/moviebox.functions.ts` exposing `createServerFn` wrappers, with all runtime helpers in a separate `src/lib/moviebox.server.ts`:
> `getHomepage`, `getTrending({page,perPage})`, `getHot`, `getPopularSearches`, `getSuggestions({query,perPage})`, `search({query,subjectType,page,perPage})`, `getItemDetails({subjectId})`, `getRecommendations({subjectId,page,perPage})`, `getMedia({subjectId,detailPath,season,episode})`.
> Rules:
> - Store the key as a secret `ZST_API_KEY`; read it **inside** each `.handler()`, never at module scope, never in client code.
> - Send `x-api-key` plus a browser `User-Agent` — Cloudflare blocks bot UAs.
> - Base URL `https://api.zstlab.cyou`. Validate inputs with zod.
> - Normalize every response into clean typed DTOs: `MediaItem { subjectId, subjectType: 'MOVIE'|'TV_SERIES', title, description, releaseDate, year, genres: string[], country, rating: number|null, poster {url,blurHash,avgHueDark,width,height}, detailPath, hasResource, trailerUrl }`, and `PlaybackSource { id, resolution: number, sizeBytes, streamUrl, downloadUrl, format, codec, durationSec }`, `Caption { lang, label, url }`.
> - **Media normalizer is the heart of the app.** Merge `data.downloads.data.downloads` and `data.stream.data.streams` by `id`, dedupe by resolution, sort descending. For any entry lacking `streamUrl`, synthesize it with `https://api.zstlab.cyou/api/proxy?url=${encodeURIComponent(raw)}`; same for `downloadUrl` via `/api/proxy-download?url=..&name=..&quality=..`. Wrap every caption `url` through the proxy too so `<track>` passes CORS. **Never expose or use the raw `url` client-side.**
> - Respect the 300req/300s budget: cache with TanStack Query (`homepage`/`hot`/`popular` 10 min, `search`/`suggestions` 60 s, `item-details` 5 min, **`media` 60 s max — signed URLs expire**). Debounce suggestions 250 ms.
> - Treat `200 + hasResource:false` as an empty state, and proxy `426` as "source busy, try another quality".
> - Ignore `/api/v1/moviebox/movies/:id` (502) and `/schedule` (404). Use `/api/v1/moviebox/*` (TMDB) only as an optional backdrop/overview enrichment layer.
>
> ### Routes
> `/` home (hero banner from `operatingList` BANNER + rails from `SUBJECTS_MOVIE` groups + hot movies/series), `/search`, `/title/$subjectId` (details, cast, seasons/episodes, recommendations), `/watch/$subjectId` (player, `?season=&episode=`), `/downloads`, `/my-list`. Give every route its own `head()` with unique title/description/og tags.
>
> ### Player
> Custom-built over a native `<video>` fed by `streamUrl` (progressive MP4 — `hls`/`dash` are empty upstream, but keep an hls.js branch behind `if (hls.length)`). Requirements: glass control bar that auto-hides, gradient-filled scrubber with buffered-range shading and hover thumbnail time tooltip, quality switcher that preserves `currentTime` across source swaps, `<track>` subtitles from proxied captions with a language chip menu, volume, PiP, fullscreen, keyboard shortcuts (space, ←/→ 10 s, ↑/↓, F, M, C), double-tap-to-seek on mobile with a ripple, resume-from-position in localStorage, and an auto-retry ladder that steps down resolution on a 426/stall.
>
> ### Design — Apple-grade, Web3 fintech energy
> - Dark-first. Near-black charcoal canvas, aurora gradient mesh (deep blue → violet → cyan) drifting slowly behind content, fine film grain overlay, 1px hairline borders, heavy use of `backdrop-filter` glass.
> - Accent: electric cyan → violet gradient, used on primary CTAs, active states, scrubber and focus rings, with a soft neon bloom. **All colors as `oklch` semantic tokens in `src/styles.css`** — zero hardcoded color classes in components. Ship shadcn variants (`variant="hero"`, `variant="glass"`) instead of className overrides.
> - Typography: one tight-tracked geometric display face for titles (large, confident, uppercase hero titles) paired with a clean grotesque for body. Not Inter, not Poppins.
> - Use `blurHash` for blur-up image loading and `avgHueDark` to tint each card's hover glow and each detail page's ambient background — the API gives you these, exploit them.
> - Motion (Motion for React): shared-element `layoutId` transitions from poster card → detail hero → player; spring-based card lift with parallax poster on pointer move; staggered rail reveal on scroll; number/rating counters that count up; magnetic buttons; page transitions that scale-and-fade rather than cut; skeleton shimmer that morphs into content. Springs, never linear easing. Respect `prefers-reduced-motion`.
> - Fully responsive: bottom glass tab bar + snap-scroll rails on mobile, slim icon rail + multi-row grids on desktop, and a `⌘K` command-palette search with live suggestions from `/api/search-suggestion`.
> - Detail page: full-bleed blurred backdrop, muted autoplaying trailer, IMDb rating pill, genre chips, cast rail, season segmented control, episode list built from `allEp`, quality/size table, and a "More Like This" rail from `/api/recommendations` (filter out the source title, it returns itself).
>
> Reference the two supplied mockups for the visual target.

---

## 4. Visual references
- `moviebox-ui-reference-desktop.jpg` — desktop home + hero + rails + quality chips
- `moviebox-ui-reference-mobile.jpg` — mobile home, player with subtitle/quality sheets, series detail with episode list
