package pl.boss.drive.core.addon

import pl.boss.drive.core.R

/** Catalogue of optional apps offered in the head unit's store. */
data class Addon(
    val pkg: String,
    val name: String,
    val subtitle: String,
    val category: String,
    val description: String,
    val features: List<String>,
    val glyph: Int,
    val color: Int,
    val color2: Int,
    val audio: Boolean = false,
    /** Third-party app installed from Google Play, not shipped by us. */
    val external: Boolean = false,
    val version: Int = 1,
)

object Addons {
    const val RADIO_PKG = "pl.boss.drive.radio"
    const val YT_MUSIC = "com.google.android.apps.youtube.music"

    val ALL = listOf(
        Addon(YT_MUSIC, "YouTube Music", "Oficjalna aplikacja Google", "Muzyka",
            "Oficjalne YouTube Music zainstalowane na radiu. Z YouTube Premium pobierasz playlisty offline, a one same się aktualizują, " +
                "gdy radio ma internet z hotspotu telefonu. Boss Drive steruje nim tak jak muzyką z telefonu: okładki, kolejka, " +
                "przyciski na kierownicy.",
            listOf("Sterowanie z ekranu „Teraz gra” i z kierownicy", "Playlisty offline z Premium — aktualizują się same",
                "Pauza na czas rozmowy telefonicznej", "Instalacja ze Sklepu Google Play na radiu"),
            R.drawable.ic_play_circle, 0xFFFF2B2B.toInt(), 0xFFC4001D.toInt(), audio = true, external = true),
        Addon("pl.boss.drive.addon.trip", "Trasa", "Licznik i komputer pokładowy", "Jazda",
            "Duży prędkościomierz z GPS i statystyki bieżącej trasy: dystans, czas jazdy, średnia i maksymalna prędkość, wysokość i kierunek. " +
                "Trasa zeruje się sama po dłuższym postoju.",
            listOf("Prędkość z GPS — dokładniejsza niż zegar w aucie", "Dystans, czas, średnia, maksimum", "Kompas i wysokość", "Automatycznie nowa trasa po postoju"),
            R.drawable.ic_speedometer, 0xFF5E5CE6.toInt(), 0xFF3634A3.toInt()),
        Addon("pl.boss.drive.addon.fuel", "Paliwo", "Spalanie i koszty tankowań", "Auto",
            "Zapisuj tankowania dużą klawiaturą prosto z radia. Boss Drive liczy realne spalanie (metodą „do pełna”), koszt przejechania " +
                "100 km i wydatki w tym miesiącu.",
            listOf("Realne spalanie l/100 km", "Koszt 100 km i miesięczne wydatki", "Historia tankowań", "Działa w pełni offline"),
            R.drawable.ic_fuel, 0xFFFF9F0A.toInt(), 0xFFE07800.toInt()),
        Addon("pl.boss.drive.addon.podcasts", "Podcasty", "Nowe odcinki same w aucie", "Audio",
            "Subskrybuj podcasty, a nowe odcinki pobiorą się do radia same, gdy jest internet. W trasie słuchasz offline, " +
                "aplikacja pamięta, gdzie skończyłeś, a przesłuchane odcinki same znikają.",
            listOf("Automatyczne pobieranie nowych odcinków", "Słuchanie bez internetu", "Pamięta miejsce w odcinku", "Wyszukiwarka podcastów"),
            R.drawable.ic_podcast, 0xFFBF5AF2.toInt(), 0xFF8A2BC2.toInt(), audio = true),
        Addon("pl.boss.drive.addon.webradio", "Radio online", "Polskie stacje na żywo", "Audio",
            "Setki polskich stacji internetowych — RMF, ZET, Trójka, Eska, TOK FM i lokalne rozgłośnie — bez szumów i zasięgu FM. " +
                "Ulubione stacje zawsze pod ręką.",
            listOf("Stacje z całej Polski", "Ulubione jednym dotknięciem", "Wyszukiwanie po nazwie", "Sterowanie z kierownicy"),
            R.drawable.ic_radio, 0xFFFF375F.toInt(), 0xFFD70040.toInt(), audio = true),
        Addon("pl.boss.drive.addon.service", "Serwis", "Przegląd, OC, olej, opony", "Auto",
            "Wszystkie terminy auta w jednym miejscu: przegląd techniczny, OC/AC, wymiana oleju (data i kilometry), opony sezonowe. " +
                "Kolorowe ostrzeżenia, zanim minie termin.",
            listOf("Przegląd, OC, olej, opony", "Ostrzega 30 dni / 1000 km wcześniej", "Liczy dni i kilometry do terminu", "Działa w pełni offline"),
            R.drawable.ic_wrench, 0xFF8E8E93.toInt(), 0xFF5A5A5F.toInt()),
    )

    fun byPkg(pkg: String) = ALL.firstOrNull { it.pkg == pkg }
}
