# Auto Sable Physics User and Configuration Guide

This is a concise English handoff guide. The complete Chinese player/configuration guide is `AutoSablePhysics-模组介绍与配置说明.md`.

## Purpose

Auto Sable Physics listens to block changes, records affected areas, splits candidate blocks into face-connected components, and submits unsupported components to a delayed Sable assembly queue. It avoids full-world scanning and throttles Sable sub-level creation to reduce synchronization failures.

## Major features

- Event-driven automatic Sable assembly.
- Affected-region SavedData to avoid swallowing natural terrain.
- Component-level scan instead of origin-by-origin Sable gather.
- Delayed assembly queue with Sable tracking gates.
- Vanilla FallingBlockEntity routing for selected single-block components.
- Auto restore for Auto Sable Physics-created sub-levels.
- Grid-aligned fast restore.
- Sable hammer: pin mode and restore mode.
- Stationary sub-level no-collision crush/placement blocking.
- Configurable support/connectivity tags for mod compatibility.

## Configuration

All gameplay configuration is SERVER config. See the Chinese guide for the exhaustive table generated from `ASPServerConfig.java`.

## Compatibility

Prefer data-pack tags over code hooks: `immobile`, `ignored`, `force_supporting`, `non_supporting`, `force_connecting`, `non_connecting`, and the limited-horizontal-connectivity tags.
