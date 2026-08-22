STRONA — MONIKA SZEWCZYK MAKE-UP
=================================

STRUKTURA (7 podstron)
  index.html      - strona glowna (hero, o mnie, skrocona oferta,
                    6 zdjec z galerii, kadry z pracy, opinie)
  oferta.html     - wszystkie 6 uslug + jak przebiega rezerwacja
  slub.html       - pakiet slubny 2027 (3000 zl) + warunki + FAQ
  szkolenia.html  - lekcja makijazu i szkolenie PRO
  cennik.html     - pelny cennik + FAQ
  galeria.html    - 12 zdjec, klikalne (powiekszenie) + opinie
  kontakt.html    - dane kontaktowe, jak przebiega rezerwacja, FAQ

  img/            - wszystkie zdjecia
  assets/style.css - wyglad calej strony (jeden plik dla 7 podstron)
  assets/app.js    - menu, galeria, animacje

  WAZNE: wyglad i skrypty sa teraz w folderze assets/, wspolne dla
  wszystkich podstron. Poprawka w jednym miejscu zmienia cala strone.
  Przegladarka pobiera te pliki raz - kolejne podstrony otwieraja sie
  szybciej.

TELEFON — NA CZYM SIE SKUPILISMY
  - Pasek akcji na dole ekranu: „Zadzwon" i „Rezerwuj" zawsze pod
    kciukiem, pojawia sie po pierwszym przewinieciu
  - Menu na caly ekran: duze pozycje, numeracja, telefon i social
    media na dole, zamykanie klawiszem Escape lub krzyzykiem
  - Gorny pasek chowa sie przy przewijaniu w dol i wraca przy
    przewijaniu w gore - wiecej miejsca na tresc
  - Oferta i opinie jako karuzela przesuwana palcem (na komputerze
    zwykla siatka)
  - Cennik: nazwa po lewej, cena po prawej, opis pod spodem -
    czytelny nawet na waskim ekranie
  - Powiekszanie zdjec: strzalki i licznik na dole, w zasiegu kciuka,
    przesuwanie palcem w bok, zamykanie Escape
  - Wszystkie przyciski i linki maja min. 44-52 px wysokosci
  - Uwzglednione wciecia ekranow z „notchem" (safe-area)
  - Zadnych poziomych przewijan na zadnej szerokosci ekranu
    (sprawdzone od 360 px do 1600 px)

WARSTWA WIZUALNA (animacje i efekty)
  - Naglowek na stronie glownej wjezdza slowo po slowie
  - Tresc pojawia sie kaskadowo przy przewijaniu
    (zdjecia z lekkim powiekszeniem, tekst z przesunieciem)
  - Cena pakietu slubnego nalicza sie od zera
  - Zlota linia przebiega przez separatory sekcji
  - Dwie cieple aury swiatla, powoli dryfujace w tle
  - Karty uslug unosza sie, zdjecie w srodku sie przybliza
  - Wiersze cennika: podkreslenie rysowane od lewej

  TYLKO NA KOMPUTERZE (zeby telefon dzialal plynnie):
  - Unoszacy sie pylek na plotnie canvas
  - Swiatlo podazajace za kursorem
  - Pasek postepu czytania na gorze ekranu
  - Paralaksa zdjec przy przewijaniu

  WYDAJNOSC: na telefonie nie uruchamiamy ciezkich efektow.
  Pylek zatrzymuje sie, gdy karta jest w tle.
  Opcja systemowa „ogranicz ruch": wszystkie efekty wylaczone,
  tresc widoczna od razu.
  Bez JavaScriptu cala tresc nadal jest widoczna.

JAK OPUBLIKOWAC (za darmo)
  1. Wejdz na  https://app.netlify.com/drop
  2. Przeciagnij CALY ten folder na strone
  3. Gotowe - dostajesz link. Mozna podpiac wlasna domene.
  WAZNE: przeciagnij folder, nie pojedyncze pliki -
  inaczej podstrony i wyglad nie beda sie ladowac.

JAK PODMIENIC ZDJECIE
  Wgraj nowe do folderu img/ pod ta sama nazwa.
  Nazwy: monika-hero, monika-about, monika-kwiat, slub2027,
         szkolenie, praca1, praca2,
         c-okolicz, c-slub, c-probny, c-lekcja,
         c-szkolenie, c-poprawiny, g1...g12
  Najlepsze proporcje:
    monika-hero            - poziome, twarz w gornej czesci kadru
    monika-about, monika-kwiat - pionowe
    c-*                    - poziome 3:2
    g1...g12               - pionowe 3:4
    praca1, praca2         - poziome 3:2

JAK ZMIENIC CENE
  Ceny sa w cennik.html (tabela) oraz w oferta.html i index.html
  (karty uslug). Szukaj kwoty np. „300 zl" i popraw w kazdym miejscu.

JAK ZMIENIC KOLORY
  Wszystkie kolory sa na poczatku pliku assets/style.css
  w sekcji „TOKENY" - wystarczy podmienic tam jedna wartosc.

DANE KONTAKTOWE NA STRONIE
  Studio: ul. Jana Kilinskiego 11, 26-610 Radom
  Telefon: 537 800 187
  E-mail:  monika.szewczyk.makeup@gmail.com

ZMIANY ZAMOWIONE PRZEZ MONIKE (wprowadzone)
  - Usuniete zdjecie w lustrze (prywatne) - zastapione portretem
  - Ceny szkolen: 1-dniowy 1700 zl, 1-dniowy 2 rodzaje 2000 zl
  - Pakiet Slubny 2027 (3000 zl) - JEDYNY pakiet slubny:
      dojazd 100 km w cenie, powyzej 4 zl/km
      zadatek 1000 zl + umowa, max 5 makijazy w dniu
      dodatkowa osoba +320 zl
      probne wylacznie soboty i niedziele, min. 3 mies. przed
      wspolpraca ze stylistka fryzur
  - Pakiet 2026 (2500 zl) USUNIETY na prosbe klientki
  - Nowe zdjecia: sesja z papierowym kwiatem, kadry z pracy
    przy klientce, panna mloda w welonie, portrety makijazy
  - FAQ opisuje warunki pakietu 2027

DECYZJE CENOWE PODJETE PRZY BUDOWIE (do zmiany w razie potrzeby)
  - Ceny z Booksy oznaczone „+" pokazane jako „od ... zl"
    (makijaz okolicznosciowy, slubny, probny, korekta Pana Mlodego)
  - „Makijaz w niedziele i swieta 310 zl" zostawiony ze starego
    cennika - NIE MA GO W BOOKSY, sprawdz czy nadal obowiazuje
  - Makijaz probny: 310 zl dotyczy uslugi kupowanej osobno,
    w pakiecie slubnym jest wliczony (dopisane pod cennikiem)
  - Upiecie i fale opisane jako „stylizacja wlosow" - w Booksy
    maja w nazwie imie Agata, wiec zalozylem ze robi je
    wspolpracujaca stylistka. Popraw jesli inaczej.

CO WARTO JESZCZE UZUPELNIC
  - polityka prywatnosci / cookies (RODO)
  - liczba lat doswiadczenia (teraz ogolne „od lat")
  - godziny otwarcia studia
  - pelny adres studia na stronie (teraz widnieje sam Radom)
