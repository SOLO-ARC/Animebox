package com.lagradost.cloudstream3.ui.animebox.settings

import android.content.Context

/** Translations for fixed home and navigation labels. Anime titles remain provider supplied. */
object AnimeBoxUiText {
    private val english = mapOf(
        "Home" to "Home", "Search" to "Search", "My Space" to "My Space",
        "Top 10 Trending Shows" to "Top 10 Trending Shows",
        "Animation Studios" to "Animation Studios", "Recently Added" to "Recently Added",
        "Popular This Season" to "Popular This Season", "Popular Movies" to "Popular Movies",
        "Coming Soon" to "Coming Soon", "Frequently Asked Questions" to "Frequently Asked Questions",
        "Play" to "Play", "None" to "None", "In List" to "In List", "My List" to "My List"
    )

    private val translations: Map<String, Map<String, String>> = mapOf(
        "japanese" to mapOf("Home" to "ホーム", "Search" to "検索", "My Space" to "マイスペース", "Top 10 Trending Shows" to "人気急上昇トップ10", "Animation Studios" to "アニメーション制作会社", "Recently Added" to "最近追加", "Popular This Season" to "今期の人気作品", "Popular Movies" to "人気映画", "Coming Soon" to "近日公開", "Frequently Asked Questions" to "よくある質問", "Play" to "再生", "None" to "なし", "In List" to "リストに追加済み", "My List" to "マイリスト"),
        "hindi" to mapOf("Home" to "होम", "Search" to "खोजें", "My Space" to "मेरा स्पेस", "Top 10 Trending Shows" to "टॉप 10 ट्रेंडिंग शो", "Animation Studios" to "एनीमेशन स्टूडियो", "Recently Added" to "हाल ही में जोड़े गए", "Popular This Season" to "इस सीज़न के लोकप्रिय", "Popular Movies" to "लोकप्रिय फ़िल्में", "Coming Soon" to "जल्द आ रहा है", "Frequently Asked Questions" to "अक्सर पूछे जाने वाले सवाल", "Play" to "चलाएँ", "None" to "कोई नहीं", "In List" to "सूची में", "My List" to "मेरी सूची"),
        "spanish" to mapOf("Home" to "Inicio", "Search" to "Buscar", "My Space" to "Mi espacio", "Top 10 Trending Shows" to "10 series más populares", "Animation Studios" to "Estudios de animación", "Recently Added" to "Añadidos recientemente", "Popular This Season" to "Populares esta temporada", "Popular Movies" to "Películas populares", "Coming Soon" to "Próximamente", "Frequently Asked Questions" to "Preguntas frecuentes", "Play" to "Reproducir", "None" to "Ninguno", "In List" to "En mi lista", "My List" to "Mi lista"),
        "french" to mapOf("Home" to "Accueil", "Search" to "Rechercher", "My Space" to "Mon espace", "Top 10 Trending Shows" to "Les 10 séries tendance", "Animation Studios" to "Studios d’animation", "Recently Added" to "Ajouts récents", "Popular This Season" to "Populaires cette saison", "Popular Movies" to "Films populaires", "Coming Soon" to "À venir", "Frequently Asked Questions" to "Questions fréquentes", "Play" to "Lire", "None" to "Aucun", "In List" to "Dans ma liste", "My List" to "Ma liste"),
        "german" to mapOf("Home" to "Start", "Search" to "Suche", "My Space" to "Mein Bereich", "Top 10 Trending Shows" to "Top 10 beliebte Serien", "Animation Studios" to "Animationsstudios", "Recently Added" to "Kürzlich hinzugefügt", "Popular This Season" to "Diese Staffel beliebt", "Popular Movies" to "Beliebte Filme", "Coming Soon" to "Demnächst", "Frequently Asked Questions" to "Häufige Fragen", "Play" to "Abspielen", "None" to "Keine", "In List" to "In meiner Liste", "My List" to "Meine Liste"),
        "portuguese" to mapOf("Home" to "Início", "Search" to "Buscar", "My Space" to "Meu espaço", "Top 10 Trending Shows" to "10 séries em alta", "Animation Studios" to "Estúdios de animação", "Recently Added" to "Adicionados recentemente", "Popular This Season" to "Populares nesta temporada", "Popular Movies" to "Filmes populares", "Coming Soon" to "Em breve", "Frequently Asked Questions" to "Perguntas frequentes", "Play" to "Reproduzir", "None" to "Nenhum", "In List" to "Na minha lista", "My List" to "Minha lista"),
        "indonesian" to mapOf("Home" to "Beranda", "Search" to "Cari", "My Space" to "Ruang Saya", "Top 10 Trending Shows" to "10 Anime Tren Teratas", "Animation Studios" to "Studio Animasi", "Recently Added" to "Baru Ditambahkan", "Popular This Season" to "Populer Musim Ini", "Popular Movies" to "Film Populer", "Coming Soon" to "Segera Hadir", "Frequently Asked Questions" to "Pertanyaan Umum", "Play" to "Putar", "None" to "Tidak ada", "In List" to "Di Daftar", "My List" to "Daftar Saya"),
        "arabic" to mapOf("Home" to "الرئيسية", "Search" to "بحث", "My Space" to "مساحتي", "Top 10 Trending Shows" to "أفضل 10 مسلسلات رائجة", "Animation Studios" to "استوديوهات الرسوم المتحركة", "Recently Added" to "أضيفت مؤخراً", "Popular This Season" to "الأكثر شعبية هذا الموسم", "Popular Movies" to "أفلام شائعة", "Coming Soon" to "قريباً", "Frequently Asked Questions" to "الأسئلة الشائعة", "Play" to "تشغيل", "None" to "لا شيء", "In List" to "في قائمتي", "My List" to "قائمتي"),
        "russian" to mapOf("Home" to "Главная", "Search" to "Поиск", "My Space" to "Моё пространство", "Top 10 Trending Shows" to "Топ-10 популярных сериалов", "Animation Studios" to "Анимационные студии", "Recently Added" to "Недавно добавлено", "Popular This Season" to "Популярное в этом сезоне", "Popular Movies" to "Популярные фильмы", "Coming Soon" to "Скоро", "Frequently Asked Questions" to "Частые вопросы", "Play" to "Смотреть", "None" to "Нет", "In List" to "В списке", "My List" to "Мой список")
    )

    fun text(context: Context, englishText: String): String {
        val language = AnimeBoxSettings.getDisplayLanguage(context).lowercase()
        return translations[language]?.get(englishText) ?: english[englishText] ?: englishText
    }
}
