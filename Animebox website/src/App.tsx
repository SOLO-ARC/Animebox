import { useState, useEffect } from "react";
import { 
  Sparkles, 
  Shield, 
  Zap, 
  Languages, 
  ListVideo, 
  Users, 
  Layers, 
  Search, 
  Heart, 
  Github, 
  ExternalLink,
  Play
} from "lucide-react";

const ANILIST_QUERY = `
  query {
    tv: Page(page: 1, perPage: 24) {
      media(type: ANIME, format_in: [TV, TV_SHORT, ONA], sort: POPULARITY_DESC, isAdult: false) {
        id
        title { romaji english }
        coverImage { extraLarge large }
      }
    }
    movies: Page(page: 1, perPage: 20) {
      media(type: ANIME, format: MOVIE, sort: POPULARITY_DESC, isAdult: false) {
        id
        title { romaji english }
        coverImage { extraLarge large }
      }
    }
  }
`;

const DEFAULT_COVERS = [
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21519-CLbDvwR4TGRB.png", // Your Name
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-YCDoj1EkAxL8.png", // One Punch Man
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20954-UMjLWY3HX54a.jpg", // A Silent Voice
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-PEn1rB905jqc.jpg", // Demon Slayer
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx105333-eW02jL1yTfl9.jpg", // Weathering With You
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-ffl2T3D3w2eG.jpg", // Jujutsu Kaisen
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx199-m5ZWy5xFyqSC.jpg", // Spirited Away
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-m5ZWy5xFyqSC.jpg", // Attack on Titan
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx131573-0wS307vR4JgX.jpg", // Suzume
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx11061-N5EWBZSQAawL.jpg", // Hunter x Hunter
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx128893-nFlSjL9SszA0.jpg", // Jujutsu Kaisen 0
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20464-6BG40F3mBofF.jpg", // Haikyuu!!
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20605-t6oP7c6WbXsp.jpg", // Tokyo Ghoul
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-LHn2ptFZF18W.jpg", // Chainsaw Man
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101291-729r7UfaERpT.jpg", // Bunny Girl Senpai
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx1535-4rLyJ62ChA2T.jpg", // Death Note
];

function AndroidIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden="true">
      <path d="M17.6 9.48l1.84-3.18a.4.4 0 0 0-.69-.4l-1.86 3.22a11.5 11.5 0 0 0-9.78 0L5.25 5.9a.4.4 0 1 0-.69.4L6.4 9.48A10.8 10.8 0 0 0 1 18h22a10.8 10.8 0 0 0-5.4-8.52zM7 15.25a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5zm10 0a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5z" />
    </svg>
  );
}

function preloadAll(urls: string[]): Promise<void> {
  return new Promise((resolve) => {
    if (!urls.length) return resolve();
    let done = 0;
    const finish = () => {
      done += 1;
      if (done >= urls.length) resolve();
    };
    urls.forEach((src) => {
      const img = new Image();
      img.onload = finish;
      img.onerror = finish;
      img.src = src;
    });
  });
}

export default function App() {
  const downloadUrl = "https://github.com/SOLO-ARC/Animebox/releases/latest";
  const [covers, setCovers] = useState<string[]>(DEFAULT_COVERS);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    fetch("https://graphql.anilist.co", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ query: ANILIST_QUERY }),
    })
      .then((res) => res.json())
      .then((json) => {
        const tv = json?.data?.tv?.media ?? [];
        const movies = json?.data?.movies?.media ?? [];
        const mixed: string[] = [];
        const max = Math.max(tv.length, movies.length);
        for (let i = 0; i < max; i++) {
          if (tv[i]?.coverImage?.large) mixed.push(tv[i].coverImage.large);
          if (movies[i]?.coverImage?.large) mixed.push(movies[i].coverImage.large);
        }
        if (mixed.length >= 12) {
          setCovers(mixed);
        }
      })
      .catch((err) => console.error("AniList fetch error:", err));
  }, []);

  useEffect(() => {
    if (!covers.length) return;
    let cancelled = false;
    preloadAll(covers).then(() => {
      if (!cancelled) setReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, [covers.length]);

  const COL_COUNT = 6;
  const columns: string[][] = Array.from({ length: COL_COUNT }, () => []);
  const source = ready && covers.length ? covers : Array(36).fill("");
  source.forEach((src, i) => columns[i % COL_COUNT].push(src));

  return (
    <main className="relative min-h-screen overflow-hidden bg-black text-white font-sans">
      
      {/* Moving colorful poster collage */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden opacity-75">
        <div className="absolute inset-0 flex gap-3 p-3 sm:gap-4 sm:p-4 -rotate-2 scale-105">
          {columns.map((col, ci) => {
            const doubled = [...col, ...col];
            const direction = ci % 2 === 0 ? "marquee-up" : "marquee-down";
            const duration = 50 + (ci % 3) * 20;
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
                        className="relative aspect-[2/3] w-full overflow-hidden rounded-xl bg-neutral-900 shadow-lg shadow-black/50 border border-white/10"
                      >
                        {src ? (
                          <img
                            src={src}
                            alt="Anime Poster"
                            loading="lazy"
                            className="h-full w-full object-cover filter brightness-95 contrast-105"
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
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_center,_rgba(0,0,0,0.92)_0%,_rgba(0,0,0,0.70)_35%,_rgba(0,0,0,0.30)_60%,_transparent_88%)]" />
      {/* Bottom shadow */}
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-[60vh] bg-gradient-to-t from-black via-black/95 to-transparent" />

      {/* Header featuring actual AnimeBox logo */}
      <header className="relative z-10 flex items-center justify-between px-6 py-6 sm:px-10 max-w-7xl mx-auto">
        <div className="flex items-center gap-3">
          <img 
            src="./logo.png" 
            alt="AnimeBox Logo" 
            className="w-10 h-10 object-contain drop-shadow-md" 
          />
          <span className="font-bold text-xl tracking-tight text-white">
            AnimeBox
          </span>
        </div>
        <nav className="hidden items-center gap-8 text-sm font-medium text-white/70 md:flex">
          <a href="#features" className="transition hover:text-white">Features</a>
          <a href="#showcase" className="transition hover:text-white">Showcase</a>
          <a href="#why" className="transition hover:text-white">Why AnimeBox</a>
          <a href="#download" className="transition hover:text-white">Download</a>
        </nav>
      </header>

      {/* Hero Section */}
      <section className="relative z-10 mx-auto flex max-w-4xl flex-col items-center px-6 pt-16 pb-28 text-center sm:pt-24">
        <h1 className="text-5xl font-bold tracking-tight sm:text-6xl md:text-7xl leading-tight">
          Just press play.
          <br />
          <span className="text-white/50">Anime, made simple.</span>
        </h1>

        <p className="mt-6 max-w-xl text-base text-white/60 sm:text-lg">
          Your anime, your language, your pace.
        </p>

        {/* Download Button */}
        <div id="download" className="relative mt-12 group">
          <div className="absolute -inset-4 rounded-full bg-white/20 blur-2xl transition group-hover:bg-white/30" />
          <a
            href={downloadUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="relative inline-flex items-center gap-4 rounded-full bg-white px-8 py-5 text-black shadow-[0_20px_60px_-10px_rgba(255,255,255,0.4)] transition hover:scale-[1.02] active:scale-100 sm:px-10 sm:py-6"
          >
            <AndroidIcon className="h-8 w-8" />
            <span className="flex flex-col items-start leading-tight">
              <span className="text-xs font-medium uppercase tracking-widest text-black/60">
                Download for
              </span>
              <span className="text-xl font-semibold sm:text-2xl">Android APK</span>
            </span>
          </a>
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-white/50">
          <span>APK · 98 MB</span>
          <span className="h-1 w-1 rounded-full bg-white/30" />
          <span>Android 8.0+</span>
        </div>
      </section>

      {/* App Showcase Section */}
      <section
        id="showcase"
        className="relative z-10 mx-auto max-w-6xl px-6 pb-24"
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
              {/* Notch / camera controls */}
              <div className="pointer-events-none absolute left-3 top-1/2 z-20 -translate-y-1/2">
                <div className="h-16 w-2 rounded-full bg-white/10" />
              </div>
              <div className="pointer-events-none absolute right-3 top-1/2 z-20 -translate-y-1/2 flex flex-col gap-2">
                <div className="h-2 w-2 rounded-full bg-white/20" />
                <div className="h-2 w-2 rounded-full bg-white/10" />
              </div>
              <img
                src="./showcase/photo_2026-07-29_03-09-17.jpg"
                alt="AnimeBox app player screen showing Jujutsu Kaisen with quality, subtitle, and episode controls"
                className="block h-auto w-full"
                loading="lazy"
              />
            </div>
          </div>

          {/* Small captions under the mockup */}
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

      {/* Main Features Grid */}
      <section
        id="features"
        className="relative z-10 mx-auto max-w-5xl px-6 pb-24"
      >
        <div className="text-center mb-10">
          <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">Features You'll Love</h2>
          <p className="mt-2 text-sm text-white/60">Designed for fast playback, ease of use, and multi-profile support.</p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            {
              icon: Languages,
              title: "Multi-Audio Support",
              body: "Japanese (Sub), English (Dub), and Hindi audio tracks on supported titles.",
            },
            {
              icon: Users,
              title: "Multiple Profiles",
              body: "Create separate user profiles, including dedicated kids profiles.",
            },
            {
              icon: Play,
              title: "Custom Video Player",
              body: "Volume/brightness gestures, skip intro/outro buttons, and subtitle controls.",
            },
            {
              icon: Search,
              title: "Search & Discovery",
              body: "Filter through anime series by genre, tags, and popularity.",
            },
            {
              icon: Layers,
              title: "Backup & Restore",
              body: "Export profile data and app settings to a JSON file anytime.",
            },
            {
              icon: Shield,
              title: "Personal Watchlist",
              body: "Track watch history and save titles to your watchlist.",
            },
          ].map(({ icon: Icon, title, body }) => (
            <div
              key={title}
              className="rounded-2xl border border-white/10 bg-white/[0.03] p-6 backdrop-blur-md hover:border-white/20 transition"
            >
              <Icon className="h-5 w-5 text-white" />
              <h3 className="mt-4 text-base font-semibold">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-white/60">{body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Why AnimeBox Exists Section */}
      <section
        id="why"
        className="relative z-10 mx-auto max-w-4xl px-6 pb-24"
      >
        <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-8 backdrop-blur-md sm:p-10">
          <div className="flex items-center gap-3 text-purple-400 mb-4">
            <Heart className="h-5 w-5" />
            <h2 className="text-xl font-bold tracking-tight text-white">Why AnimeBox Exists</h2>
          </div>

          <p className="text-sm text-white/70 leading-relaxed">
            Mainstream anime streaming platforms like Crunchyroll frequently remove anime titles from their catalog, feature dated user interfaces, and deliver lower video playback options. On the other hand, platforms like Netflix offer a polished UI, but their anime collections are heavily region-locked or limited in selection.
          </p>

          <p className="text-sm text-white/70 leading-relaxed mt-4">
            AnimeBox was created to solve these interface and accessibility hurdles with a clean, user-focused mobile experience.
          </p>

          <div className="mt-6 p-4 rounded-xl bg-neutral-900/90 border border-white/10 text-xs text-white/60 leading-relaxed">
            <strong className="text-white block mb-1">Important Notice:</strong>
            This repository and source code <span className="text-white font-semibold">do not host any streaming sources or video content</span>. 
            For streaming, API endpoints can be configured locally or linked directly to official platforms like Crunchyroll, Netflix, or TMDB/AniList metadata.
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="relative z-10 border-t border-white/10 bg-black/80 py-12 text-xs text-white/40">
        <div className="max-w-6xl mx-auto px-6 flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-3">
            <img src="./logo.png" alt="AnimeBox Logo" className="w-8 h-8 object-contain" />
            <span className="font-bold text-sm text-white">AnimeBox</span>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-6 text-white/60">
            <a href="#features" className="hover:text-white transition">Features</a>
            <a href="#showcase" className="hover:text-white transition">Showcase</a>
            <a href="#why" className="hover:text-white transition">Why AnimeBox</a>
            <a href={downloadUrl} target="_blank" rel="noopener noreferrer" className="hover:text-white transition">Download APK</a>
          </div>

          <div className="flex items-center gap-4 text-white/40">
            <span>© {new Date().getFullYear()} AnimeBox</span>
            <span>·</span>
            <a 
              href="https://github.com/SOLO-ARC/Animebox" 
              target="_blank" 
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 hover:text-white transition"
            >
              <Github className="h-3.5 w-3.5" />
              <span>GitHub</span>
            </a>
          </div>
        </div>
      </footer>

    </main>
  );
}
