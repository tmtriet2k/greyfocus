# GreyFocus

Android app that turns the screen greyscale whenever you open a chosen app, or open a chosen
website in your browser, and restores colour the moment you leave. Less colour, less pull.

## Download

Grab the latest `GreyFocus-vX.Y.Z.apk` from the
[Releases](https://github.com/tmtriet2k/greyfocus/releases) page, install it, then grant the one
adb permission described below. Release APKs are built on the maintainer's machine and signed
with a stable local key, so newer releases install over older ones.

## How it works

| Piece | Role |
| --- | --- |
| `FocusAccessibilityService` | Receives window events, so it always knows the foreground package. For browsers it reads the address bar (Chrome `url_bar`, Firefox, Brave, Edge, Samsung Internet, Opera, Vivaldi, Kiwi, DuckDuckGo) and extracts the domain. |
| `GreyscaleController` | Flips Android's built-in colour correction to "monochromacy" through `Settings.Secure`. This is the same toggle as Settings > Accessibility > Colour correction, so it affects everything on screen including browser content. It saves your previous colour-correction state and restores exactly that. |
| `Prefs` | Master switch, chosen packages, chosen domains. |
| `MainActivity` / `AppPickerActivity` | Setup checklist, live status, app picker with search, website list. |

Matching rule for websites: `youtube.com` matches `youtube.com` and any subdomain such as
`m.youtube.com`, but not `notyoutube.com`.

The screen returns to colour when the screen turns off, when the service is disabled, and when
the master switch is turned off, so the phone can never get stuck grey.

## Why adb is needed once

Changing colour correction requires `WRITE_SECURE_SETTINGS`. Android only lets adb (or root)
grant it, and there is no in-app prompt. It is a one-time step that survives reboots but not
uninstalling the app.

## Build and install

Requirements on the Mac: JDK 17 (`brew install openjdk@17`), Android command-line tools and
platform-tools (`brew install --cask android-commandlinetools android-platform-tools`), and the
SDK packages `platforms;android-35` and `build-tools;35.0.0` installed under
`~/Library/Android/sdk` (see `local.properties`).

On the phone: enable Developer options, then USB debugging, and plug it in.

```sh
./scripts/install.sh
```

The script builds the APK, installs it, grants the permission, and launches the app.
Manual equivalent:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.indiedev2k.greyfocus android.permission.WRITE_SECURE_SETTINGS
```

Then in the app:

1. Tap **Open settings** and enable **GreyFocus greyscale** under Accessibility.
   Both setup rows on the main screen should read ON / granted.
2. **Test greyscale for 3 s** confirms the permission works.
3. **Choose apps** to pick apps, and add domains under **Greyscale websites**.

## Notes and limits

- If Android 13+ shows "Restricted setting" when enabling the accessibility service, open
  App info for GreyFocus, tap the three-dot menu, choose **Allow restricted settings**, and try
  again. Installing over adb normally avoids this.
- Some phones kill accessibility services aggressively. If it stops working, exclude GreyFocus
  from battery optimisation.
- Browser detection reads only the address bar text. The URL is never stored or sent anywhere.
- Full-screen video hides the browser toolbar. GreyFocus keeps the last decision until the
  toolbar is visible again.
- Incognito tabs in Chrome still expose the address bar, so they are matched as well.

## Releasing

```sh
./scripts/release.sh v1.1.0
```

Builds the release APK, tags the commit, pushes the tag and creates a GitHub release with the
APK attached. CI (`.github/workflows/build.yml`) also builds a debug APK on every push to `main`
as a compile check.

## License

MIT
