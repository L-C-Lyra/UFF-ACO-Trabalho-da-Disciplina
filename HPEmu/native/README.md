# Native audio core (YM2608 / OPNA)

The HP16's sound chip is emulated by [ymfm](https://github.com/aaronsgiles/ymfm)
(Aaron Giles' OPN/OPL core, BSD-3), wrapped in a small C ABI and reached from
Java through the Foreign Function & Memory API (`Ym2608Native`).

## Layout

- `ymfm/` — vendored ymfm sources needed for the OPNA (FM + SSG + ADPCM).
- `hpym2608.cpp` — C ABI wrapper: one opaque chip handle, timer/IRQ handling,
  ADPCM-B RAM, optional rhythm ROM, and resampling to the host output rate.
- `build.sh` — compiles everything into
  `src/main/resources/native/<platform>/libhpym2608.so` so it ships on the
  classpath and is extracted at runtime.

## Building

```sh
./native/build.sh        # needs g++ (C++17)
```

Run this once before launching the emulator (the `.so` is git-ignored). If the
library is missing or fails to load, the emulator falls back to a silent stub.

## Clock & rate

- Master clock: **7.987200 MHz** — the canonical OPNA value (PC-8801 Sound
  Board II), independent of the HP16 CPU. Timer A/B periods derive from it.
- Native ymfm rate: clock/48 (lowest fidelity); the wrapper resamples to
  **48 kHz** for output.

## Not yet wired

- ADPCM-A **rhythm ROM** is not bundled (copyright). `hpym_set_adpcma_rom`
  exists to load it later; until then the 6 rhythm channels are silent.
