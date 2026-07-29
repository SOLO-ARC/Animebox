import { 
  Tv, 
  Smartphone, 
  Sparkles, 
  ShieldCheck, 
  Users, 
  PlayCircle, 
  Database, 
  Download, 
  ExternalLink,
  Sliders,
  Layers,
  Heart
} from "lucide-react";

export default function App() {
  const downloadUrl = "https://github.com/SOLO-ARC/Animebox/releases/latest";

  return (
    <div className="min-h-screen bg-[#09090b] text-white selection:bg-purple-500 selection:text-white">
      {/* Background glow effects */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden z-0">
        <div className="absolute -top-40 left-1/2 -translate-x-1/2 w-[800px] h-[500px] bg-purple-600/15 rounded-full blur-[140px]" />
        <div className="absolute top-96 left-1/4 w-[400px] h-[400px] bg-blue-600/10 rounded-full blur-[120px]" />
      </div>

      {/* Navigation */}
      <nav className="relative z-10 border-b border-white/10 bg-black/40 backdrop-blur-md sticky top-0">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img 
              src="./photo_2026-07-27_05-42-31.jpg" 
              alt="AnimeBox Logo" 
              className="w-9 h-9 rounded-xl shadow-lg border border-white/10 object-cover" 
            />
            <span className="font-bold text-xl tracking-tight bg-gradient-to-r from-white via-white to-white/70 bg-clip-text text-transparent">
              AnimeBox
            </span>
          </div>

          <div className="flex items-center gap-6">
            <a 
              href="#features" 
              className="text-sm text-white/70 hover:text-white transition hidden sm:inline-block"
            >
              Features
            </a>
            <a 
              href="#why" 
              className="text-sm text-white/70 hover:text-white transition hidden sm:inline-block"
            >
              Why AnimeBox
            </a>
            <a 
              href={downloadUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white text-black text-xs font-semibold hover:bg-white/90 transition shadow-md"
            >
              <Download className="w-3.5 h-3.5" />
              Download APK
            </a>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <header className="relative z-10 max-w-4xl mx-auto px-6 pt-20 pb-16 text-center">
        <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-purple-500/30 bg-purple-500/10 text-purple-300 text-xs font-medium mb-8 backdrop-blur-md">
          <Sparkles className="w-3.5 h-3.5" />
          <span>Modern Anime Experience for Android</span>
        </div>

        <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight leading-[1.15]">
          Anime, <span className="bg-gradient-to-r from-purple-400 via-pink-400 to-indigo-300 bg-clip-text text-transparent">refined</span> & made simple.
        </h1>

        <p className="mt-6 text-base sm:text-lg text-white/70 max-w-2xl mx-auto leading-relaxed">
          Explore trending anime, manage multi-user profiles, and enjoy customizable player controls powered by AniList metadata.
        </p>

        {/* Download Button CTA */}
        <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
          <a
            href={downloadUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-3 px-8 py-4 rounded-full bg-white text-black font-semibold text-base shadow-[0_0_40px_-5px_rgba(255,255,255,0.4)] hover:scale-105 active:scale-95 transition"
          >
            <Smartphone className="w-5 h-5 text-purple-600" />
            <span>Download for Android</span>
          </a>
          <a
            href="https://github.com/SOLO-ARC/Animebox"
            target="_blank"
            rel="noopener noreferrer"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-4 rounded-full border border-white/15 bg-white/5 text-white/80 font-medium text-sm hover:bg-white/10 transition"
          >
            <span>View Source on GitHub</span>
            <ExternalLink className="w-4 h-4" />
          </a>
        </div>

        <div className="mt-6 text-xs text-white/40 flex items-center justify-center gap-4">
          <span>Free & Open Source</span>
          <span>•</span>
          <span>Android 8.0+</span>
          <span>•</span>
          <span>No In-App Purchases</span>
        </div>
      </header>

      {/* Features Grid */}
      <section id="features" className="relative z-10 max-w-5xl mx-auto px-6 py-16 border-t border-white/10">
        <div className="text-center mb-12">
          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Built for How You Watch</h2>
          <p className="text-sm text-white/60 mt-2">Clean, powerful features designed without unnecessary clutter.</p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          <div className="p-6 rounded-2xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Database className="w-6 h-6 text-purple-400 mb-4" />
            <h3 className="text-base font-semibold">AniList Integration</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Fetches rich anime metadata, cover art, tags, genres, and community ratings directly from AniList.
            </p>
          </div>

          <div className="p-6 rounded-2xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Users className="w-6 h-6 text-pink-400 mb-4" />
            <h3 className="text-base font-semibold">Multi-Profile Support</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Create and manage individual user profiles with dedicated watch histories and kids restrictions.
            </p>
          </div>

          <div className="p-6 rounded-2xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Sliders className="w-6 h-6 text-blue-400 mb-4" />
            <h3 className="text-base font-semibold">Custom Video Player</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Includes volume/brightness gestures, skip intro/outro buttons, quality switching, and subtitle settings.
            </p>
          </div>

          <div className="p-6 rounded-2xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <PlayCircle className="w-6 h-6 text-indigo-400 mb-4" />
            <h3 className="text-base font-semibold">Continue Watching</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Pick up right where you left off across your active profiles automatically.
            </p>
          </div>

          <div className="p-6 rounded-2xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <Layers className="w-6 h-6 text-emerald-400 mb-4" />
            <h3 className="text-base font-semibold">Backup & Restore</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Export and import your profile preferences, watch state, and settings to a JSON file anytime.
            </p>
          </div>

          <div className="p-6 rounded-2xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:border-purple-500/40 transition">
            <ShieldCheck className="w-6 h-6 text-amber-400 mb-4" />
            <h3 className="text-base font-semibold">Clean Interface</h3>
            <p className="text-xs text-white/60 mt-2 leading-relaxed">
              Designed with dark aesthetics, modern typography, and smooth navigation.
            </p>
          </div>
        </div>
      </section>

      {/* Why AnimeBox Section */}
      <section id="why" className="relative z-10 max-w-4xl mx-auto px-6 py-16 border-t border-white/10">
        <div className="bg-gradient-to-b from-white/[0.05] to-transparent p-8 sm:p-10 rounded-3xl border border-white/10">
          <div className="flex items-center gap-3 text-purple-400 mb-4">
            <Heart className="w-5 h-5" />
            <h2 className="text-xl font-bold tracking-tight text-white">Why AnimeBox Was Created</h2>
          </div>

          <p className="text-sm text-white/70 leading-relaxed">
            Mainstream anime streaming platforms often struggle to deliver the experience fans deserve. Services like Crunchyroll frequently remove popular titles, feature dated user interfaces, and deliver basic subtitle formatting or lower video playback options.
          </p>

          <p className="text-sm text-white/70 leading-relaxed mt-4">
            While platforms like Netflix offer a polished UI, their anime catalogs are heavily region-locked, limited in title selection, or unavailable in many countries. AnimeBox was created to solve these interface and accessibility hurdles with a clean, responsive client interface.
          </p>

          <div className="mt-6 p-4 rounded-2xl bg-black/40 border border-white/10 text-xs text-white/60 leading-relaxed">
            <strong className="text-white block mb-1">Important Notice:</strong>
            This repository and mobile app client do not host or distribute any video streams. For playback functionality, streaming sources can be configured locally or linked directly to official platforms such as Crunchyroll, Netflix, or TMDB/AniList metadata providers.
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="relative z-10 border-t border-white/10 py-8 text-center text-xs text-white/40">
        <div className="max-w-4xl mx-auto px-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>© {new Date().getFullYear()} AnimeBox. Released under open-source license.</div>
          <div className="flex items-center gap-4">
            <a href={downloadUrl} className="hover:text-white transition">Download APK</a>
            <a href="https://github.com/SOLO-ARC/Animebox" className="hover:text-white transition">GitHub Repo</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
