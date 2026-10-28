# Offline routing: status and plan

**Status in V1: not implemented.** Every region shows "Offline routing not available for this
region". HAVEN never draws an estimated or straight-line route as if it were navigation.

## Why it is not in V1

A vector-tile map is not a routing graph. Tiles drop or simplify what routing depends on: one-way
rules, turn restrictions, access tags (private roads, footways), road connectivity at junctions and
precise geometry. Routing on tile geometry would produce routes that look plausible and are wrong,
which is dangerous in an emergency app.

A real solution needs:

1. A routing engine that runs on Android with local data and a licence compatible with GPL-3.0.
2. A pipeline that builds its graph from the same OSM extract as the tiles, preserving road
   attributes and turn restrictions.
3. Versioned graph data checked against the engine version (the manifest's `engine` and
   `engineVersion`), downloaded and verified like other components.
4. Testing on real routes in the starter region before the feature is shown as available.

## Candidates to evaluate

| Engine | Notes to check |
|---|---|
| GraphHopper (Apache-2.0) | Java; has run on Android in the past. Check current Android support, memory use and graph size. |
| Valhalla (MIT) | C++ with tiled graphs; used on mobile by other projects. Needs NDK integration. |
| BRouter (MIT) | Android app with routing segments; check embedding options. |

## Integration points already in the code

- `data/maps/Routing.kt`: `RoutingEngine`, `RouteRequest`, `RouteResult` (route, no route, unavailable).
- `OfflineRouting.supportedEngines`: empty in V1. An engine registers its id and supported data
  versions here; `Capabilities` then reports `READY` only for packs with matching routing data.
- `ManifestValidator`: routing components must declare `engine` and `engineVersion`.

Features to build after an engine is chosen: start and destination selection (from places search
or the map), route calculation, route display, turn instructions, location tracking, off-route
detection with rerouting, distance and clear failure messages.
