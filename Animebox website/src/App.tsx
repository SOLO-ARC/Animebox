import { useState, useEffect } from "react";
import { 
  Sparkles, 
  ShieldCheck, 
  Users, 
  PlayCircle, 
  Database, 
  ExternalLink,
  Sliders,
  Layers,
  Heart,
  Wifi,
  Languages,
  CheckCircle2
} from "lucide-react";

// Default curated anime covers matching the reference screenshot layout
const DEFAULT_COVERS = [
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-YCDoj1EkAxL8.png", // One Punch Man
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101291-729r7UfaERpT.jpg", // Bunny Girl Senpai
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21459-Ro2qTzA6M0vB.jpg", // My Hero Academia
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx11061-N5EWBZSQAawL.jpg", // Hunter x Hunter
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20464-6BG40F3mBofF.jpg", // Haikyuu!!
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-PEn1rB905jqc.jpg", // Demon Slayer
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20954-UMjLWY3HX54a.jpg", // Silent Voice
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20605-t6oP7c6WbXsp.jpg", // Tokyo Ghoul
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-LHn2ptFZF18W.jpg", // Chainsaw Man
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-m5ZWy5xFyqSC.jpg", // Attack on Titan
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-ffl2T3D3w2eG.jpg", // Jujutsu Kaisen
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx1535-4rLyJ62ChA2T.jpg", // Death Note
];

function AndroidIcon({ className = "w-6 h-6" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5516 0 .9997.4482.9997.9993 0 .5511-.4481.9997-.9997.9997zm-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5516 0 .9997.4482.9997.9993 0 .5511-.4481.9997-.9997.9997zm11.4045-6.02l1.9973-3.4592a.416.416 0 0 0-.1523-.5676.416.416 0 0 0-.5676.1523l-2.0223 3.503C15.5902 8.3093 13.8567 7.973 12 7.973c-1.8567 0-3.5902.3363-5.1326.9769L4.8451 5.4469a.416.416 0 0 0-.5676-.1523.416.416 0 0 0-.1523.5676l1.9973 3.4592C2.6889 11.0506.4121 14.197.0264 17.973h23.9472c-.3857-3.776-2.6625-6.9224-6.0918-8.6516z"/>
    </svg>
  );
}

export default function App() {
  const downloadUrl = "https://github.com/SOLO-ARC/Animebox/releases/latest";
  const [covers, setCovers] = useState<string[]>(DEFAULT_COVERS);

  // Dynamically fetch top trending anime covers from AniList GraphQL API
  useEffect(() => {
    const query = `
      query {
        Page(page: 1, perPage: 16) {
          media(sort: POPULARITY_DESC, type: ANIME) {
            coverImage {
              extraLarge
              large
            }
          }
        }
      }
    `;

    fetch("https://graphql.anilist.co", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify({ query }),
    })
      .then((res) => res.json())
      .then((data) => {
        const fetchedCovers = data?.data?.Page?.media
          ?.map((m: any) => m.coverImage?.extraLarge || m.coverImage?.large)
          ?.filter(Boolean);
        if (fetchedCovers && fetchedCovers.length >= 8) {
          setCovers(fetchedCovers);
        }
      })
      .catch((err) => console.error("AniList fetch error:", err));
  }, []);

  return (
    <div className="min-h-screen bg-[#09090b] text-white selection:bg-purple-500 selection:text-white font-sans relative overflow-x-hidden">
      
      {/* Background Poster Cards Grid with Dark Overlay */}
      <div className="absolute inset-0 z-0 overflow-hidden opacity-35 select-none pointer-events-none">
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-4 p-4 -rotate-3 scale-110 transform-gpu">
          {covers.concat(covers).map((coverUrl, idx) => (
            <div 
              key={idx} 
              className="aspect-[2/3] rounded-3xl overflow-hidden shadow-2xl border border-white/10 bg-white/5 transition-transform duration-700 hover:scale-105"
            >
              <img 
                src={coverUrl} 
                alt="Anime Poster" 
                className="w-full h-full object-cover filter brightness-90 contrast-105" 
                loading="lazy"
              />
            </div>
          ))}
        </div>
        {/* Dark radial gradient overlay for high contrast text readability */}
        <div className="absolute inset-0 bg-gradient-to-t from-[#09090b] via-[#09090b]/75 to-[#09090b]/80 backdrop-blur-[1px]" />
      </div>

      {/* Top Header Navigation */}
      <nav className="relative z-20 border-b border-white/10 bg-black/40 backdrop-blur-md sticky top-0">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img 
              src="./photo_2026-07-27_05-42-31.jpg" 
              alt="AnimeBox Logo" 
              className="w-8 h-8 rounded-xl border border-white/15 object-cover shadow-lg" 
            />
            <span className="font-bold text-lg tracking-tight text-white">AnimeBox</span>
          </div>

          <div className="flex items-center gap-6 text-sm font-medium text-white/70">
            <a href="#features" className="hover:text-white transition">Features</a>
            <a href="#showcase" className="hover:text-white transition">Showcase</a>
            <a href="#download" className="hover:text-white transition">Download</a>
          </div>
        </div>
      </nav>

      {/* Hero Section matching Screenshot 1 */}
      <header className="relative z-10 max-w-4xl mx-auto px-6 pt-24 pb-20 text-center flex flex-col items-center justify-center min-h-[75vh]">
        
        <h1 className="text-4xl sm:text-6xl md:text-7xl font-black tracking-tight leading-[1.1] drop-shadow-2xl">
          Just press play.
          <br />
          <span className="text-white/60 font-semibold">Anime, made simple.</span>
        </h1>

        <p className="mt-6 text-base sm:text-xl text-white/80 font-normal max-w-xl drop-shadow-md">
          Your anime, your language, your pace.
        </p>

        {/* Hero Download Button matching Screenshot 1 */}
        <div id="download" className="relative mt-10 group z-20">
          <div className="absolute -inset-4 rounded-full bg-white/25 blur-2xl transition group-hover:bg-white/40" />
          <a
            href={downloadUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="relative inline-flex items-center gap-4 rounded-full bg-white px-8 py-4 sm:px-10 sm:py-5 text-black shadow-[0_20px_60px_-10px_rgba(255,255,255,0.4)] transition hover:scale-[1.03] active:scale-100"
          >
            <AndroidIcon className="h-7 w-7 sm:h-8 sm:w-8 text-black" />
            <span className="flex flex-col items-start leading-tight">
              <span className="text-[10px] sm:text-xs font-semibold uppercase tracking-widest text-black/60">
                Download for
              </span>
              <span className="text-lg sm:text-2xl font-bold">Android APK</span>
            </span>
          </a>
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs text-white/60 font-medium">
          <span>APK · 98 MB</span>
          <span className="h-1 w-1 rounded-full bg-white/40" />
          <span>Android 8.0+</span>
        </div>
      </header>

      {/* App Showcase Section matching Screenshot 2 */}
      <section
        id="showcase"
        className="relative z-10 max-w-6xl mx-auto px-6 pt-16 pb-24 border-t border-white/10"
      >
        <div className="mb-12 text-center">
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight">
            Built for how you actually watch.
          </h2>
          <p className="mt-3 text-sm sm:text-base text-white/60">
            Pick your audio, jump between episodes, and get back to the show.
          </p>
        </div>

        {/* Horizontal Phone Mockup displaying Jujutsu Kaisen Player Screenshot */}
        <div className="relative mx-auto w-full max-w-4xl">
          <div className="absolute -inset-8 rounded-[3rem] bg-purple-600/10 blur-3xl" />
          
          <div className="relative rounded-[2.5rem] border-[10px] border-[#18181b] bg-black p-2 shadow-2xl overflow-hidden group">
            <img
              src="./showcase/photo_2026-07-29_03-09-17.jpg"
              alt="AnimeBox Video Player UI"
              className="w-full h-auto rounded-[1.8rem] object-cover transition-transform duration-500 group-hover:scale-[1.01]"
            />
          </div>
        </div>

        {/* Showcase Feature Chips */}
        <div className="mt-10 flex flex-wrap items-center justify-center gap-4 text-xs sm:text-sm text-white/70">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-white/10 bg-white/5 backdrop-blur-md">
            <Languages className="w-4 h-4 text-purple-400" />
            <span>Japanese, English and Hindi audio — switch anytime.</span>
          </div>
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-white/10 bg-white/5 backdrop-blur-md">
            <PlayCircle className="w-4 h-4 text-purple-400" />
            <span>Episode selector right inside the player.</span>
          </div>
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-white/10 bg-white/5 backdrop-blur-md">
            <Sparkles className="w-4 h-4 text-purple-400" />
            <span>Clean player UI that stays out of the way.</span>
          </div>
        </div>
      </section>

      {/* Main Features Grid */}
      <section id="features" className="relative z-10 max-w-5xl mx-auto px-6 py-20 border-t border-white/10">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-bold tracking-tight">Features You'll Love</h2>
          <p className="text-sm text-white/60 mt-2">Designed from the ground up for performance and comfort.</p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          <div className="p-6 rounded-3xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Database className="w-6 h-6 text-purple-400 mb-4" />
            <h3 className="text-base font-semibold">AniList Data Integration</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Fetches anime metadata, cover art, tags, genres, and community ratings directly from AniList.
            </p>
          </div>

          <div className="p-6 rounded-3xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Users className="w-6 h-6 text-pink-400 mb-4" />
            <h3 className="text-base font-semibold">Multiple Profiles</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Support for creating and managing separate user profiles, including dedicated kids profiles.
            </p>
          </div>

          <div className="p-6 rounded-3xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Sliders className="w-6 h-6 text-blue-400 mb-4" />
            <h3 className="text-base font-semibold">Custom Video Player</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Built-in video player with volume/brightness gestures, skip intro/outro options, and subtitle controls.
            </p>
          </div>

          <div className="p-6 rounded-3xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Wifi className="w-6 h-6 text-indigo-400 mb-4" />
            <h3 className="text-base font-semibold">Search & Discovery</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Search through anime series with category and tag filters.
            </p>
          </div>

          <div className="p-6 rounded-3xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Layers className="w-6 h-6 text-emerald-400 mb-4" />
            <h3 className="text-base font-semibold">Backup & Restore</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Export profile data and app settings to a JSON file and import them back anytime.
            </p>
          </div>

          <div className="p-6 rounded-3xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <ShieldCheck className="w-6 h-6 text-amber-400 mb-4" />
            <h3 className="text-base font-semibold">Personal Watchlist</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Per-profile anime saving and watch history tracking.
            </p>
          </div>
        </div>
      </section>

      {/* Why AnimeBox Section */}
      <section id="why" className="relative z-10 max-w-4xl mx-auto px-6 py-16 border-t border-white/10">
        <div className="p-8 sm:p-10 rounded-3xl border border-white/10 bg-white/[0.02] backdrop-blur-md">
          <div className="flex items-center gap-3 text-purple-400 mb-4">
            <Heart className="w-5 h-5" />
            <h2 className="text-xl font-bold tracking-tight text-white">Why AnimeBox Exists</h2>
          </div>

          <p className="text-sm text-white/70 leading-relaxed">
            Many mainstream anime streaming services often fall short for dedicated fans. Platforms like Crunchyroll frequently remove anime titles from their catalog, offer dated user interfaces, and sometimes deliver lower video resolutions or basic subtitle options compared to modern media standards. On the other hand, platforms like Netflix offer a polished UI and high playback quality, but their anime collections are heavily region-locked, limited in size, or completely unavailable in many countries.
          </p>

          <p className="text-sm text-white/70 leading-relaxed mt-4">
            AnimeBox was created to solve these interface and accessibility issues by providing a smooth, user-focused mobile experience.
          </p>

          <div className="mt-6 p-4 rounded-2xl bg-black/50 border border-white/10 text-xs text-white/60 leading-relaxed">
            <strong className="text-white block mb-1">Important Notice:</strong>
            This repository and source code <span className="text-white font-semibold">do not contain or host any streaming sources or copyrighted video content</span>. 
            For streaming, API endpoints can be configured locally or linked directly to official legal streaming services like Crunchyroll, Netflix, or local providers using AniList/TMDB metadata.
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="relative z-10 border-t border-white/10 py-8 text-center text-xs text-white/40">
        <div className="max-w-4xl mx-auto px-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>© {new Date().getFullYear()} AnimeBox</div>
          <div className="flex items-center gap-6">
            <a href={downloadUrl} target="_blank" rel="noopener noreferrer" className="hover:text-white transition">Download APK</a>
            <a href="https://github.com/SOLO-ARC/Animebox" target="_blank" rel="noopener noreferrer" className="hover:text-white transition">GitHub Source</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
