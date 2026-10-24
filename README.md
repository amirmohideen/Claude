# FoldFX

Fold your phone. Feel things.

FoldFX turns the hinge of an Android foldable into a controller for small physical experiences.
The first one is a **creaky door**: fold slowly and the door groans open loud and long, fold fast
and it gives a quick little squeak, open the phone flat fast and the door slams shut with a thud
you can feel.

Built first for the Galaxy Z Fold7, and meant to work on any foldable that reports its hinge angle
(Android 11 and later).

## Try it on your phone

Every push builds an installable APK.

1. On your phone, open this repository's **Releases** and find **FoldFX test build**.
2. Download `FoldFX-test.apk` and open it. Android will ask you to allow installs from your
   browser or file manager the first time.
3. Turn the media volume up.

Test builds share one signing key, so each new one installs over the last.

### Please run the sensor lab first

Open **Sensor lab** at the bottom of the home screen. Fold slowly, fold fast, and slam the phone
flat a few times, then tap **Share report**. The report shows how often your phone reports the
hinge, how fine its steps are and what range it covers. Those numbers set how smooth the sounds
can be, so they decide the next round of tuning.

## How it works

| Part | Where | What it does |
| --- | --- | --- |
| Fold tracking | `core/.../FoldTracker.kt` | Turns raw hinge readings into angle, speed and slams. Copes with sensors that report in whole degrees and only on change. |
| Creak | `core/.../audio/CreakSynth.kt` | Synthesizes the creak live as stick-slip friction ringing wood resonances. Slow means heavy, loud slips; fast means a thin, quick squeak. Some angles stick harder, so the same spots creak every time. |
| Slam | `core/.../audio/SlamVoice.kt` | A thud, a wood knock and the latch clicking home. |
| Sound output | `app/.../AudioEngine.kt` | Low-latency audio stream that pulls a few milliseconds at a time, so sound follows the hinge. |
| Door art | `app/.../ui/DoorArt.kt` | The room and door, drawn in perspective so the door swings as you fold. |
| Sensor lab | `app/.../ui/SensorLabScreen.kt` | Live graph and a shareable report of how the phone reports its hinge. |

All sounds are synthesized in code; there are no audio files to license.

The `core` module is plain Kotlin with no Android dependencies, so the fold tracking and the sound
can be tested on any computer:

```
./gradlew :core:test           # fold tracking and sound tests
./gradlew :app:assembleDebug   # the APK (needs the Android SDK)
```

## Roadmap

- [x] Sensor lab
- [x] Creaky door (free)
- [ ] Tune the door to real Fold7 sensor data
- [ ] Crocodile chomp (free)
- [ ] Treasure chest, flip lighter, safe cracker (premium)
- [ ] One-time "unlock everything" purchase with Google Play Billing
- [ ] Other foldables: Pixel Fold line, Galaxy Z Flip, Motorola Razr, OnePlus Open and more
- [ ] Store listing, release signing, and a closed test with 12 testers for 14 days

## Before the first Play Store upload

- `applicationId` (`com.amirmohideen.foldfx`) can never change once published.
- Make a release signing key and keep it out of this repository (`keystore/release*` is ignored).
- Decide whether to require the hinge sensor in the manifest, based on the sensor lab's
  "Declares hinge-angle feature" line. Requiring it while a phone doesn't declare it would hide
  the app from that phone.
