# Documentation: RailScout Telemetry Mod
**Project:** OneDest SwitchBoard  
**Role:** Automated Geospatial & Material Surveyor  
**Status:** Core Specification  

---

## 1. Executive Summary
**RailScout** is a client-side Fabric mod designed to act as a high-fidelity "black box" recorder for the OneDest rail network. Its primary purpose is to eliminate manual data entry for rail maintenance. By analyzing a player’s movement in real-time, it identifies the exact coordinate-stamps of uncoppered rail segments and captures routing intent via chat-hooking.

The output of RailScout is a **Survey Report (JSON)**, which is ingested by the SwitchBoard web tool to update the global rail map and maintenance heatmap.

---

## 2. Core Responsibilities

### A. Motion Telemetry
The mod tracks the player’s position and velocity with sub-block precision. 
- **Frequency:** Samples every 1 to 5 ticks (configurable) while the player is in a `MinecartEntity`.
- **Velocity Tracking:** Captures horizontal magnitude ($\sqrt{v_x^2 + v_z^2}$) to determine the current speed tier.

### B. Material Heuristics (Copper Detection)
RailScout uses speed as a proxy for physical rail composition. 
- **The Binary Threshold:** 
    - **Speed > 11 m/s:** Classified as **Coppered**. (CivMC allows speeds up to 30 m/s on copper).
    - **Speed ≤ 11 m/s:** Classified as **Uncoppered**. (Vanilla rail/Cobble limit).
- **Segment Mapping:** Instead of averaging speed over a trip, RailScout identifies "Breakpoints." If a player travels at 30 m/s and then drops to 8 m/s, the mod marks the exact `BlockPos` where the transition occurred.

### C. Intent Capture (Chat Hooking)
To correlate physical movement with routing logic, RailScout monitors the outgoing chat buffer.
- **Trigger:** Any command matching the regex `^/dest\s+(.*)`.
- **Function:** It captures the destination string and associates it with the current recording session. This allows the SwitchBoard to verify if the physical routers actually sent the player where they intended to go.

---

## 3. Technical Implementation Details

### State Machine Logic
RailScout operates on a simple state machine to ensure clean data:
1.  **IDLE:** Player is on foot. No data is recorded.
2.  **RECORDING:** Triggered when `EntityMountEvent` detects the player entering a Minecart.
3.  **SAMPLING:** Every $N$ ticks, it pushes a `LogPoint` {x, y, z, v} to a local buffer.
4.  **SEGMENTING:** If the current speed crosses the 11 m/s threshold for more than 3 consecutive samples (to avoid noise from lag), it closes the current segment and starts a new one.
5.  **EXPORT:** Triggered on `EntityDismountEvent`. The buffer is serialized to JSON and saved to `.minecraft/railscout/surveys/`.

### Key Mixins & Hooks
- `net.minecraft.client.network.ClientPlayerEntity`: Used to intercept outgoing `/dest` commands.
- `net.minecraft.client.world.ClientWorld`: Used to query block data (optional) or lighting if needed.
- `net.minecraft.entity.Entity`: Used to monitor velocity and mounting states.

---

## 4. Data Structure: The Survey Report
The generated JSON is designed to be "snapped" to the global graph by the SwitchBoard backend.

```json
{
  "survey_metadata": {
    "timestamp": "2023-10-27T14:30:00Z",
    "player": "RailEngineer01",
    "destination_intent": "-,+ occident icenia icenia-city"
  },
  "raw_path": [
    {"x": 1000, "y": 64, "z": -500, "v": 29.8},
    {"x": 1010, "y": 64, "z": -500, "v": 30.1}
  ],
  "detected_segments": [
    {
      "start": [1000, 64, -500],
      "end": [1250, 64, -500],
      "type": "COPPERED",
      "avg_speed": 29.5
    },
    {
      "start": [1250, 64, -500],
      "end": [1300, 64, -500],
      "type": "UNCOPPERED",
      "avg_speed": 8.2
    }
  ]
}
```

---

## 5. Explanation for Future Work (Developer Guide)

### Handling "False Slowdowns"
Future versions must account for slowdowns not caused by missing copper (e.g., steep inclines, sharp turns, or colliding with another player).
*   *Requirement:* Integrate a check for `player.horizontalCollision`. If the player hits a block, the resulting speed drop should not mark the rail as "Uncoppered."

### Automated Uploading
The current iteration requires manual JSON upload to the SwitchBoard website.
*   *Future Work:* Implement an OAuth2 flow within the mod to allow players to "Sync to OneDest" directly from an in-game GUI after a ride.

### Visual Overlay (HUD)
Maintainers need real-time feedback.
*   *Requirement:* Implement a "Survey HUD" that shows a live speed graph and a "Current Rail Status" indicator (Green/Red text) so the rider knows exactly where the mod is marking a segment break.

---

## 6. Execution Instructions for AI Agents

**Prompt for RailScout Core Logic:**
> Develop a Fabric 1.21.x client-side Mixin in Java. 
> 1. Target `ClientPlayerEntity` to monitor velocity. 
> 2. Implement a `List<LogPoint>` to store coordinates and horizontal speed. 
> 3. Create a logic gate: if speed > 11.0, set state to `COPPERED`; else `UNCOPPERED`. 
> 4. When the state flips, store the current coordinate as a `SegmentBreak`. 
> 5. Implement a chat listener for `/dest` and capture the arguments. 
> 6. On dismount, use GSON to write the `survey_metadata`, the full path, and the identified segments to a file. 
> 7. Ensure the mod handles world-reloads and server-switches without crashing.