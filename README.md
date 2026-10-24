# Super Bomberman 4 Recomp

A native Android port of **Super Bomberman 4** (Japan), built on
[SNESRecomp](https://github.com/RetroPortingToolKit/snesrecomp), SDL3, the Android NDK, and a native touch-controller activity. The Android app does not use Expo, WebView, or an emulator frontend.

> **Private test build.** The source tree excludes raw ROM files. The APK under `artifacts/` embeds the ROM supplied by the user for authorized private testing; keep this repository private and do not redistribute the APK or game data.

## Status

An Android arm64 debug APK was compiled on 2026-10-24. The APK contains native `libmain.so` and SDL3 libraries, the supplied ROM, and the license/attribution documents listed in the in-app Licenses screen. Touch controls are implemented in the native Android activity, but gameplay and touch input have **not** been verified on a physical device.

**Measured AOT progress in the current analysis scope:** 405 of 493 instruction instances across five selected roots are marked AOT-eligible (**82.2%**); 21 of 23 manifest variants (**91.3%**) are AOT-eligible. These are scoped manifest metrics, not the percentage of the full game or ROM completed. Unmapped 65C816 execution still uses the SNESRecomp interpreter fallback. Differential testing matched video/VRAM through frame 18; WRAM first differed at frame 19, while the image remained identical through frame 32. Full gameplay parity is not claimed. See [Porting from here](#porting-from-here).

## ROM identity

| | |
|---|---|
| File | `Super Bomberman 4 (Japan).sfc` |
| Mapping | hirom |
| Region | JPN (Japan) |
| Coprocessor | none |
| CRC32 | `3bbaeb19` |
| SHA-256 | `bdd90b49bf08d0d0a80e4b4ec811084d470355c648c49a28a0e838cbf1ea8f92` |

`tools/regen.sh` refuses to run against a different ROM revision. The raw ROM is excluded by `.gitignore`; the private APK artifact embeds the user-supplied ROM.

## Build

Initialize the framework and re-apply the Android SDL3 linkage patch saved in this repository (it only changes Android builds; it was not pushed to the public SNESRecomp upstream):

```sh
git submodule update --init --recursive
git -C snesrecomp apply ../patches/snesrecomp-android-sdl-shared.patch
```

For the desktop host, provide the matching ROM and build:

```sh
bash tools/regen.sh --rom "/path/to/Super Bomberman 4 (Japan).sfc"
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
```

For Android, place the authorized ROM at the ignored path `android/app/src/main/assets/rom.sfc`, then build the debug APK:

```sh
bash tools/regen.sh --rom "/path/to/Super Bomberman 4 (Japan).sfc"
cd android
./gradlew assembleDebug
```

The deliverable in `artifacts/SuperBomberman4-Android-Native.apk` is a private test binary and includes the ROM. Do not change repository visibility to public while that artifact remains present.

## Run

```sh
./build/SuperBomberman4SNESRecomp                     # launcher picks the ROM
./build/SuperBomberman4SNESRecomp "/path/to/Super Bomberman 4 (Japan).sfc"
```

With no ROM on the command line the build opens the recomp-ui launcher: a ROM picker with a verification badge, plus display/audio/input settings. Your choice is cached in `rom.cfg` beside the executable. The ROM is checked against the digests above before anything boots.

## Layout

| Path | What lives there |
|---|---|
| `recomp/` | Analysis input: bank configs and `symbols.toml` |
| `src/` | Native host code, including Android input integration |
| `src/gen/` | Generated C; ignored and regenerated locally |
| `snesrecomp/` | Upstream framework submodule |
| `patches/` | Reproducible local Android SDL3 framework patch |
| `android/` | Native Android/SDL3 app, touch controls and license assets |
| `artifacts/` | Private APK deliverable; embeds the user-provided ROM |
| `tools/` | ROM verification and recompilation pipeline |

## Porting from here

1. **Make it boot.** `src/game_rtl.c` holds the frame driver. It uses the framework's beam-aligned driver and raster journal.
2. **Name things.** Add entries to `recomp/symbols.toml` as routines are identified, then re-run `tools/regen.sh`. Set `emit = true` only for proven variants; unresolved code remains interpreted.
3. **Resolve dispatch misses.** Handle unresolved indirect targets before promoting more code.
4. **Never synthesize a result** to get past uncovered code, and never edit `src/gen/` by hand. Fix the config/framework and regenerate.

## Multiplayer

Two players, one controller per port.

## License and notices

This project's own source uses the license in `LICENSE`. The framework has separate terms in `snesrecomp/LICENSE` and `snesrecomp/THIRD_PARTY_ATTRIBUTION.md`. The Android app also bundles SDL3, Android NDK/toolchain notices, a GPLv3 reference text with an applicability note, and a ROM-provenance notice. The GPL reference cores are not bundled in the APK runtime.

The ROM and game content are not licensed by this repository's software license. The raw ROM is not committed; the private APK artifact does contain the ROM supplied by the user and is not a public release or a grant of redistribution rights.
