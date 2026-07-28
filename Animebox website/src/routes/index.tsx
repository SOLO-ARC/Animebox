import { createFileRoute } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useState, useEffect } from "react";
import { Sparkles, Shield, Wifi, Languages, ListVideo } from "lucide-react";
import appScreenshot from "@/assets/app-screenshot.jpg.asset.json";

const ANILIST_QUERY = `
  query {
    tv: Page(page: 1, perPage: 12) {
      media(type: ANIME, format_in: [TV, TV_SHORT, ONA], sort: POPULARITY_DESC, isAdult: false) {
        id
        coverImage { large }
      }
    }
    movies: Page(page: 1, perPage: 12) {
      media(type: ANIME, format: MOVIE, sort: POPULARITY_DESC, isAdult: false) {
        id
        coverImage { large }
      }
    }
  }
`;

const CACHE_KEY = "anilist_trending_covers_v1";

function getCachedCovers(): string[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch (e) {
    // Ignore cache read errors
  }
  return [];
}

async function fetchTrending(): Promise<string[]> {
  const res = await fetch("https://graphql.anilist.co", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ query: ANILIST_QUERY }),
  });
  const json = await res.json();
  const tvCovers = (json?.data?.tv?.media ?? [])
    .map((a: { coverImage?: { large?: string } }) => a?.coverImage?.large)
    .filter(Boolean);
  const movieCovers = (json?.data?.movies?.media ?? [])
    .map((a: { coverImage?: { large?: string } }) => a?.coverImage?.large)
    .filter(Boolean);

  const mixed: string[] = [];
  const max = Math.max(tvCovers.length, movieCovers.length);
  for (let i = 0; i < max; i++) {
    if (tvCovers[i]) mixed.push(tvCovers[i]);
    if (movieCovers[i]) mixed.push(movieCovers[i]);
  }

  if (mixed.length > 0 && typeof window !== "undefined") {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(mixed));
    } catch (e) {
      // Ignore cache write errors
    }
  }

  return mixed;
}

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "AnimeBox" },
      {
        name: "description",
        content:
          "Download AnimeBox for Android. Stream trending anime, build your watchlist, and enjoy multi-audio playback.",
      },
      { property: "og:title", content: "AnimeBox" },
      {
        property: "og:description",
        content: "Trending anime, watchlists, and simulcasts on Android.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: DownloadPage,
});

function AndroidIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden="true">
      <path d="M17.6 9.48l1.84-3.18a.4.4 0 0 0-.69-.4l-1.86 3.22a11.5 11.5 0 0 0-9.78 0L5.25 5.9a.4.4 0 1 0-.69.4L6.4 9.48A10.8 10.8 0 0 0 1 18h22a10.8 10.8 0 0 0-5.4-8.52zM7 15.25a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5zm10 0a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5z" />
    </svg>
  );
}

function DownloadPage() {
  const [cachedCovers, setCachedCovers] = useState<string[]>([]);

  useEffect(() => {
    setCachedCovers(getCachedCovers());
  }, []);

  const { data: fetchedCovers } = useQuery({
    queryKey: ["anilist-trending"],
    queryFn: fetchTrending,
    staleTime: 1000 * 60 * 60 * 2, // 2 hours stale time
    gcTime: 1000 * 60 * 60 * 24, // 24 hours retention
  });

  const covers = fetchedCovers && fetchedCovers.length > 0 ? fetchedCovers : cachedCovers;

  const COL_COUNT = 6;
  const columns: string[][] = Array.from({ length: COL_COUNT }, () => []);
  const source = covers.length > 0 ? covers : Array(24).fill("");
  source.forEach((src, i) => columns[i % COL_COUNT].push(src));

  return (
    <main className="relative min-h-screen overflow-hidden bg-black text-white">
      {/* Moving colorful poster collage */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden opacity-40">
        <div className="absolute inset-0 flex gap-3 p-3 sm:gap-4 sm:p-4">
          {columns.map((col, ci) => {
            const doubled = [...col, ...col];
            const direction = ci % 2 === 0 ? "marquee-up" : "marquee-down";
            const duration = 45 + (ci % 3) * 15;
            return (
              <div
                key={ci}
                className="relative flex-1 overflow-hidden"
                style={{ minWidth: 0 }}
              >
                <div
                  className={direction}
                  style={{ animationDuration: `${duration}s` }}
                >
                  <div className="flex flex-col gap-3 sm:gap-4">
                    {doubled.map((src, ri) => (
                      <div
                        key={`${ci}-${ri}`}
                        className="relative aspect-[2/3] w-full overflow-hidden rounded-xl bg-neutral-900 shadow-lg shadow-black/50"
                      >
                        {src ? (
                          <img
                            src={src}
                            alt=""
                            loading="eager"
                            className="h-full w-full object-cover transition-opacity duration-300"
                          />
                        ) : (
                          <div className="h-full w-full animate-pulse bg-neutral-800" />
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Top vignette */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-64 bg-gradient-to-b from-black via-black/85 to-transparent" />
      {/* Middle spotlight for content */}
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_center,_rgba(0,0,0,0.92)_0%,_rgba(0,0,0,0.75)_40%,_rgba(0,0,0,0.4)_70%,_transparent_100%)]" />
      {/* Bottom shadow */}
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-[60vh] bg-gradient-to-t from-black via-black/95 to-transparent" />

      {/* Header */}
      <header className="relative z-10 flex items-center justify-between px-6 py-4 sm:px-10 sm:py-6">
        <div className="flex items-center gap-2 font-bold text-xl tracking-tight text-white">
          <span>Anime<span className="text-purple-300">Box</span></span>
        </div>
        <nav className="flex gap-6 text-sm text-white/70 sm:gap-8">
          <a href="#features" className="transition hover:text-white">Features</a>
          <a href="#showcase" className="transition hover:text-white">Showcase</a>
          <a href="#download" className="transition hover:text-white">Download</a>
        </nav>
      </header>

      {/* Hero Section */}
      <section className="relative z-10 mx-auto flex min-h-[calc(100vh-80px)] max-w-4xl flex-col items-center justify-center px-6 py-8 text-center sm:py-12">
        <h1 className="text-4xl font-extrabold tracking-tight sm:text-6xl md:text-7xl">
          Just press play.
          <br />
          <span className="text-white/50">Anime, made simple.</span>
        </h1>

        <p className="mt-4 max-w-xl text-base text-white/70 sm:mt-6 sm:text-lg">
          Your anime, your language, your pace.
        </p>

        {/* Download button */}
        <div id="download" className="relative mt-8 sm:mt-10 group">
          <div className="absolute -inset-4 rounded-full bg-white/20 blur-2xl transition group-hover:bg-white/35" />
          <a
            href="https://www.dropbox.com/scl/fi/huu7ud4sqk635lhnfohxd/app-prerelease-debug.apk?rlkey=dczqw7z4psd5wg5tu1lmn0npm&st=8y2x8qm5&dl=0"
            target="_blank"
            rel="noopener noreferrer"
            className="relative inline-flex items-center gap-4 rounded-full bg-white px-8 py-4 text-black shadow-[0_20px_60px_-10px_rgba(255,255,255,0.4)] transition hover:scale-[1.03] active:scale-100 sm:px-10 sm:py-5"
          >
            <AndroidIcon className="h-7 w-7 sm:h-8 sm:w-8" />
            <span className="flex flex-col items-start leading-tight">
              <span className="text-[11px] font-medium uppercase tracking-widest text-black/60 sm:text-xs">
                Download for
              </span>
              <span className="text-lg font-semibold sm:text-2xl">Android APK</span>
            </span>
          </a>
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-white/50">
          <span>APK · 98 MB</span>
          <span className="h-1 w-1 rounded-full bg-white/30" />
          <span>Android 8.0+</span>
        </div>
      </section>

      {/* App Showcase */}
      <section
        id="showcase"
        className="relative z-10 mx-auto max-w-6xl px-6 pt-20 pb-24 border-t border-white/10 sm:pt-28"
      >
        <div className="mb-10 text-center">
          <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
            Built for how you actually watch.
          </h2>
          <p className="mt-3 text-sm text-white/60 sm:text-base">
            Pick your audio, jump between episodes, and get back to the show.
          </p>
        </div>

        {/* Horizontal phone mockup */}
        <div className="relative mx-auto w-full max-w-4xl">
          <div className="absolute -inset-8 rounded-[3rem] bg-white/5 blur-3xl" />
          <div className="relative rounded-[2.25rem] border border-white/15 bg-neutral-950 p-3 shadow-[0_40px_120px_-20px_rgba(0,0,0,0.9)]">
            {/* Landscape phone frame */}
            <div className="relative overflow-hidden rounded-[1.75rem] border border-white/10 bg-black">
              {/* Notch / camera on the side */}
              <div className="pointer-events-none absolute left-3 top-1/2 z-20 -translate-y-1/2">
                <div className="h-16 w-2 rounded-full bg-white/10" />
              </div>
              <div className="pointer-events-none absolute right-3 top-1/2 z-20 -translate-y-1/2 flex flex-col gap-2">
                <div className="h-2 w-2 rounded-full bg-white/20" />
                <div className="h-2 w-2 rounded-full bg-white/10" />
              </div>
              <img
                src="/photo_2026-07-27_05-42-31.jpg"
                alt="AnimeBox app showcase"
                className="block h-auto w-full"
                loading="lazy"
              />
            </div>
          </div>

          {/* Captions under mockup */}
          <div className="mt-8 grid grid-cols-1 gap-4 text-sm text-white/60 sm:grid-cols-3">
            <div className="flex items-start gap-3">
              <Languages className="mt-0.5 h-4 w-4 shrink-0 text-white" />
              <span>Japanese, English and Hindi audio — switch anytime.</span>
            </div>
            <div className="flex items-start gap-3">
              <ListVideo className="mt-0.5 h-4 w-4 shrink-0 text-white" />
              <span>Episode selector right inside the player.</span>
            </div>
            <div className="flex items-start gap-3">
              <Sparkles className="mt-0.5 h-4 w-4 shrink-0 text-white" />
              <span>Clean player UI that stays out of the way.</span>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section
        id="features"
        className="relative z-10 mx-auto max-w-5xl px-6 pb-24"
      >
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            {
              icon: Wifi,
              title: "Fast playback",
              body: "Streams start quickly and keep up on mobile data.",
            },
            {
              icon: Shield,
              title: "No ads",
              body: "No banners, no pre-rolls. Just the episode.",
            },
            {
              icon: Languages,
              title: "Multi-audio",
              body: "Japanese, English and Hindi tracks on supported titles.",
            },
          ].map(({ icon: Icon, title, body }) => (
            <div
              key={title}
              className="rounded-2xl border border-white/10 bg-white/[0.03] p-6 backdrop-blur-md"
            >
              <Icon className="h-5 w-5 text-white" />
              <h3 className="mt-4 text-base font-semibold">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-white/60">{body}</p>
            </div>
          ))}
        </div>

        <footer className="mt-16 flex flex-col items-center gap-2 text-xs text-white/40">
          <div>© {new Date().getFullYear()} AnimeBox</div>
        </footer>
      </section>
    </main>
  );
}
