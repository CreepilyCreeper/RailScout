# RailScout — Telemetry Handoff

## Summary
RailScout implements a client-side telemetry sensor for Fabric 1.21.8 that samples minecart movement to detect rail quality (COPPERED / UNCOPPERED) using a speed threshold. Data is exported as JSON on dismount for ingestion by OneDest / SwitchBoard.

## Implemented
- Client tick hook (Mixin) samples horizontal speed and block position.
- RLE-style segmentation to mark state changes (threshold 11 m/s).
- Chat listener captures `/dest` and stores it as Active Trip Metadata.
- Serializer produces JSON files: `railscout/survey_[timestamp].json` under the game directory.
- Telemetry: Now includes dimension, player UUID, and player name.
- Unit Testing: JUnit 5 tests for the segmentation logic.

## Key Files
- src/client/java/com/creepilycreeper/railscout/mixin/MinecartEntityMixin.java
  - Injects into AbstractMinecart.tick
  - Samples every 5 ticks
  - Detects state change (speed > 11m/s => COPPERED; <= 11m/s => UNCOPPERED)
  - Triggers export on dismount
- src/client/java/com/creepilycreeper/railscout/RailScoutClient.java
  - Registers ClientReceiveMessageEvents.CHAT
  - Parses messages starting with `/dest` and forwards to mixin
- src/main/java/com/creepilycreeper/railscout/data/SurveySerializer.java
  - Data classes: Sample, Segment, Survey
  - computeSegmentsFromSamples(List<Sample>, threshold)
  - writeSurveyFile(...) → writes pretty JSON to `railscout/survey_[timestamp].json`
- src/test/java/com/creepilycreeper/railscout/data/SurveySerializerTest.java
  - Unit tests for the segmentation logic.

## Build notes / current status
- Mappings: Project uses Mojang (official) mappings.
- Current status: Build and tests are passing.
- Run:
  - Windows: .\gradlew.bat build test --no-daemon --stacktrace

## How to test (manual)
1. Build and run the client in a Fabric dev environment (`runClient`).
2. In-game, ensure the RailScout client is loaded.
3. Enter a minecart and run `/dest <args>` in chat to set metadata.
4. Ride rails; telemetry samples every 5 ticks.
5. Dismount: check `<game_dir>/railscout/` for `survey_[timestamp].json`.

## Improvements made
- Fixed `sourcesJar` duplicate entry by moving `SurveySerializer` to `main` and removing placeholder.
- Added JUnit 5 and unit tests for `SurveySerializer`.
- Fixed a concurrency/shared state bug in `MinecartEntityMixin` by making tracking fields non-static.
- Added player identity and dimension metadata to telemetry.
- Cleaned up boilerplate code and unused mixin configs.

## Handoff contacts & context
- Repo: origin: https://github.com/CreepilyCreeper/RailScout.git
- Minecraft/Fabric versions: 1.21.8, Fabric loader 0.18.4, Fabric API 0.136.1+1.21.8
- Notes: The implementation intentionally keeps client-only logic in `src/client` and a small placeholder in `src/main` to avoid server-side compile errors.