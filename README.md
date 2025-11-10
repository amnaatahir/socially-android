# Socially

An Instagram-style social media app for Android, built with Kotlin. Socially lets
people sign up, share photo posts, follow each other, post and view stories,
chat in real time, and start audio/video calls.

Repository: https://github.com/amnaatahir/socially-android

## Features

- **Authentication** — email/password sign up and login backed by Firebase Auth.
- **Feed & Explore** — scrollable photo feed with likes and comments, plus an
  explore/search grid to discover other users and posts.
- **Posts** — create posts from the camera or gallery, like and comment.
- **Profiles** — view profiles, edit your own profile, manage followers and
  following lists.
- **Stories** — add stories, view stories from people you follow, and save
  highlights.
- **Direct Messaging** — real-time one-to-one chat with presence (online/last
  seen) powered by Firebase Realtime Database.
- **Audio & Video Calling** — in-app calling with incoming-call handling, built
  on the Agora RTC SDK.
- **Push Notifications** — message and activity notifications via Firebase Cloud
  Messaging (FCM HTTP v1).

## Tech Stack

- **Language:** Kotlin
- **UI:** Android Views, ConstraintLayout, Material Components, RecyclerView
- **Backend:** Firebase (Authentication, Realtime Database, Storage, Cloud
  Messaging)
- **Real-time calling:** Agora RTC SDK
- **Image loading:** Glide
- **Build system:** Gradle (Kotlin DSL) with the Gradle wrapper
- **Testing:** JUnit4, AndroidX Test, Espresso, UI Automator

## Screenshots

> _Screenshots coming soon._
>
> | Feed | Profile | Stories | Chat |
> | ---- | ------- | ------- | ---- |
> |      |         |         |      |

## Build Instructions

### Prerequisites

- Android Studio (latest stable, with the bundled JDK)
- Android SDK with API level 36 installed
- A device or emulator running Android 7.0 (API 24) or higher

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/amnaatahir/socially-android.git
   ```
2. Open the project folder in **Android Studio** (`File > Open`) and let Gradle
   sync finish.
3. Create a `local.properties` file in the project root if Android Studio does
   not generate one. It must point at your SDK location, e.g.:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
4. Select a device/emulator and click **Run** (or use `./gradlew installDebug`).

### Firebase setup

This app talks to Firebase. The bundled `app/google-services.json` contains the
client configuration (a restricted, public client identifier) so the project
builds out of the box against the existing Firebase project. To point it at your
own Firebase project, replace `app/google-services.json` with the file generated
in your Firebase console for the package `com.example.smd_a1`, and enable
Authentication, Realtime Database, Storage, and Cloud Messaging.

> **Note on secrets:** server-side FCM credentials (the service-account private
> key) are **not** committed. Provide them through a gitignored
> `local.properties` entry (`fcm.serviceAccount.privateKey=...`), which is read
> into `BuildConfig` at build time. For production, the FCM HTTP v1 send flow
> should run on a trusted backend rather than on the client.

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file
for details.
