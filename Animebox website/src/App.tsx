import { useState, useEffect } from "react";
import { 
  Sparkles, 
  ShieldCheck, 
  Users, 
  PlayCircle, 
  Globe,
  Film,
  Layers,
  Heart,
  Wifi,
  Languages,
  CheckCircle2,
  Sliders,
  Tv,
  Play,
  Download,
  ExternalLink,
  Github
} from "lucide-react";

// Default curated mix of popular anime TV shows and iconic anime movies
const DEFAULT_COVERS = [
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21519-CLbDvwR4TGRB.png", // Your Name (Movie)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-YCDoj1EkAxL8.png", // One Punch Man (TV)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20954-UMjLWY3HX54a.jpg", // A Silent Voice (Movie)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-PEn1rB905jqc.jpg", // Demon Slayer (TV)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx105333-eW02jL1yTfl9.jpg", // Weathering With You (Movie)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-ffl2T3D3w2eG.jpg", // Jujutsu Kaisen (TV)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx199-m5ZWy5xFyqSC.jpg", // Spirited Away (Movie)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-m5ZWy5xFyqSC.jpg", // Attack on Titan (TV)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx131573-0wS307vR4JgX.jpg", // Suzume (Movie)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx11061-N5EWBZSQAawL.jpg", // Hunter x Hunter (TV)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx128893-nFlSjL9SszA0.jpg", // Jujutsu Kaisen 0 (Movie)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20464-6BG40F3mBofF.jpg", // Haikyuu!! (TV)
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20605-t6oP7c6WbXsp.jpg", // Tokyo Ghoul
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-LHn2ptFZF18W.jpg", // Chainsaw Man
  "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101291-729r7UfaERpT.jpg", // Bunny Girl Senpai
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

  // Dynamically fetch a 50/50 mix of popular TV Anime shows and Anime Movies from AniList GraphQL API
  useEffect(() => {
    const query = `
      query {
        tv: Page(page: 1, perPage: 16) {
          media(sort: POPULARITY_DESC, type: ANIME, format: TV) {
            coverImage { extraLarge large }
          }
        }
        movies: Page(page: 1, perPage: 16) {
          media(sort: POPULARITY_DESC, type: ANIME, format: MOVIE) {
            coverImage { extraLarge large }
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
        const tvCovers = data?.data?.tv?.media?.map((m: any) => m.coverImage?.extraLarge || m.coverImage?.large) || [];
        const movieCovers = data?.data?.movies?.media?.map((m: any) => m.coverImage?.extraLarge || m.coverImage?.large) || [];
        
        // Interleave TV shows and Movies for a rich mixed grid
        const mixed: string[] = [];
        const maxLen = Math.max(tvCovers.length, movieCovers.length);
        for (let i = 0; i < maxLen; i++) {
          if (tvCovers[i]) mixed.push(tvCovers[i]);
          if (movieCovers[i]) mixed.push(movieCovers[i]);
        }
        if (mixed.length >= 12) {
          setCovers(mixed);
        }
      })
      .catch((err) => console.error("AniList fetch error:", err));
  }, []);

  // Split covers into 6 columns for dense, smaller poster cards matching Screenshot 2
  const col1 = covers.slice(0, 5);
  const col2 = covers.slice(5, 10);
  const col3 = covers.slice(10, 15);
  const col4 = covers.slice(15, 20).length > 0 ? covers.slice(15, 20) : covers.slice(0, 5).reverse();
  const col5 = covers.slice(5, 10).reverse();
  const col6 = covers.slice(10, 15).reverse();

  return (
    <div className="min-h-screen bg-[#09090b] text-white selection:bg-purple-500 selection:text-white font-sans relative overflow-x-hidden">
      
      {/* Background Moving Poster Cards Grid (Smaller cards, 6 columns, matching Screenshot 2) */}
      <div className="absolute inset-0 z-0 overflow-hidden opacity-60 select-none pointer-events-none">
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-3 sm:gap-4 p-3 -rotate-3 scale-110 h-[240vh]">
          
          {/* Column 1 - Scroll Up */}
          <div className="flex flex-col gap-3 sm:gap-4 animate-scroll-up">
            {col1.concat(col1).map((url, i) => (
              <div key={`col1-${i}`} className="aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl border border-white/10 bg-white/5">
                <img src={url} alt="Anime Cover" className="w-full h-full object-cover filter brightness-95 contrast-105" loading="lazy" />
              </div>
            ))}
          </div>

          {/* Column 2 - Scroll Down */}
          <div className="flex flex-col gap-3 sm:gap-4 animate-scroll-down">
            {col2.concat(col2).map((url, i) => (
              <div key={`col2-${i}`} className="aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl border border-white/10 bg-white/5">
                <img src={url} alt="Anime Cover" className="w-full h-full object-cover filter brightness-95 contrast-105" loading="lazy" />
              </div>
            ))}
          </div>

          {/* Column 3 - Scroll Up */}
          <div className="flex flex-col gap-3 sm:gap-4 animate-scroll-up">
            {col3.concat(col3).map((url, i) => (
              <div key={`col3-${i}`} className="aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl border border-white/10 bg-white/5">
                <img src={url} alt="Anime Cover" className="w-full h-full object-cover filter brightness-95 contrast-105" loading="lazy" />
              </div>
            ))}
          </div>

          {/* Column 4 - Scroll Down */}
          <div className="flex flex-col gap-3 sm:gap-4 animate-scroll-down hidden sm:flex">
            {col4.concat(col4).map((url, i) => (
              <div key={`col4-${i}`} className="aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl border border-white/10 bg-white/5">
                <img src={url} alt="Anime Cover" className="w-full h-full object-cover filter brightness-95 contrast-105" loading="lazy" />
              </div>
            ))}
          </div>

          {/* Column 5 - Scroll Up */}
          <div className="flex flex-col gap-3 sm:gap-4 animate-scroll-up hidden md:flex">
            {col5.concat(col5).map((url, i) => (
              <div key={`col5-${i}`} className="aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl border border-white/10 bg-white/5">
                <img src={url} alt="Anime Cover" className="w-full h-full object-cover filter brightness-95 contrast-105" loading="lazy" />
              </div>
            ))}
          </div>

          {/* Column 6 - Scroll Down */}
          <div className="flex flex-col gap-3 sm:gap-4 animate-scroll-down hidden md:flex">
            {col6.concat(col6).map((url, i) => (
              <div key={`col6-${i}`} className="aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl border border-white/10 bg-white/5">
                <img src={url} alt="Anime Cover" className="w-full h-full object-cover filter brightness-95 contrast-105" loading="lazy" />
              </div>
            ))}
          </div>

        </div>

        {/* Crisp radial and linear dark overlay for high-contrast text rendering */}
        <div className="absolute inset-0 bg-gradient-to-b from-black/70 via-black/50 to-[#09090b] backdrop-blur-[1px]" />
      </div>

      {/* Top Header Navigation - Large Logo Without Box Enclosure */}
      <nav className="relative z-30 border-b border-white/10 bg-black/40 backdrop-blur-2xl sticky top-0 shadow-2xl">
        <div className="max-w-6xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-3.5 group cursor-pointer">
            <img 
              src="./logo.png" 
              alt="AnimeBox Logo" 
              className="w-11 h-11 object-contain drop-shadow-[0_4px_12px_rgba(255,255,255,0.25)] transition-transform duration-300 group-hover:scale-105" 
            />
            <span className="font-extrabold text-2xl tracking-tight bg-gradient-to-r from-white via-white to-purple-200 bg-clip-text text-transparent drop-shadow-md">
              AnimeBox
            </span>
          </div>

          <div className="flex items-center gap-8 text-sm font-semibold text-white/80">
            <a href="#features" className="hover:text-white transition drop-shadow-sm">Features</a>
            <a href="#showcase" className="hover:text-white transition drop-shadow-sm">Showcase</a>
            <a href="#download" className="hover:text-white transition drop-shadow-sm">Download</a>
          </div>
        </div>
      </nav>

      {/* Hero Section - Padded to prevent bottom text overlap on single viewports */}
      <header className="relative z-10 max-w-4xl mx-auto px-6 pt-20 pb-36 text-center flex flex-col items-center justify-center min-h-[90vh]">
        
        <h1 className="text-5xl sm:text-7xl md:text-8xl font-black tracking-tight leading-[1.1]">
          <span className="text-white drop-shadow-[0_10px_25px_rgba(0,0,0,0.9)]">Just press play.</span>
          <br />
          <span className="text-white/80 font-extrabold drop-shadow-[0_8px_20px_rgba(0,0,0,0.9)]">Anime, made simple.</span>
        </h1>

        <p className="mt-6 text-base sm:text-xl text-white/90 font-medium max-w-xl drop-shadow-[0_4px_12px_rgba(0,0,0,0.95)]">
          Your anime, your language, your pace.
        </p>

        {/* Hero Download Button */}
        <div id="download" className="relative mt-10 group z-20">
          <div className="absolute -inset-4 rounded-full bg-white/30 blur-2xl transition group-hover:bg-white/50" />
          <a
            href={downloadUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="relative inline-flex items-center gap-4 rounded-full bg-white px-8 py-4 sm:px-10 sm:py-5 text-black shadow-[0_20px_60px_-10px_rgba(255,255,255,0.5)] transition hover:scale-[1.03] active:scale-100"
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

        <div className="mt-5 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs text-white/80 font-semibold drop-shadow-[0_2px_4px_rgba(0,0,0,0.9)]">
          <span>APK · 98 MB</span>
          <span className="h-1 w-1 rounded-full bg-white/60" />
          <span>Android 8.0+</span>
        </div>
      </header>

      {/* App Showcase Section - Cleanly Separated Viewport */}
      <section
        id="showcase"
        className="relative z-10 max-w-6xl mx-auto px-6 pt-24 pb-28 border-t border-white/10"
      >
        <div className="mb-12 text-center">
          <h2 className="text-3xl sm:text-5xl font-black tracking-tight text-white drop-shadow-[0_8px_20px_rgba(0,0,0,0.9)]">
            Built for how you actually watch.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-white/70 font-medium max-w-xl mx-auto drop-shadow-md">
            Pick your audio, jump between episodes, and get back to the show.
          </p>
        </div>

        {/* Horizontal Phone Mockup displaying Jujutsu Kaisen Player Screenshot */}
        <div className="relative mx-auto w-full max-w-4xl">
          <div className="absolute -inset-8 rounded-[3rem] bg-purple-600/20 blur-3xl" />
          
          <div className="relative rounded-[2.5rem] border-[10px] border-[#18181b] bg-black p-2 shadow-2xl overflow-hidden group">
            <img
              src="./showcase/photo_2026-07-29_03-09-17.jpg"
              alt="AnimeBox Video Player UI"
              className="w-full h-auto rounded-[1.8rem] object-cover transition-transform duration-500 group-hover:scale-[1.01]"
            />
          </div>
        </div>

        {/* Showcase Feature Chips with Glassmorphism */}
        <div className="mt-12 flex flex-wrap items-center justify-center gap-4 text-xs sm:text-sm text-white/90">
          <div className="inline-flex items-center gap-2 px-5 py-3 rounded-full border border-white/20 bg-black/60 backdrop-blur-2xl shadow-xl">
            <Languages className="w-4 h-4 text-purple-400" />
            <span>Japanese, English and Hindi audio — switch anytime.</span>
          </div>
          <div className="inline-flex items-center gap-2 px-5 py-3 rounded-full border border-white/20 bg-black/60 backdrop-blur-2xl shadow-xl">
            <PlayCircle className="w-4 h-4 text-purple-400" />
            <span>Episode selector right inside the player.</span>
          </div>
          <div className="inline-flex items-center gap-2 px-5 py-3 rounded-full border border-white/20 bg-black/60 backdrop-blur-2xl shadow-xl">
            <Sparkles className="w-4 h-4 text-purple-400" />
            <span>Clean player UI that stays out of the way.</span>
          </div>
        </div>
      </section>

      {/* Main Features Glassmorphism Grid */}
      <section id="features" className="relative z-10 max-w-5xl mx-auto px-6 py-20 border-t border-white/10">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-bold tracking-tight">Features You'll Love</h2>
          <p className="text-sm text-white/60 mt-2">Designed from the ground up for performance, quality, and comfort.</p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          
          {/* Multi-Audio Support with Languages Icon */}
          <div className="p-6 rounded-3xl border border-white/15 bg-white/[0.04] backdrop-blur-2xl shadow-xl hover:border-purple-500/50 hover:bg-white/[0.07] transition group">
            <div className="w-12 h-12 rounded-2xl bg-purple-500/15 border border-purple-500/30 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <Languages className="w-6 h-6 text-purple-400" />
            </div>
            <h3 className="text-base font-semibold">Multi-Audio Support</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Switch seamlessly between Japanese (Sub), English (Dub), and Hindi audio tracks on supported titles.
            </p>
          </div>

          {/* Multiple Profiles */}
          <div className="p-6 rounded-3xl border border-white/15 bg-white/[0.04] backdrop-blur-2xl shadow-xl hover:border-pink-500/50 hover:bg-white/[0.07] transition group">
            <div className="w-12 h-12 rounded-2xl bg-pink-500/15 border border-pink-500/30 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <Users className="w-6 h-6 text-pink-400" />
            </div>
            <h3 className="text-base font-semibold">Multiple Profiles</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Support for creating and managing separate user profiles, including dedicated kids profiles.
            </p>
          </div>

          {/* Custom Video Player with Play Icon */}
          <div className="p-6 rounded-3xl border border-white/15 bg-white/[0.04] backdrop-blur-2xl shadow-xl hover:border-blue-500/50 hover:bg-white/[0.07] transition group">
            <div className="w-12 h-12 rounded-2xl bg-blue-500/15 border border-blue-500/30 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <Play className="w-6 h-6 text-blue-400 fill-blue-400/20" />
            </div>
            <h3 className="text-base font-semibold">Custom Video Player</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Advanced video player featuring volume/brightness gestures, skip intro/outro controls, and subtitle options.
            </p>
          </div>

          {/* Search & Discovery */}
          <div className="p-6 rounded-3xl border border-white/15 bg-white/[0.04] backdrop-blur-2xl shadow-xl hover:border-indigo-500/50 hover:bg-white/[0.07] transition group">
            <div className="w-12 h-12 rounded-2xl bg-indigo-500/15 border border-indigo-500/30 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <Wifi className="w-6 h-6 text-indigo-400" />
            </div>
            <h3 className="text-base font-semibold">Search & Discovery</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Search through anime series with category, genre, and tag filters.
            </p>
          </div>

          {/* Backup & Restore */}
          <div className="p-6 rounded-3xl border border-white/15 bg-white/[0.04] backdrop-blur-2xl shadow-xl hover:border-emerald-500/50 hover:bg-white/[0.07] transition group">
            <div className="w-12 h-12 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <Layers className="w-6 h-6 text-emerald-400" />
            </div>
            <h3 className="text-base font-semibold">Backup & Restore</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Export profile data and app settings to a JSON file and import them back anytime.
            </p>
          </div>

          {/* Personal Watchlist */}
          <div className="p-6 rounded-3xl border border-white/15 bg-white/[0.04] backdrop-blur-2xl shadow-xl hover:border-amber-500/50 hover:bg-white/[0.07] transition group">
            <div className="w-12 h-12 rounded-2xl bg-amber-500/15 border border-amber-500/30 flex items-center justify-center mb-4 group-hover:scale-110 transition">
              <ShieldCheck className="w-6 h-6 text-amber-400" />
            </div>
            <h3 className="text-base font-semibold">Personal Watchlist</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Per-profile anime saving and watch history tracking.
            </p>
          </div>

        </div>
      </section>

      {/* Why AnimeBox Section */}
      <section id="why" className="relative z-10 max-w-4xl mx-auto px-6 py-16 border-t border-white/10">
        <div className="p-8 sm:p-10 rounded-3xl border border-white/15 bg-white/[0.04] backdrop-blur-2xl shadow-2xl">
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

          <div className="mt-6 p-4 rounded-2xl bg-black/60 border border-white/10 text-xs text-white/60 leading-relaxed">
            <strong className="text-white block mb-1">Important Notice:</strong>
            This repository and source code <span className="text-white font-semibold">do not contain or host any streaming sources or copyrighted video content</span>. 
            For streaming, API endpoints can be configured locally or linked directly to official legal streaming services like Crunchyroll, Netflix, or local providers using AniList/TMDB metadata.
          </div>
        </div>
      </section>

      {/* Redesigned Glassmorphic Footer */}
      <footer className="relative z-20 border-t border-white/10 bg-black/60 backdrop-blur-2xl py-12">
        <div className="max-w-6xl mx-auto px-6 flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-3">
            <img src="./logo.png" alt="AnimeBox Logo" className="w-8 h-8 object-contain" />
            <span className="font-bold text-lg text-white">AnimeBox</span>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-6 text-xs text-white/70 font-medium">
            <a href="#features" className="hover:text-white transition">Features</a>
            <a href="#showcase" className="hover:text-white transition">Showcase</a>
            <a href="#why" className="hover:text-white transition">Why AnimeBox</a>
            <a href={downloadUrl} target="_blank" rel="noopener noreferrer" className="hover:text-white transition">Download APK</a>
          </div>

          <div className="flex items-center gap-4 text-xs text-white/50">
            <span>© {new Date().getFullYear()} AnimeBox</span>
            <span>•</span>
            <a 
              href="https://github.com/SOLO-ARC/Animebox" 
              target="_blank" 
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 hover:text-white transition"
            >
              <Github className="w-3.5 h-3.5" />
              <span>GitHub</span>
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
