# 🧩 Lustx Patches

> **Lustx Patches by sqzass**  
> Custom patches for use with the Morphe app patcher.

## ❓ About

**Lustx Patches** is a custom patch bundle by **sqzass**, built for compatibility with the [Morphe](https://morphe.software) patcher ecosystem and based on prior work of the Morphe and [ReVanced](https://github.com/ReVanced) projects.

### How to use these patches

Add this repository source to Morphe Manager:
`https://github.com/sqzass/lustx-patches`

---

## 🛠️ Tools & Development

This repository includes a local helper tool for decompiling Android apps and split packages (`.apk`, `.apks`, `.xapk`, `.apkm`, `.aab`, `.zip`) to inspect Java sources, Smali bytecode, and Android resources when developing custom patches:

- **Decompile all app packages in `apks/`**:
  ```bash
  python scripts/decompile.py
  ```

_Note: All app files placed in `apks/` are processed and automatically deleted once decompilation successfully completes._

&nbsp;

## 🩹 Patches list

<!-- PATCHES_START -->
> **[v1.17.3](https://github.com/sqzass/lustx-patches/releases/tag/v1.17.3)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;23 patches total
<details>
<summary>📦 Pawxy - Fast VPN & Web Browser&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.15.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock premium](#unlock-premium) | Unlocks Pawxy premium features (VPN servers, removes paywall and upsell badges). |  |

</details>

<details>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;22 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Allow screen recording](#allow-screen-recording) | Clears FLAG_SECURE and disables secure surfaces so the app can be screen-recorded. |  |
| [Allow screenshots](#allow-screenshots) | Clears FLAG_SECURE from window flags so the app can be captured in screenshots. |  |
| [Allow text selection](#allow-text-selection) | Flips setTextIsSelectable(false)/setLongClickable(false) to true so text can be selected and copied. |  |
| [Bypass root / emulator detection](#bypass-root-emulator-detection) | Forces common root and emulator detection methods to report clean. Covers Java/Kotlin-level checks in app code; library packages and native (.so) detection are not affected. |  |
| [Change package name](#change-package-name) | Renames the app package (appends ".lustx" by default) and updates custom permissions and content-provider authorities. Changing the package name can break apps that hardcode their package or authorities in code. | • Package name<br>• Update permissions<br>• Update provider authorities |
| [Change version name / code](#change-version-name-code) | Rewrites the manifest versionName/versionCode and the baked-in BuildConfig values. 'Current' keeps the existing value. | • Version name<br>• Version code |
| [Custom font](#custom-font) | Overrides the app's typeface with a built-in font family. Affects explicit Typeface usage; views that never set a typeface keep the system default. | • Font family |
| [Disable boot start-up](#disable-boot-start-up) | Prevents the app from auto-starting on device boot by removing boot intent-filter actions and the RECEIVE_BOOT_COMPLETED permission. | • Also block update start-up |
| [Force dark / AMOLED theme](#force-dark-amoled-theme) | Forces AppCompat night mode to YES and (optionally) flips AMOLED/pure-black flags. Covers AppCompat/Material apps; Jetpack Compose themes are not affected. | • Enable AMOLED / pure-black |
| [Forced-update bypass](#forced-update-bypass) | Neuters in-app forced-update gates (dialogs, blocking checks, version prompts). Works on apps that keep readable method names; fully-obfuscated apps need per-app fingerprints. |  |
| [Hide app icon](#hide-app-icon) | Hides the app from the launcher by replacing the LAUNCHER category with DEFAULT. The app stays installed; launch it via another app or a shortcut. |  |
| [Remove PairIP](#remove-pairip) | Removes the PairIP Play licensing gate (startup license check, response validation, repeated checks and paywall/kill path). Does not bypass PairIP native virtualization or Play Integrity attestation. |  |
| [Remove rate-us / review prompts](#remove-rate-us-review-prompts) | Suppresses in-app rating/review dialogs and gates. Optionally blocks the Play Core system review dialog (may crash apps that don't null-check the returned Task). | • Block Play Core review dialog |
| [Rename app label](#rename-app-label) | Sets a literal launcher name on <application>, launcher activities and aliases. 'Current' keeps the existing label. | • App label |
| [Skip splash](#skip-splash) | Skips splash/intro screens by running their deferred navigation immediately and removing artificial sleeps. Scoped to splash-named Activity/Fragment classes. | • Convert splash delays to immediate |
| [Spoof device identity](#spoof-device-identity) | Unified device spoofing with per-category toggles: Android ID, MAC address, SIM/carrier, and Build device fields. Includes emulator presets. Replaces the former individual SpoofAndroidId, SpoofSim, and SpoofDevice patches. | • Spoof Android ID<br>• Android ID value<br>• Spoof MAC address<br>• MAC address value<br>• Spoof SIM / carrier<br>• SIM preset<br>• Spoof device / Build fields<br>• Device preset<br>• Also spoof OS version |
| [Spoof install source](#spoof-install-source) | Makes the app believe it was installed from a chosen store (Play Store by default). | • Install source |
| [Spoof rooted environment](#spoof-rooted-environment) | Makes Java-level root checks report rooted: forces detection methods to return true and overrides Build.TAGS/USER/HOST. For testing bypass patches (e.g. PortDroid). Native detection is not affected. | • Override Build fields |
| [Strip VPN checks](#strip-vpn-checks) | Hides an active VPN from the app by neutralizing NetworkCapabilities transport/capability checks and (optionally) network-interface name checks. | • Also strip interface-name checks |
| [Strip sensitive permissions](#strip-sensitive-permissions) | Removes selected sensitive permissions from the manifest. Can break features that rely on them; enable only the groups you want stripped. | • Location<br>• Contacts & accounts<br>• SMS & call log<br>• Phone<br>• Camera & microphone<br>• Storage / media<br>• Sensors & activity<br>• Calendar<br>• Nearby devices<br>• Notifications |
| [Telemetry / crash-reporter kill](#telemetry-crash-reporter-kill) | Silences analytics, crash-reporting and telemetry SDKs (Firebase, Sentry, Adjust, Mixpanel, Amplitude, Segment, AppMetrica, Bugly, ACRA, etc.). | • Also kill SDK initialization |
| [Universal cleanup](#universal-cleanup) | Removes junk metadata files and (optionally) strips debug logging calls. | • Strip debug logging<br>• Remove Kotlin module metadata |

</details>

<!-- PATCHES_END -->

## Contributing

We currently accept pull requests for fixes/improvements to the Pawxy patch.
If you have developed patches for other apps, you are encouraged to release your patches in a third party repository.
See the [Patches template](https://github.com/morpheapp/morphe-patches-template) for more information.

Before opening a pull request, read the [contribution guidelines](CONTRIBUTING.md).

## 📜 License

Lustx Patches are licensed under the [GNU General Public License v3.0](LICENSE), with additional conditions under GPLv3 Section 7 preserving upstream notices and disclaimers.

- **Attribution (7b):** Any use of this code, including derivative works, must preserve all original notices and disclaimers.
- **Name & Branding Restrictions (7c & 7e):** Derivative works must use their own distinct branding. The **"Morphe"** name, logos, and trademarks may not be used for the branding or title of derivative works (e.g., names like _"Morphe Plus"_, _"Morphe Expanded"_, or _"Morphe UserXYZ"_ are strictly prohibited).

See the [LICENSE](LICENSE) file for full GPLv3 terms and the [NOTICE](NOTICE) file for full conditions of GPLv3 Section 7 and upstream attributions.
