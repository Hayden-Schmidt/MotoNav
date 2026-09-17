# Side-loading an offline basemap (Phase E)

The route-selection map (`RouteSelectionMapScreen`) renders local PMTiles through MapLibre
Native's `pmtiles://` support. **No basemap data ships with the app** — a usable NZ cutout is
still hundreds of MB to a few GB, and the full planet PMTiles archive is ~120GB (plan
`MotoNav_REBUILD_PLAN_OSM.md` Phase E item 2). Until a file is side-loaded, the map screen still
works — it just shows a plain dark background behind the destination pin.

## Where the app looks

`com.motonav.app.map.OfflineMapTiles` reads a single file:

```
<external files dir>/nz.pmtiles
```

i.e. on a typical device:

```
/sdcard/Android/data/com.motonav.app/files/nz.pmtiles
```

This is `getExternalFilesDir(null)`, **not** `assets/`. MapLibre Native only supports
`pmtiles://file://` against a real filesystem path — `pmtiles://asset://` is not supported. That's
why the file has to be side-loaded onto the device rather than bundled into the APK.

## Getting a cutout

1. Get (or build) a Protomaps "basemap" PMTiles archive — see https://maps.protomaps.com/builds/
   or run `pmtiles` yourself against a planetiler/protomaps-basemaps build.
2. Extract an NZ-sized bounding box with the `pmtiles` CLI:

   ```
   pmtiles extract https://build.protomaps.com/YYYYMMDD.pmtiles nz.pmtiles \
     --bbox=165.5,-47.5,179.0,-34.0
   ```

3. Push it onto the device:

   ```
   adb push nz.pmtiles /sdcard/Android/data/com.motonav.app/files/nz.pmtiles
   ```

   (or copy the same file over USB/MTP into that path — same mechanism as the Phase C Valhalla
   tile side-load in `ride/LocalRouteProvider.kt`'s `LocalTileFiles`.)

## Style assumptions

`OfflineMapTiles.styleJson()` styles against the Protomaps basemap source-layer schema (`water`,
`landuse`, `roads`, `buildings`, `boundaries`, `places`). If your cutout uses a different schema
(e.g. raw OpenMapTiles), those layers simply draw nothing — the map still loads, just blank. Fixing
that is a styling exercise against whatever schema you actually side-load, not a code change to
the loading path.
