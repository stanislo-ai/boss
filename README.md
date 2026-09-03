# Aura — asystent głosowy AI na Androida

Osobisty asystent głosowy działający na telefonie, oparty o **darmowy klucz Gemini API**.
Rozumie mowę po polsku, odpowiada głosem i — co najważniejsze — **naprawdę steruje telefonem**:
dodaje wydarzenia do kalendarza, ustawia budziki i przypomnienia, wysyła SMS-y, sprawdza pogodę,
uruchamia aplikacje, prowadzi nawigację i pamięta fakty o Tobie między rozmowami.

Interfejs jest minimalistyczny, utrzymany w bieli, z płynnymi animacjami (żywa „aura” reagująca
na głos, wjeżdżające wiadomości, miękkie przejścia między ekranami).

Testowany pod kątem: **Pixel 7 / Android 14–16**, budowany z poziomu **CachyOS (Arch Linux)**.

---

## Spis treści

1. [Co potrafi](#1-co-potrafi)
2. [Krok 1 — darmowy klucz Gemini API](#2-krok-1--darmowy-klucz-gemini-api)
3. [Krok 2 — przygotowanie CachyOS](#3-krok-2--przygotowanie-cachyos)
4. [Krok 3 — zbudowanie aplikacji](#4-krok-3--zbudowanie-aplikacji)
5. [Krok 4 — instalacja na Pixelu 7](#5-krok-4--instalacja-na-pixelu-7)
6. [Krok 5 — pierwsze uruchomienie](#6-krok-5--pierwsze-uruchomienie)
7. [Jak używać na co dzień](#7-jak-używać-na-co-dzień)
8. [Rozwiązywanie problemów](#8-rozwiązywanie-problemów)
9. [Prywatność i limity](#9-prywatność-i-limity)
10. [Jak to jest zbudowane](#10-jak-to-jest-zbudowane)

---

## 1. Co potrafi

Aura ma **37 narzędzi**, które model wywołuje samodzielnie w trakcie rozmowy
(Gemini Function Calling). Możesz łączyć je w jednym zdaniu — asystent wykona je po kolei.

| Obszar | Co powiesz | Co się stanie |
|---|---|---|
| **Kalendarz** | „Dodaj spotkanie z Anną jutro o 15 w kawiarni” | Wpis trafia prosto do Twojego kalendarza Google wraz z przypomnieniem |
| | „Co mam dzisiaj?”, „Jakie mam plany w tym tygodniu?” | Odczytuje wydarzenia z wybranego zakresu dni |
| | „Usuń to spotkanie” | Kasuje wydarzenie |
| **Czas** | „Ustaw budzik na 6:30 w dni robocze” | Budzik w systemowym Zegarze, także cykliczny |
| | „Minutnik na 8 minut na jajka” | Odliczanie w Zegarze |
| | „Przypomnij mi za godzinę o praniu” | Powiadomienie z aplikacji Aura (przetrwa restart telefonu) |
| **Notatki i pamięć** | „Zapisz notatkę: lista zakupów…” | Notatka w zakładce *Twoje dane* |
| | „Zapamiętaj, że jestem weganinem” | Trwały fakt dołączany do każdej przyszłej rozmowy |
| **Kontakty** | „Napisz SMS do Kasi, że się spóźnię 10 minut” | Otwiera Wiadomości z gotową treścią — wysyłasz jednym kliknięciem |
| | „Zadzwoń do mamy” | Otwiera dialer z numerem |
| | „Napisz maila do jan@example.com w sprawie faktury” | Otwiera klienta poczty z gotową wiadomością |
| **Pogoda** | „Jaka będzie pogoda w weekend w Krakowie?” | Aktualna pogoda i prognoza do 7 dni (Open-Meteo, bez dodatkowego klucza) |
| **Aplikacje** | „Włącz Spotify”, „Jakie mam aplikacje do zdjęć?” | Uruchamia aplikację po nazwie (odporne na polskie znaki) |
| **Mapy** | „Prowadź do Galerii Krakowskiej rowerem” | Nawigacja Google Maps |
| | „Pokaż apteki w pobliżu” | Wyszukiwanie na mapie |
| **Telefon** | „Ile mam baterii?” | Bateria, sieć, wolne miejsce, tryb dzwięku, głośności |
| | „Włącz latarkę”, „Ścisz do 20%”, „Tryb wibracji” | Sterowanie sprzętem |
| | „Pauza”, „Następny utwór” | Sterowanie odtwarzaczem multimediów |
| | „Otwórz ustawienia Wi-Fi” | Skrót do konkretnego ekranu ustawień |
| **Internet** | „Wyszukaj przepis na żurek” | Otwiera wyniki w przeglądarce |
| **Schowek** | „Skopiuj to do schowka”, „Udostępnij ten tekst” | Schowek i systemowe udostępnianie |

Dodatkowo:

- **Rozmowa ciągła** — po odpowiedzi mikrofon włącza się sam (tryb hands-free).
- **Kafelek w szybkich ustawieniach** — jedno dotknięcie i mówisz.
- **Domyślny asystent systemu** — możesz przypisać Aurę do gestu wywołania asystenta.
- **Udostępnianie tekstu do Aury** — zaznacz tekst w dowolnej aplikacji → *Udostępnij* → Aura.
- **Rozpoznawanie mowy offline** (Pixel radzi sobie z tym świetnie) — opcja w ustawieniach.

---

## 2. Krok 1 — darmowy klucz Gemini API

1. Wejdź na **<https://aistudio.google.com/app/apikey>** i zaloguj się kontem Google.
2. Kliknij **Create API key** (możesz utworzyć klucz w nowym projekcie — nie wymaga karty).
3. Skopiuj klucz — wygląda jak `AIzaSy...`.

Klucz wpiszesz później w aplikacji. Nie musisz go nigdzie umieszczać w kodzie
i **nie trafia on do repozytorium ani do kopii zapasowej Google**.

> Plan bezpłatny działa od ręki. Domyślnie aplikacja używa modelu `gemini-3.6-flash`.
>
> **Lista modeli pobierana jest na żywo z API**, więc Aura nie zdezaktualizuje się, gdy Google
> wycofa albo doda model. Gdyby zapisany model przestał istnieć, aplikacja sama znajdzie następcę
> i powtórzy pytanie — bez potrzeby aktualizowania aplikacji. W Ustawieniach możesz odświeżyć
> listę ręcznie i wybrać np. `gemini-3.5-flash-lite`, jeśli odbijasz się od limitów.

---

## 3. Krok 2 — przygotowanie CachyOS

Wszystko poniżej to jednorazowa konfiguracja. Kopiuj polecenia po kolei.

### 3.1. Java i narzędzia podstawowe

```bash
sudo pacman -S --needed jdk21-openjdk android-tools unzip git
```

- `jdk21-openjdk` — do kompilacji (działa też `jdk17-openjdk`),
- `android-tools` — daje `adb` i `fastboot`,
- reszta jest oczywista.

Ustaw Javę 21 jako domyślną (jeśli masz kilka wersji):

```bash
sudo archlinux-java set java-21-openjdk
java -version   # powinno pokazać 21.x
```

### 3.2. Android SDK

Nie potrzebujesz całego Android Studio. Wystarczą oficjalne narzędzia wiersza poleceń:

```bash
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
curl -L -o cmdline-tools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdline-tools.zip
mv cmdline-tools latest
rm cmdline-tools.zip
```

Dodaj zmienne środowiskowe (dla `bash` — jeśli używasz `fish` lub `zsh`, wpisz je w swoim pliku konfiguracyjnym):

```bash
cat >> ~/.bashrc <<'EOF'

# Android SDK
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF

source ~/.bashrc
```

Zainstaluj wymagane komponenty i zaakceptuj licencje:

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

> **Wolisz Android Studio?** Zainstaluj je (`yay -S android-studio` albo z <https://developer.android.com/studio>),
> otwórz katalog `android/` jako projekt i kliknij *Run*. Studio samo pobierze SDK.
> Reszta tego poradnika opisuje szybszą drogę z terminala.

### 3.3. Dostęp do telefonu przez USB (udev)

Aby `adb` widział Pixela bez `sudo`:

```bash
sudo tee /etc/udev/rules.d/51-android.rules > /dev/null <<'EOF'
# Google / Pixel
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0660", GROUP="adbusers", TAG+="uaccess"
EOF

sudo groupadd -f adbusers
sudo usermod -aG adbusers "$USER"
sudo udevadm control --reload-rules && sudo udevadm trigger
```

**Wyloguj się i zaloguj ponownie** (albo zrestartuj komputer), żeby członkostwo w grupie zaczęło działać.

---

## 4. Krok 3 — zbudowanie aplikacji

```bash
git clone https://github.com/stanislo-ai/boss.git
cd boss/android
./gradlew assembleDebug
```

Pierwsze uruchomienie potrwa kilka minut — Gradle pobiera zależności. Na końcu zobaczysz `BUILD SUCCESSFUL`,
a gotowy plik znajdziesz w:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Wersja „release” (mniejsza i szybsza — zalecana do codziennego używania)

```bash
./gradlew assembleRelease
```

Plik: `android/app/build/outputs/apk/release/app-release.apk` (~3,2 MB zamiast ~62 MB).
Jest podpisany kluczem debugowym, więc instaluje się dokładnie tak samo — projekt jest przeznaczony
do prywatnego użytku, a nie do publikacji w Google Play.

### Sprawdzenie poprawności (opcjonalnie)

```bash
./gradlew testDebugUnitTest   # testy jednostkowe (format zapytań do Gemini, parsowanie dat)
./gradlew lintDebug           # analiza statyczna Androida
```

---

## 5. Krok 4 — instalacja na Pixelu 7

### 5.1. Włącz opcje programisty

1. *Ustawienia → Informacje o telefonie*
2. Stuknij **7 razy** w pozycję **Numer kompilacji** — pojawi się komunikat „Jesteś teraz programistą”.
3. Wróć do *Ustawienia → System → Opcje programisty*.
4. Włącz **Debugowanie USB**.

### 5.2. Podłącz telefon i zainstaluj

```bash
adb devices
```

Na telefonie pojawi się okno **„Zezwolić na debugowanie USB?”** — zaznacz *Zawsze zezwalaj z tego komputera*
i potwierdź. Ponownie uruchom `adb devices` — status musi brzmieć `device` (nie `unauthorized`).

Instalacja:

```bash
# wersja release (zalecana)
adb install -r app/build/outputs/apk/release/app-release.apk

# albo wersja debug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Komunikat `Success` oznacza, że Aura jest już na liście aplikacji.

> **Bez kabla?** Skopiuj plik `.apk` na telefon (np. przez `adb push`, chmurę albo Bluetooth),
> otwórz go menedżerem plików i zezwól tej aplikacji na instalowanie nieznanych aplikacji.

### Aktualizacja do nowszej wersji

```bash
cd boss && git pull
cd android && ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Flaga `-r` zachowuje Twoje ustawienia, notatki i pamięć asystenta.

---

## 6. Krok 5 — pierwsze uruchomienie

1. **Otwórz Aurę** i przejdź do **Ustawień** (ikona suwaków w prawym górnym rogu).
2. **Wklej klucz Gemini API** w pierwszym polu. Przycisk *Pobierz klucz* otwiera stronę Google AI Studio.
3. Zjedź do sekcji **Uprawnienia i integracje** i kliknij **Przyznaj uprawnienia**.
   Android zapyta kolejno o:
   - **Mikrofon** — bez tego nie ma sterowania głosem (wymagane),
   - **Kalendarz** — dodawanie i odczyt wydarzeń,
   - **Kontakty** — wyszukiwanie numerów przy SMS-ach i telefonach,
   - **Lokalizacja** — pogoda „u mnie” i pytania o miejsce,
   - **Powiadomienia** — przypomnienia.

   Wszystkie są opcjonalne poza mikrofonem — jeśli któreś odrzucisz, Aura po prostu powie,
   że danej rzeczy nie może zrobić. Gdy narzędzie natrafi na brakującą zgodę,
   aplikacja sama poprosi o nią w trakcie rozmowy.
4. Kliknij **Zezwól na dokładne alarmy** — dzięki temu przypomnienia zadziałają co do minuty.
5. *(Opcjonalnie)* **Ustaw Aurę jako domyślnego asystenta** — otworzy się ekran
   *Asystent i wprowadzanie głosowe*, gdzie wybierasz Aurę. Od tej pory wywołasz ją
   gestem/przyciskiem zamiast Asystenta Google.
6. *(Opcjonalnie)* **Dodaj kafelek**: rozwiń pasek powiadomień → ołówek/edycja →
   przeciągnij kafelek **Aura** na widoczną listę.

Wróć na ekran główny, dotknij mikrofonu i powiedz np.:

> „Dodaj do kalendarza wizytę u dentysty w piątek o 10 rano i przypomnij mi dzień wcześniej.”

---

## 7. Jak używać na co dzień

**Trzy sposoby na rozpoczęcie rozmowy:**

| Sposób | Kiedy wygodny |
|---|---|
| Ikona mikrofonu w aplikacji | Zwykłe użycie |
| Kafelek w szybkich ustawieniach | Szybki dostęp z dowolnego ekranu |
| Gest asystenta (jeśli ustawisz Aurę jako domyślną) | Najszybciej, także przy zablokowanym ekranie |

**Warte poznania ustawienia:**

- **Tryb rozmowy** — po każdej odpowiedzi mikrofon włącza się sam. Idealny podczas gotowania czy jazdy.
- **Wysyłaj od razu po dyktowaniu** — wyłącz, jeśli wolisz poprawić tekst przed wysłaniem.
- **Rozpoznawanie mowy offline** — szybsze i prywatniejsze. Wymaga pobrania pakietu polskiego
  w *Ustawienia Androida → System → Języki → Wprowadzanie głosowe → Rozpoznawanie mowy offline*.
- **Tryb głębokiego myślenia** — dokładniejsze odpowiedzi przy złożonych poleceniach,
  kosztem szybkości i większego zużycia limitu. Domyślnie wyłączony (asystent głosowy ma być szybki).
- **Personalizacja** — imię i własne instrukcje (np. „mów krótko i bez uprzejmości”).

**Zakładka „Twoje dane”** (ikona dokumentu) pokazuje wszystko, co Aura o Tobie zapisała:
notatki, zapamiętane fakty i zaplanowane przypomnienia. Każdy wpis możesz usunąć jednym kliknięciem.

---

## 8. Rozwiązywanie problemów

| Objaw | Przyczyna i rozwiązanie |
|---|---|
| `adb devices` pokazuje `unauthorized` | Odblokuj telefon i potwierdź okno debugowania USB. Jeśli się nie pojawia: `adb kill-server && adb start-server`, a w razie potrzeby *Opcje programisty → Cofnij autoryzacje debugowania USB*. |
| `adb devices` pokazuje pustą listę | Sprawdź, czy kabel przesyła dane (nie tylko ładuje), oraz czy wykonałeś krok z udev i **przelogowałeś się**. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Masz zainstalowaną wersję podpisaną innym kluczem: `adb uninstall com.stanislo.aura`, potem zainstaluj ponownie. |
| „Zapytanie odrzucone (400)” / „Brak dostępu (403)” | Zły albo nieaktywny klucz API. Wygeneruj nowy w Google AI Studio i wklej ponownie. |
| „Wybrany model nie jest już dostępny (404)” | Aura sama przełączy się na aktualny model i powtórzy pytanie. Gdyby tego nie zrobiła, wejdź w *Ustawienia → Model*, odśwież listę ikoną strzałki i wybierz pozycję oznaczoną jako „zalecany”. |
| „Przekroczono bezpłatny limit zapytań (429)” | Limit planu darmowego. Odczekaj minutę albo przełącz model na `gemini-2.5-flash-lite`. |
| Asystent nie słyszy / „Rozpoznawanie mowy wymaga internetu” | Włącz internet albo pobierz pakiet języka offline (patrz punkt 7). Sprawdź też zgodę na mikrofon. |
| Odpowiedzi nie są czytane na głos | *Ustawienia → Głos → Czytaj odpowiedzi na głos*. Jeśli nadal cicho, sprawdź *Ustawienia Androida → Ułatwienia dostępu → Zamiana tekstu na mowę* i pobierz polski głos. |
| Przypomnienia spóźniają się | Kliknij *Zezwól na dokładne alarmy* w ustawieniach Aury oraz wyłącz optymalizację baterii dla aplikacji. |
| Nie mogę zapisać wydarzenia w kalendarzu | Konto Google musi mieć zsynchronizowany kalendarz z prawem zapisu. Jeśli Aura go nie znajdzie, otworzy aplikację Kalendarz z gotowym formularzem. |
| `SDK location not found` przy budowaniu | Nie ustawiłeś `ANDROID_HOME`. Sprawdź `echo $ANDROID_HOME` albo utwórz `android/local.properties` z linią `sdk.dir=/home/TWOJA_NAZWA/Android/Sdk`. |
| Gradle nie pobiera zależności | Sprawdź połączenie i ewentualne proxy. Ponów z `./gradlew assembleDebug --refresh-dependencies`. |
| Podgląd logów aplikacji | `adb logcat --pid=$(adb shell pidof -s com.stanislo.aura)` |

---

## 9. Prywatność i limity

- **Co zostaje na telefonie:** klucz API, historia rozmów, notatki, zapamiętane fakty i przypomnienia.
  Wszystko leży w prywatnym katalogu aplikacji i jest wykluczone z kopii zapasowej Google.
- **Co wychodzi na zewnątrz:** treść rozmowy trafia do Gemini API Google (to warunek działania
  jakiegokolwiek modelu w chmurze), a zapytania o pogodę — do Open-Meteo. Nic więcej.
- **Ważne:** w **bezpłatnym** planie Gemini API Google może wykorzystywać przesyłane treści
  do ulepszania swoich modeli. Nie dyktuj Aurze rzeczy naprawdę poufnych.
- **Wysyłka wiadomości i połączenia są celowo półautomatyczne** — Aura przygotowuje SMS lub numer,
  ale ostatnie kliknięcie zawsze należy do Ciebie. Dzięki temu model nie wyśle nic bez Twojej wiedzy
  i aplikacja nie potrzebuje uprawnień `SEND_SMS` ani `CALL_PHONE`.
- **Aktualne limity** planu darmowego (zapytania na minutę i na dobę) sprawdzisz na
  <https://ai.google.dev/gemini-api/docs/rate-limits>.

---

## 10. Jak to jest zbudowane

**Stos:** Kotlin 2.2 · Jetpack Compose (Material 3) · Coroutines · kotlinx.serialization · OkHttp · DataStore
**Bez** bazy danych i generatorów kodu — dane trzymane są w plikach JSON, co maksymalnie upraszcza budowanie.

**Warstwa wizualna:** krój [Inter](https://rsms.me/inter/) (licencja OFL, dołączony w wersji okrojonej
do znaków łacińskich i polskich — 8 odmian, łącznie ok. 700 kB) jako najbliższy legalnie dostępny
odpowiednik systemowego San Francisco; własny komplet 14 ikon rysowanych wektorowo w kodzie;
system cząsteczek na klatkach animacji (`withInfiniteAnimationFrameMillis`) napędzający zarówno
kulę głosową, jak i delikatny pył w tle.

```
android/
├── app/src/main/kotlin/com/stanislo/aura/
│   ├── MainActivity.kt          # wejście, obsługa intencji ASSIST / SEND / PROCESS_TEXT
│   ├── ai/                      # klient Gemini, model danych API, 37 deklaracji narzędzi, prompt systemowy
│   ├── tools/                   # realne działania: kalendarz, czas, kontakty, aplikacje, urządzenie, pogoda
│   ├── speech/                  # rozpoznawanie mowy i synteza głosu
│   ├── data/                    # ustawienia (DataStore) i lokalny magazyn JSON
│   ├── system/                  # przypomnienia (AlarmManager), kafelek szybkich ustawień
│   └── ui/                      # ekrany i komponenty Compose + ViewModel z pętlą agenta
└── app/src/test/kotlin/         # testy formatu zapytań do Gemini i parsowania argumentów
```

**Jak działa jedna tura rozmowy** (`ui/AssistantViewModel.kt`):

1. Mowa → tekst (`SpeechManager`).
2. Zapytanie do Gemini z pełną historią, promptem systemowym i deklaracjami narzędzi.
3. Jeśli model zwrócił `functionCall` → `ToolRouter` wykonuje działanie na telefonie,
   a wynik wraca do modelu jako `functionResponse`. Pętla powtarza się do 6 razy,
   więc asystent może np. najpierw znaleźć kontakt, a potem napisać do niego SMS.
4. Gdy model odpowie samym tekstem — odpowiedź trafia na ekran i do syntezatora mowy.

Kluczowe decyzje projektowe:

- **Brak Room/KSP** — mniej ruchomych części, szybsza i pewniejsza kompilacja.
- **`thinkingBudget = 0`** dla modeli 2.5 — asystent głosowy musi odpowiadać natychmiast
  (można to przełączyć w ustawieniach).
- **Wyniki narzędzi są opisowe, nie surowe** — model dostaje po polsku informację, co się udało,
  a czego nie i dlaczego, dzięki czemu potrafi sensownie wytłumaczyć problem użytkownikowi.
- **Brakujące uprawnienie to nie błąd** — narzędzie zwraca `missing_permission`,
  a interfejs natychmiast prosi użytkownika o zgodę.
