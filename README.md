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

## Uruchomienie lokalne

Wymagany Node.js 20+.

```bash
npm install
cp .env.example .env              # lokalnie zmień NODE_ENV=development i BASE_URL=http://localhost:3000
npm run create-admin -- twoj@email.pl "Stanisław"
npm start                         # http://localhost:3000
```

`create-admin` wypisze link do ustawienia hasła. Hasło nigdy nie przechodzi przez wiersz poleceń.

Testy: `npm test`.

## Wdrożenie na biznescreator.pl

Aplikacja potrzebuje serwera z Node.js. Zwykły hosting „na PHP” nie wystarczy. Darmowe lub tanie opcje:

- **Oracle Cloud Always Free**: darmowy VPS na zawsze.
- **mikr.us**: polski VPS za kilkadziesiąt zł rocznie.
- dowolny VPS z Ubuntu.

Na serwerze:

```bash
git clone <repo> biznescreator && cd biznescreator
npm ci --omit=dev
cp .env.example .env   # NODE_ENV=production, BASE_URL=https://biznescreator.pl, TRUST_PROXY=1
npm run create-admin -- twoj@email.pl "Stanisław"
sudo npm i -g pm2 && pm2 start server/index.js --name biznescreator && pm2 save && pm2 startup
```

Przed aplikacją postaw **nginx z HTTPS** (Let's Encrypt, darmowy certyfikat):

```nginx
server {
  server_name biznescreator.pl www.biznescreator.pl;
  location / {
    proxy_pass http://127.0.0.1:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
  client_max_body_size 1m;
}
```

```bash
sudo certbot --nginx -d biznescreator.pl -d www.biznescreator.pl
```

Ustaw w DNS domeny rekord `A` wskazujący na IP serwera.

Zanim wpuścisz użytkowników, przeczytaj **[BEZPIECZENSTWO.md](BEZPIECZENSTWO.md)**.

## Struktura

```
server/      index.js (trasy), auth.js (logowanie, sesje, CSRF), search.js (OSM + Google),
             sitecheck.js (sprawdzanie stron, ochrona SSRF), db.js (SQLite), config.js
views/       strony HTML (layout + podstrony)
public/      css, js, logo (img/favicon.svg, img/logo.svg)
scripts/     create-admin.js
test/        testy (node --test)
data/        baza SQLite (nie trafia do gita)
```
