# prayr 🙏

> A simple Android prayer companion by **blondothenerd**.

`prayr` helps keep prayer requests in rotation without turning them into another productivity dashboard.

<p align="center">
  <img src="docs/images/screenshot.png" alt="Prayr Android App" width="360">
</p>

[![Android CI](https://github.com/blondothenerd/prayr/actions/workflows/android.yml/badge.svg)](https://github.com/blondothenerd/prayr/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## What it does

- Add prayer requests with **Pray for**, **Reason**, and **Specifics**.
- Mark **Done** when a prayer has been prayed in the current cycle.
- Mark **Completed** manually when a prayer is no longer active.
- Automatically begin a fresh cycle after all active prayers have been prayed.
- Choose **random** or **sequential** prayer selection.
- Configure notification timing by **interval** or **randomised frequency**.
- Configure a daily notification window and notification count.
- Use notification actions such as **Done** and **Snooze**.
- Keep the experience intentionally small, quiet, and focused.

## Android Auto

The public project should use normal Android notification behaviour only. It should not pretend to be a messaging, calendar, or other app category just to unlock restricted Android Auto surfaces.

## Screenshots

Add screenshots to `docs/images/` and replace this section with a small gallery before the first public release.

## Build

### Requirements

- Android Studio
- JDK 17
- Android SDK matching the project's configured compile SDK

### Command line

```bash
./gradlew clean
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK will normally be written beneath:

```text
app/build/outputs/apk/debug/
```

## Public identity

| Item | Value |
|---|---|
| Project | `prayr` |
| Author / maintainer | `blondothenerd` |
| Suggested package ID | `dev.blondothenerd.prayr` |
| Suggested repository | `blondothenerd/prayr` |
| License | MIT |

## Privacy

Prayer content can be deeply personal. Keep the public build local-first and avoid analytics, advertising, remote logging, or cloud sync unless those features are explicitly documented and opt-in.

See [PRIVACY.md](PRIVACY.md).

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT. See [LICENSE](LICENSE).

---

Made by **blondothenerd**. 🙏
