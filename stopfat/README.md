# StopFat 🔥

Android app for losing weight with AI, built in a white "liquid glass" style with gradients and lots of animation (Kotlin + Jetpack Compose).

## Features

**Nutrition**
- Daily food log split into breakfast, lunch, dinner and snacks, with a calorie ring and macros.
- Built-in database of 150+ products, plus your own products.
- Barcode scanner (CameraX + ML Kit). Products are looked up in Open Food Facts, and the barcode is remembered.
- AI photo scan: AI (Gemini or Claude) recognises the meal, estimates grams, kcal and macros, and you can edit them before saving.
- Meal description as text ("2 jajka i kromka chleba z masłem"). AI handles it, or the offline Polish parser does when there is no key.
- 26 fit recipes with calories and macros, plus "Co mam w lodówce?", where AI writes a recipe from your ingredients.
- Copy meals from the previous day, and see recently added products.

**Training and activity**
- AI workout plan for the gym or for home (equipment, level, goal, days per week, session length, injuries), with an offline generator as a fallback.
- Workout mode: tick off sets, rest timer, and calories burned are saved.
- Log other activities (walking, running, cycling, swimming…) using MET values.
- Step counter from the phone's built-in sensor (TYPE_STEP_COUNTER). It also counts in the background through WorkManager and handles phone restarts.

**Weight-loss analysis**
- BMR (Mifflin–St Jeor), TDEE, BMI and estimated body fat.
- Exact plan to reach X kg by a set date. A simulation accounts for metabolism slowing as weight drops, and computes the required deficit.
- Rates the pace (gentle / optimal / ambitious / too aggressive), gives a safe target date, and how many extra steps are needed when the diet alone is not enough.
- Weight forecast chart, milestones and recommendations.
- Detailed AI analysis covering calories, monthly stages, movement, a sample menu and what to do when weight stalls.

**Insulin resistance mode**
- A switch in onboarding and settings. It sets a low-GI diet, marks the glycemic index (IG) of products in the food log and search, and counts the daily glycemic load (target < 80).
- Shows carbohydrates per meal (≤ 45 g), suggests lower-GI swaps for high-GI products, and lists diet rules for insulin resistance.
- Filters products and recipes by "niski IG" (low GI).
- AI (photo, description, recipes, Coach, analysis and plans) adapts its answers to insulin resistance.

**Extras**
- AI Coach chat that knows your goal and what you ate today.
- Water tracking, intermittent fasting (16:8, 18:6, 20:4, OMAD) with fasting stages, weight log with a chart.
- 7-day calorie and step charts, 18 achievements and a logging streak.
- Reminders (meals, water, end of fast).

## AI

Two providers are available, selectable in **Ustawienia → Połączenie z AI**:
- **Google Gemini (default, free)**: a key from https://aistudio.google.com/apikey. The default model is `gemini-flash-latest`, and you can also fetch the list of models available for your key. If the chosen model is unavailable, the app automatically falls back to other Flash models.
- **Anthropic Claude (paid)**: a key from https://console.anthropic.com/settings/keys, using the official Anthropic Java SDK (Opus 5.5 / Sonnet 5.5 / Haiku 5.5).

Without a key, everything except the photo scan, AI Coach, fridge recipes and AI analysis works offline.

## Build

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```
Requires JDK 17+ and the Android SDK (compileSdk 35). Release signing uses `keystore/stopfat-release.jks` when present; otherwise the debug key is used.
