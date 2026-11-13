# Ports: other game versions and loaders

The mod is written once, in `../src`, for Minecraft 26.x on Fabric. `gen.py` turns that code into the code of each port
below; nothing in `<port>/src` is edited by hand (it is generated and ignored by git).

| Port | Game | Loader | Java | Check |
|---|---|---|---|---|
| `fabric-1.21.11` | 1.21.11 | Fabric + Fabric API | 21 | `runClientGameTest` (the same test as 26.x) |
| `fabric-1.21.1` | 1.21 – 1.21.1 | Fabric + Fabric API | 21 | `runSelftest` |
| `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1 | 21 | `runSelftest` |
| `fabric-1.20.1` | 1.20.1 | Fabric + Fabric API | 17 | `runSelftest` |
| `forge-1.20.1` | 1.20.1 | Forge 47 | 17 | `runSelftest` |

```bash
python ports/gen.py                       # generate every port (or name one)
cd ports/neoforge-1.21.1
../../gradlew build                       # build/libs/*.jar
../../gradlew runSelftest                 # opens the game, clicks through the keyboard, saves run/screenshots, closes
```

Gradle runs on JDK 25 (`JAVA_HOME`); the ports compile for their own Java level, and the Forge and NeoForge ones fetch
their JDK themselves.

What the rules in `gen.py` cover:

- **1.21.6 – 1.21.11:** the drawing class and its methods under their names before 26.1, Fabric API's old key binding names.
- **before 1.21.6:** a 3D pose stack, tooltips drawn at once.
- **before 1.21.9:** key presses and clicks as plain numbers (the records in `shared/classic/.../input` give the 26.x
  code the methods it calls), categories as strings, a screen gets its `minecraft` late.
- **1.20.1:** other packages of the options screens, the background drawn by the screen, one scroll axis.
- **NeoForge, Forge:** no Fabric API; the entry points are in `<port>/overrides`, the mixin of the old key handler in
  `shared/classic-fabric` (Forge and NeoForge read mixins too).

The self-test (`shared/classic/.../SelfTest.java`) runs only with `-Dbeezzkeys.selftest=true`: it opens the keyboard
from the title screen, right-clicks a key, pins one, holds Ctrl, opens the gear, scrolls the list, opens the plain list
and comes back, saving a screenshot at each step. The log says `BeezzKeys selftest: OK` or the step that failed.

Release file names say the loader and the version, `beezzkeys-<mod>-<loader>-<game>.jar`, so the download box on
beezzland.com sorts them right.
