# GEMBET — fake kasyno na gemy 💎

Fikcyjne kasyno w stylu Shuffle / Stake. **Tylko wirtualne gemy — żadnych prawdziwych pieniędzy.**
Start: 10 000 💎, bonus dzienny +1 000 💎, reset salda w Portfelu. Saldo i statystyki zapisują się w `localStorage`.

## Uruchomienie
Czysty HTML/CSS/JS, bez builda:

    python3 -m http.server 8000   # i otwórz http://localhost:8000

## Gry (18)
- **Originals:** Mines, Dice, Limbo, Coinflip, Hi-Lo, Pump, Plinko, Crash, Keno, Wheel, Towers, Chicken
- **Stołowe:** Roulette, Blackjack
- **Sloty (5x3, 10 linii, wild, scatter, free spins ×2, kup bonus):** Gem Rush, Fruit Fiesta, Smocze Skarby, Księga Faraona

Wyniki z `crypto.getRandomValues`, przewaga kasyna ~1% w Originals (tabele wypłat Plinko/Keno ≈ 99% RTP), sloty ≈ 96% RTP.

## Struktura
- `js/core.js` — portfel, RNG, feed zakładów, komponenty UI, rejestr gier
- `js/games1.js`, `games2.js`, `games3.js` — gry; `js/slot-engine.js` — logika slotów (testowalna w node)
- `js/app.js` — router, lobby, statystyki, portfel
