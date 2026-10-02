# BiznesCreator

Wyszukiwarka lokalnych firm **bez strony internetowej** (restauracje, fryzjerzy, barberzy, kosmetyczki, salony paznokci, mechanicy, fizjoterapeuci, dentyści, sklepy). Zaznaczasz prostokąt na mapie, a narzędzie pokazuje firmy, które:

- **nie mają strony** w ogóle,
- mają tylko **Facebooka / Instagrama / Booksy**,
- mają stronę, która **nie działa** (narzędzie sprawdza ją na żywo).

Znalezione firmy zapisujesz jako **leady** w prostym CRM ze statusami (Nowy → Zadzwoniłem → Zainteresowany → Klient / Odmowa) i notatkami.

Domena docelowa: **biznescreator.pl**. Styl strony jest taki sam jak na stanislobiznes.pl.

## Koszt: 0 zł

| Element | Skąd | Koszt |
|---|---|---|
| Dane o firmach | OpenStreetMap (Overpass API) | darmowe, bez klucza |
| Mapa | kafelki OpenStreetMap | darmowe |
| Sprawdzanie stron | własny serwer | darmowe |
| Google Places | **opcjonalnie**, z twardym limitem 900 zapytań/mies. | 0 zł w darmowym progu |

Google Places API daje dokładniejsze dane (oceny, liczba opinii), ale wymaga konta Google Cloud z podpiętą kartą. Bez klucza aplikacja działa w 100% na OpenStreetMap. W OSM nie ma ocen, a w mniejszych miejscowościach brakuje części firm. Jeśli kiedyś dodasz klucz, ustaw `GOOGLE_MONTHLY_CAP` poniżej darmowego progu (domyślnie 900). Serwer twardo blokuje dalsze zapytania po osiągnięciu limitu.

## Jak działa rejestracja (rekrutacja)

1. `/register` przekierowuje na **`/rekrutacja`**, czyli formularz z 10 pytaniami (jedno z nich to adres e-mail).
2. Zgłoszenie trafia do **panelu admina** (`/admin`).
3. Admin klika **Przyjmij**. Tworzy się konto, a admin dostaje **jednorazowy link aktywacyjny** ważny 72 godziny. Przycisk **Wyślij e-mail** otwiera gotową wiadomość w Twoim programie pocztowym, więc nie potrzebujesz płatnego serwera poczty.
4. Nowy użytkownik otwiera link, ustawia hasło i od razu jest zalogowany.

Zapomniane hasło: admin w zakładce **Użytkownicy** klika **Link do hasła** i wysyła go użytkownikowi.

## Hosting: Vercel + Turso (0 zł)

- **Vercel** uruchamia aplikację (folder `api/` i plik `vercel.json`).
- **Turso** to darmowa baza danych w chmurze (kompatybilna z SQLite, bez karty).
  Vercel nie przechowuje plików, dlatego baza musi być zewnętrzna.

Pełna instrukcja krok po kroku: **[WDROZENIE.md](WDROZENIE.md)**.

Lokalnie (Node.js 20+):

```bash
npm install
npm run create-admin -- twoj@email.pl "Stanisław"   # wypisze link do ustawienia hasła
npm start                                           # http://localhost:3000
npm test
```

Bez `DATABASE_URL` aplikacja używa lokalnego pliku `data/biznescreator.db`.

Zanim wpuścisz użytkowników, przeczytaj **[BEZPIECZENSTWO.md](BEZPIECZENSTWO.md)**.

## Struktura

```
api/         index.js – wejście dla Vercel
server/      index.js (trasy), auth.js (logowanie, sesje, CSRF, limity), search.js (OSM + Google),
             sitecheck.js (sprawdzanie stron, ochrona SSRF), db.js (Turso / SQLite), config.js
views/       strony HTML (layout + podstrony)
public/      css, js, vendor/leaflet, favicon.svg, img/logo.svg (serwowane bezpośrednio przez CDN)
scripts/     create-admin.js
test/        testy (node --test)
data/        lokalna baza SQLite (nie trafia do gita)
```
