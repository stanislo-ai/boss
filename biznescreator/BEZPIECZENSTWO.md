# Bezpieczeństwo BiznesCreator

## Co jest już wbudowane

| Zagrożenie | Zabezpieczenie |
|---|---|
| Wyciek bazy z hasłami | Hasła hashowane **scrypt** z losową solą (wbudowane w Node). Nigdy nie są zapisywane jawnie. |
| Zgadywanie haseł | Limit 10 prób logowania na 15 min z jednego IP (licznik w bazie, działa na Vercel) **oraz** blokada konta na 15 min po 5 błędnych hasłach. |
| Słabe hasła | Minimum 12 znaków, litery i cyfry. |
| Sprawdzanie, kto ma konto | Ten sam komunikat dla złego e-maila i złego hasła, stały czas odpowiedzi. Rekrutacja zawsze odpowiada „przyjęto”. |
| Kradzież sesji | Losowy 256-bitowy token w ciasteczku `HttpOnly`, `Secure`, `SameSite=Lax` (`__Host-` w produkcji). W bazie trzymany jest tylko jego hash. Sesja wygasa po 7 dniach. |
| CSRF | Każda zmiana danych wymaga tokenu CSRF z sesji, a nagłówek `Origin` musi pasować do domeny. |
| XSS | Wszystkie dane z map i od użytkowników są escapowane. Ostra polityka **CSP** (`script-src 'self'`, bez skryptów inline). Linki ograniczone do `http(s)`. |
| Clickjacking | `X-Frame-Options: DENY`, `frame-ancestors 'none'`. |
| SSRF (sprawdzanie stron) | Tylko http/https i porty 80/443. Każdy adres IP z DNS jest sprawdzany i adresy prywatne lub lokalne (127.x, 10.x, 192.168.x, 169.254.x, IPv6 lokalne) są blokowane, także przy przekierowaniach. |
| Linki aktywacyjne | Jednorazowe, wygasają (72 h aktywacja, 24 h reset), w bazie trzymany jest tylko hash, a token jest od razu usuwany z paska adresu. |
| Boty w rekrutacji | Ukryte pole „honeypot” i limit 5 zgłoszeń na godzinę z jednego IP. |
| Nadużycia API | Limity wyszukiwań i sprawdzeń stron na użytkownika. Twardy miesięczny limit Google. |
| Dostęp do cudzych danych | Każdy lead jest przypisany do właściciela, a panel admina wymaga roli `admin`. Pokrywają to testy. |
| Zablokowany użytkownik | Blokada od razu kasuje wszystkie jego sesje. |

## Co musisz zrobić przy wdrożeniu (checklista)

1. **`BASE_URL=https://biznescreator.pl`** w zmiennych środowiskowych Vercel, bo od tego zależą linki aktywacyjne. HTTPS, HSTS i ciasteczka `Secure` Vercel włącza automatycznie.
2. **Token Turso trzymaj tylko w zmiennych Vercel** i w lokalnym `.env`. Nigdy nie wrzucaj go do gita (`.env` jest w `.gitignore`), na czat ani na zrzuty ekranu. Jeśli wycieknie, w panelu Turso unieważnij stare tokeny i wygeneruj nowy.
3. **Konto Vercel, GitHub i Turso:** włącz weryfikację dwuetapową (2FA) na wszystkich trzech. Kto przejmie GitHuba lub Vercel, przejmie stronę.
4. **Repozytorium na GitHubie ustaw jako prywatne.**
5. **Konto admina:** długie, unikalne hasło, najlepiej z menedżera haseł.
6. **Linki aktywacyjne** wysyłaj tylko na e-mail z formularza. Nie wrzucaj ich na publiczne czaty.
7. **Kopie zapasowe:** Turso w darmowym planie robi kopie automatycznie (przywracanie do punktu w czasie). Raz w miesiącu możesz też zrobić własną kopię: `turso db shell biznescreator .dump > kopia.sql`.
8. **Aktualizacje:** co miesiąc `npm audit` i `npm update`, a potem commit i push. Vercel sam wdroży nową wersję.
9. **Klucz Google (jeśli dodasz):** w Google Cloud ogranicz go tylko do *Places API (New)*, ustaw alert budżetowy na 1 zł i dzienny limit zapytań. To dodatkowa ochrona obok limitu w aplikacji.
10. **Vercel Firewall:** w razie ataku w panelu Vercel → Firewall możesz włączyć „Attack Challenge Mode” jednym kliknięciem.

## Polecane dodatki (na później)

- **Weryfikacja dwuetapowa (2FA/TOTP) dla admina.** Mogę ją dodać w kolejnym kroku.
- **Własna wysyłka e-maili** (np. darmowy plan Brevo lub Resend), żeby linki aktywacyjne wysyłały się automatycznie.
- **Monitoring** (UptimeRobot, darmowy), który powiadomi Cię, gdy strona przestanie działać.
- **Plan Vercel:** darmowy plan Hobby jest według regulaminu Vercel przeznaczony do użytku niekomercyjnego. Gdy BiznesCreator zacznie zarabiać, przejdź na Vercel Pro albo przenieś aplikację na VPS (kod działa też jako zwykły serwer: `npm start`).
- **RODO:** dodaj stronę z polityką prywatności, bo zbierasz e-maile i dane osób, także niepełnoletnich. Opisz, jakie dane zbierasz i po co, jak długo je trzymasz i jak poprosić o usunięcie. Usuwaj odrzucone zgłoszenia np. po 6 miesiącach.
- **Dane firm z map** są publiczne, ale przy kontakcie telefonicznym lub mailowym z firmami pamiętaj o przepisach o marketingu bezpośrednim. Pierwszy kontakt najlepiej telefonicznie lub osobiście.
