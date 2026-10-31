# FillIt (Android)

FillIt recommends places to visit during the free slots in your calendar, based on your preferred categories, budget, and travel mode.

The backend lives in [fillit-server](https://github.com/tuguldur77/fillit-server).

## Features

- Personalized place recommendations based on free time, location, and preferences
- Keyword and category search
- Favorites with AI-generated search keywords (Google Gemini)
- Google Sign-In, with favorites synced through Firestore
- Falls back to a default Seoul location when location access is unavailable

## Tech stack

- Kotlin, Jetpack Compose
- MVVM with the Repository pattern
- Retrofit, Android DataStore
- Firebase Auth, Cloud Firestore
- Google Gemini API, Google Maps Platform

## Getting started

1. Open the project in Android Studio.
2. Add your own Firebase config as `app/google-services.json`.
3. Add your API keys to `local.properties`:

   ```properties
   GEMINI_API_KEY=your_key
   MAPS_API_KEY=your_key
   ```

4. Run the `app` configuration on a device or emulator.
