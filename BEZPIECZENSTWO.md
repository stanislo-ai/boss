# Bezpieczeństwo BiznesCreator

## Co jest już wbudowane

| Zagrożenie | Zabezpieczenie |
|---|---|
| Wyciek bazy z hasłami | Hasła hashowane **scrypt** z losową solą (wbudowane w Node). Nigdy nie są zapisywane jawnie. |
| Zgadywanie haseł | Limit 10 prób logowania na 15 min z jednego IP **oraz** blokada konta na 15 min po 5 błędnych hasłach. |
| Słabe hasła | Minimum 12 znaków, litery i cyfry. |
| Sprawdzanie, kto ma konto | Ten sam komunikat dla złego e-maila i złego hasła, stały czas odpowiedzi. Rekrutacja zawsze odpowiada „przyjęto”. |
| Kradzież sesji | Losowy 256-bitowy token w ciasteczku `HttpOnly`, `Secure`, `SameSite=Lax` (`__Host-` w produkcji). W bazie trzymany jest tylko jego hash. Sesja wygasa po 7 dniach. |
| CSRF | Każda zmiana danych wymaga tokenu CSRF z sesji i poprawnego nagłówka `Origin`. |
| XSS | Wszystkie dane z map i od użytkowników są escapowane. Ostra polityka **CSP** (`script-src 'self'`, bez skryptów inline). Linki ograniczone do `http(s)`. |
| Clickjacking | `X-Frame-Options: DENY`, `frame-ancestors 'none'`. |
| SSRF (sprawdzanie stron) | Tylko http/https i porty 80/443. Każdy adres IP z DNS jest sprawdzany i adresy prywatne lub lokalne (127.x, 10.x, 192.168.x, 169.254.x, IPv6 lokalne) są blokowane, także przy przekierowaniach. |
| Linki aktywacyjne | Jednorazowe, wygasają (72 h aktywacja, 24 h reset), w bazie trzymany jest tylko hash, a token jest od razu usuwany z paska adresu. |
| Boty w rekrutacji | Ukryte pole „honeypot” i limit 5 zgłoszeń na godzinę z jednego IP. |
| Nadużycia API | Limity wyszukiwań i sprawdzeń stron na użytkownika. Twardy miesięczny limit Google. |
| Dostęp do cudzych danych | Każdy lead jest przypisany do właściciela, a panel admina wymaga roli `admin`. Pokrywają to testy. |
| Zablokowany użytkownik | Blokada od razu kasuje wszystkie jego sesje. |

## Co musisz zrobić przy wdrożeniu (checklista)

1. **HTTPS obowiązkowo.** Użyj certbota / Let's Encrypt (patrz README). Ustaw `NODE_ENV=production`, wtedy włączają się ciasteczka `Secure` i HSTS.
2. **`BASE_URL=https://biznescreator.pl`** dokładnie tak, jak wpisują go użytkownicy. Od tego zależy ochrona CSRF (nagłówek Origin). Jeśli używasz też `www.`, przekieruj `www` → bez `www` w nginx.
3. **`TRUST_PROXY=1`** za nginx/Cloudflare. Bez tego limity prób liczyłyby wszystkich jako jedno IP.
4. **Aplikacja słucha tylko na `127.0.0.1`** (domyślne `HOST`). Na zewnątrz ma być widoczny tylko nginx.
5. **Firewall:** otwórz tylko porty 22, 80 i 443 (`sudo ufw allow OpenSSH && sudo ufw allow 'Nginx Full' && sudo ufw enable`).
6. **SSH:** logowanie kluczem, wyłącz logowanie hasłem i rootem (`PasswordAuthentication no`, `PermitRootLogin no`), zainstaluj `fail2ban`.
7. **Kopie zapasowe bazy** `data/biznescreator.db` raz dziennie, np. crontab:
   `0 3 * * * sqlite3 /sciezka/data/biznescreator.db ".backup '/backup/bc-$(date +\%F).db'"`. Kopie trzymaj poza serwerem.
8. **Uprawnienia plików:** `chmod 600 .env data/*.db`. Uruchamiaj aplikację na osobnym użytkowniku, nie jako root.
9. **Aktualizacje:** co miesiąc `npm audit` i `npm update` oraz `sudo apt upgrade` na serwerze.
10. **Konto admina:** długie, unikalne hasło (najlepiej z menedżera haseł). Nie używaj go nigdzie indziej.
11. **Linki aktywacyjne** wysyłaj tylko na e-mail z formularza. Nie wrzucaj ich na publiczne czaty.
12. **Klucz Google (jeśli dodasz):** w Google Cloud ogranicz go do *Places API (New)* i do IP serwera, ustaw alert budżetowy na 1 zł oraz dzienny limit zapytań w konsoli Google. To dodatkowa ochrona obok limitu w aplikacji.

## Polecane dodatki (na później)

- **Cloudflare (darmowy plan)** przed serwerem: ochrona przed DDoS i botami, ukrycie IP serwera. Ustaw tryb SSL „Full (strict)”.
- **Weryfikacja dwuetapowa (2FA/TOTP) dla admina.** Mogę ją dodać w kolejnym kroku.
- **Własna wysyłka e-maili** (np. darmowy plan Brevo lub Resend), żeby linki aktywacyjne wysyłały się automatycznie.
- **Monitoring** (UptimeRobot, darmowy), który powiadomi Cię, gdy strona przestanie działać.
- **RODO:** dodaj stronę z polityką prywatności, bo zbierasz e-maile i dane osób, także niepełnoletnich. Opisz, jakie dane zbierasz i po co, jak długo je trzymasz i jak poprosić o usunięcie. Usuwaj odrzucone zgłoszenia np. po 6 miesiącach.
- **Dane firm z map** są publiczne, ale przy kontakcie telefonicznym lub mailowym z firmami pamiętaj o przepisach o marketingu bezpośrednim. Pierwszy kontakt najlepiej telefonicznie lub osobiście.
