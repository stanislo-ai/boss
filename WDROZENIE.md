# Wdrożenie BiznesCreator: Vercel + Turso (0 zł)

## 0. Co będzie potrzebne
- konto GitHub (masz),
- konto Vercel (masz),
- konto Turso: https://turso.tech, logowanie przez GitHub, plan Free, bez karty,
- Node.js 20+ na komputerze: https://nodejs.org (wersja LTS), potrzebny jednorazowo do założenia konta admina.

## 1. Kod na gałęzi main
Kod jest na gałęzi `ccr-b011b4ed-e57j0t`. Na GitHubie (repo `stanislo-ai/boss`) utwórz Pull Request z tej gałęzi do `main` i kliknij **Merge**.

## 2. Baza Turso
1. https://app.turso.tech → **Create Database**, nazwa `biznescreator`, region **Frankfurt** albo **Warsaw** (najbliżej Polski).
2. Na stronie bazy skopiuj **URL** (`libsql://biznescreator-twojlogin.turso.io`).
3. Kliknij **Create Token** (uprawnienia Read & Write, bez daty wygaśnięcia) i skopiuj token. Token pokazuje się tylko raz.

## 3. Projekt na Vercel
1. https://vercel.com/new → **Import** repozytorium `stanislo-ai/boss`.
2. Framework Preset: **Other**. Build Command i Output Directory zostaw puste.
3. **Environment Variables**:
   - `DATABASE_URL` = URL z Turso
   - `DATABASE_AUTH_TOKEN` = token z Turso
   - `BASE_URL` = `https://biznescreator.pl`
4. **Deploy**.

## 4. Domena biznescreator.pl
1. Vercel → projekt → **Settings → Domains** → dodaj `biznescreator.pl` i `www.biznescreator.pl`, ustaw przekierowanie `www` → `biznescreator.pl`.
2. U rejestratora domeny (np. OVH, home.pl, nazwa.pl) w strefie DNS dodaj rekordy, które pokaże Vercel: zwykle `A @ → 76.76.21.21` i `CNAME www → cname.vercel-dns.com`. Skopiuj wartości z panelu Vercel, bo mogą być inne.
3. Poczekaj od kilku minut do kilku godzin. Certyfikat HTTPS Vercel wystawi sam.

## 5. Konto admina (jednorazowo, na komputerze)
```bash
git clone https://github.com/stanislo-ai/boss.git
cd boss
npm install
```
Utwórz plik `.env` w folderze `boss`:
```
DATABASE_URL=libsql://...       (to samo co na Vercel)
DATABASE_AUTH_TOKEN=...         (to samo co na Vercel)
BASE_URL=https://biznescreator.pl
```
Następnie:
```bash
npm run create-admin -- twoj@email.pl "Stanisław"
```
Otwórz wypisany link (ważny 24 h), ustaw hasło i gotowe: jesteś adminem.

## 6. Test
1. W trybie incognito wejdź na `https://biznescreator.pl/register`. Powinno przekierować do rekrutacji. Wyślij testowe zgłoszenie.
2. Jako admin: `/admin` → **Przyjmij** → skopiuj link → otwórz go w incognito → ustaw hasło.
3. `/app` → wybierz branże → **Zaznacz obszar** → przeciągnij prostokąt nad kawałkiem miasta → **Szukaj**.

## Aktualizacje
Każdy push na `main` = automatyczne wdrożenie na Vercel.

## Lokalnie (opcjonalnie)
Bez `DATABASE_URL` w `.env` aplikacja używa lokalnego pliku `data/biznescreator.db`:
```bash
npm start   # http://localhost:3000
```
