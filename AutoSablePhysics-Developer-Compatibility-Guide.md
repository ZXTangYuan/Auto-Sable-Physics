# Auto Sable Physics Developer Compatibility Guide

This is a concise English compatibility handoff. The complete Chinese document is `AutoSablePhysics-开发者兼容说明.md`.

## Runtime expectations

- Minecraft 1.21.1, NeoForge 21.1.x, Java 21.
- Sable 1.2.2 and Sable Companion 1.6.0.
- Auto Sable Physics currently relies on Sable assembly internals and should pin Sable to `[1.2.2,1.3.0)`.

## Preferred compatibility mechanism

Use block tags rather than direct code dependencies:

- `autosablephysics:immobile`: never auto-assemble.
- `autosablephysics:ignored`: ignored and not valid support.
- `autosablephysics:force_supporting` / `non_supporting`: override support logic.
- `autosablephysics:force_connecting` / `non_connecting`: override component connectivity.
- `autosablephysics:limited_horizontal_connecting`: material has bounded horizontal connectivity.
- `leaf_horizontal_limited`, `granular_horizontal_limited`, `terrain_horizontal_limited`: specific bounded-connectivity families.

## BlockEntity guidance

If your block has a BlockEntity, ensure NBT save/load is stable after movement and restoration. If it cannot safely move, add it to `immobile` or block connectivity with `non_connecting`.

## Events

If your mod changes blocks through custom logic, prefer standard NeoForge block events or normal neighbor updates. Auto Sable Physics does not scan the whole world.

## Sub-levels

Auto Sable Physics ignores events inside existing Sable sub-levels by default. Do not rely on it to re-process internal block edits inside another physics object.
