# Radiation

A Fallout-style radiation mod for **Minecraft 26.3** (Fabric).

Admins mark out irradiated zones and place radiation sources. Players who walk into one get a red
**rad meter** in the top left corner showing how many RAD/s they are absorbing, hear a Geiger counter
clicking, and pass through stages of radiation sickness until they die at 1000 rads.

Online handbook: https://mchamradio.antwire.net/handbook/radiation/

## Installing with Prism Launcher

1. In Prism, create a new instance (**Add Instance**), choose Minecraft **26.3**, and pick **Fabric**
   as the mod loader (any recent loader version, at least 0.19.5).
2. Open the instance's **Edit → Mods** page and click **Download mods**. Search for **Fabric API** and
   install it. Radiation needs it.
3. Click **Add file** and pick `radiation-1.2.0.jar`, or drop the jar onto the mod list.
4. Start the instance.

For a multiplayer server, put the same jar plus Fabric API into the server's `mods` folder. Every
player needs the mod installed as well.

## How it works

| Accumulated rads | Default stage                 | Effects                                              |
|------------------|-------------------------------|------------------------------------------------------|
| 250              | Mild Radiation Sickness       | Hunger, −1 heart                                     |
| 500              | Radiation Sickness            | Hunger, Weakness, −2 hearts                          |
| 750              | Severe Radiation Sickness     | Hunger II, Weakness II, Slowness, Mining Fatigue, −4 hearts |
| 1000             | Death ("died of radiation poisoning") |                                              |

Rads don't wear off on their own (as in Fallout). You need RadAway, or you have to die. They reset on death.

### The rad meter

- **☢ RADS** with the current rate on the right. It pulses red while you take radiation and turns
  green while RadAway is flushing rads out.
- A segmented red bar from 0 to the maximum, with a coloured tick at each sickness stage.
- Absorbed / maximum rads, your current protection (**RES %**), and the name of your sickness stage.
- It appears when you are irradiated or treated and fades out a few seconds later. Holding a Geiger
  counter always shows it. It hides while the F3 debug screen is open.

### Items

| Item | What it does | Recipe |
|------|--------------|--------|
| **Geiger Counter** | Right-click for a detailed reading: ambient radiation, what you absorb, protection, condition. Holding it shows the meter. | Copper ingot (top right), iron / glass pane / iron, yellow dye / redstone / yellow dye |
| **RadAway** | Removes 150 rads over 10 seconds | Shapeless: glass bottle + dried kelp + glow berries + sugar |
| **Rad-X** | Blocks 50% of incoming radiation for 4 minutes | Shapeless: paper + amethyst shard + sugar + orange dye → 2 |
| **Hazmat Hood / Suit / Trousers / Boots** | Each piece blocks 22.5% (full set 90%) | Shaped like leather armor, using leather and yellow wool (the hood has a glass pane visor) |
| **Nuclear Waste Barrel** | A block that radiates its surroundings (6 rad/s, radius 6 by default). It glows and drips green particles. Creative/admin only. | none |

Protection from armor and Rad-X stacks, capped at 95% by default.

Solid blocks between you and a **point source** absorb radiation. By default each block removes 35%,
so hiding behind a thick wall helps. Zones are uniform and can't be shielded against.

## Vault blocks

Version 1.2.0 adds everything you need to build a Fallout-style fallout shelter.

![A Vault Door numbered 73 in its frame, with hazard stripes and a Vault Door Console beside it](docs/img/vault_door.jpg)

| Block | What it does |
|-------|--------------|
| **Vault Door** | The big cog door for a round 5×5 opening. Place it in the middle of the opening, facing the outside. A **screw arm** behind it extends, screws into the cog, pulls it back into the vault and lets go; the cog then rolls aside. Closing runs the other way. An alarm klaxon sounds the whole time. It is blast-proof. |
| **Vault Door Console** | A pedestal with a big button: opens or closes the nearest vault door within 16 blocks. A vault door also toggles on a redstone pulse. |
| **Vault Alarm Light** | A rotating amber warning light. It spins and lights up while a vault door within 16 blocks opens or closes. Mount it on any wall, floor or ceiling. |
| **Vault Sliding Door** | A two-block steel door that slides up into the wall above it. Doors standing side by side in the same wall open together, so you can build wider doorways. Opens by hand or redstone. |
| **Vault Wall Panel**, **Striped Vault Wall Panel**, **Vault Pipe Panel** | Riveted steel walls: plain, with the blue and yellow stripe, and with pipes. |
| **Vault Floor Plate**, **Vault Floor Grate** | Tread plate and see-through grating. |
| **Hazard Stripes**, **Vault Door Frame** | Yellow and black warning stripes; heavy frame blocks for the door opening. |
| **Vault Light Panel**, **Blue / Yellow / White Neon Tube** | Flat light panels and neon tubes for walls, floors and ceilings. |

![The screw arm waiting behind a closed vault door](docs/img/vault_arm_idle.jpg)

![The arm's screw head has engaged the door's centre](docs/img/vault_arm_screwing.jpg)

![The arm pulls the door back into the vault](docs/img/vault_arm_pulling.jpg)

![The door rolls aside while the arm parks](docs/img/vault_door_rolling.jpg)

**Room the door needs.** The door is 5 blocks across. Behind it (inside the vault) keep the space clear: **7 blocks deep** for the screw arm, **4 blocks above the door's centre** for the arm's ceiling mount, and **8 blocks to the side** the door rolls to. Corners of the 5×5 square stay solid; use Vault Door Frame blocks there.

**Number and roll direction.** Sneak and use the door to set the number painted on it (0 to 999) and which side it rolls to. Only operators and players in creative mode can do this.

![Setting the door's number](docs/img/vault_door_settings.jpg)

![Alarm lights sweep amber beams while the door opens](docs/img/vault_alarm_lights.jpg)

![A vault room with light panels, neon tubes, striped walls, a floor grate and sliding doors](docs/img/vault_room.jpg)

![A double sliding door halfway up](docs/img/vault_sliding_door.jpg)

### Vault recipes

| Item | Recipe |
|------|--------|
| Vault Door | 8 iron blocks around a redstone block |
| Vault Door Console | Stone button on top, glass pane / redstone / glass pane, 3 iron ingots |
| Vault Alarm Light | Glowstone dust, orange stained glass pane / redstone / orange stained glass pane, iron ingot |
| Vault Sliding Door ×2 | 5 iron ingots and a piston (iron, iron / piston, iron / iron, iron) |
| Vault Wall Panel ×8 | 8 smooth stone around an iron ingot |
| Striped Vault Wall Panel ×2 | 2 wall panels, yellow dye, blue dye |
| Vault Pipe Panel | Wall panel and a copper ingot |
| Vault Floor Plate | Wall panel and grey dye |
| Vault Floor Grate ×4 | 4 iron bars |
| Hazard Stripes | Wall panel, yellow dye, black dye |
| Vault Door Frame ×4 | 2 iron ingots and 2 obsidian, diagonally |
| Vault Light Panel ×2 | Iron nugget, glowstone, iron nugget |
| Neon Tube ×2 | Glass pane, glowstone dust and blue, yellow or white dye |

## Commands

`/rads` works for every player and shows your own rads and condition.

The rest need operator permission (or cheats enabled in singleplayer):

| Command | Description |
|---------|-------------|
| `/radiation zone add <name> <from> <to> <rads/s> [fade]` | Box-shaped area with a uniform radiation level. `fade` makes it ramp up over that many blocks from the edge. |
| `/radiation source add <name> <pos> <rads/s> <radius> [falloff] [shielded]` | Point source that weakens with distance. Falloff: `linear` (default), `quadratic`, `inverse_square`, `constant`. `shielded` (default `true`) controls whether walls block it. |
| `/radiation zone remove <name>` / `/radiation source remove <name>` | Remove a zone or source (tab-completes names) |
| `/radiation list` (or `zone list` / `source list`) | List everything |
| `/radiation show [seconds]` | Draws zones (green), sources (red) and barrels (yellow) with particles. `0` turns it off. |
| `/radiation check [pos]` | Radiation level at a position, broken down by zone/source |
| `/radiation get <player>` | Show a player's rads |
| `/radiation set <players> <amount>` / `add <players> <amount>` | Change rads (negative `add` removes) |
| `/radiation clear [players]` | Reset rads to 0 |
| `/radiation reload` | Reload the config and the sources file |

Examples:

```
/radiation zone add crater 100 50 100 160 90 160 15 5
/radiation source add reactor ~ ~1 ~ 40 20 quadratic
/radiation show 60
```

Zones and sources are saved per world in `<world folder>/radiation_sources.json`. You can also edit
that file by hand and then run `/radiation reload`.

## Configuration

Every value is configurable. The files are created on first start in the instance's `config` folder.
In Prism, that's **Edit → Minecraft folder → config**.

**`config/radiation.json`**: gameplay (on a server, the server's copy applies):

| Setting | Default | |
|---------|---------|-|
| `maxRads` | 1000 | Rads at which you die. The meter's scale. |
| `stages` | 250 / 500 / 750 | List of stages: `threshold`, `name`, `color` (ARGB hex), `maxHealthModifier` (half-hearts), `effects` (any effect id + `amplifier`, 0 = level I). Add, remove or rename freely. |
| `naturalDecayPerSecond` | 0 | Rads lost per second without treatment |
| `updateIntervalTicks` | 10 | How often radiation is calculated (20 = once per second) |
| `affectCreativeAndSpectator` | false | |
| `shieldingPerBlock` | 0.35 | Fraction removed by each solid block between a point source and you |
| `barrelRads`, `barrelRadius` | 6, 6 | Nuclear Waste Barrel strength |
| `protectiveItems` | hazmat pieces at 0.225 | Any item id → protection when worn. You can add other mods' armor here. |
| `radXResistance` | 0.5 | |
| `maxProtection` | 0.95 | Set to 1.0 to allow full immunity |
| `radAwayTotalRads` | 150 | |
| `radAwayDurationSeconds`, `radXDurationSeconds` | 10, 240 | Need a restart |
| `requireGeigerCounter` | false | If true, the RAD/s readout and the clicking only work while you carry a Geiger counter |

**`config/radiation-client.json`**: per-player display settings:

| Setting | Default | |
|---------|---------|-|
| `hudX`, `hudY`, `hudScale` | 6, 6, 1.0 | Position and size of the meter |
| `hudMode` | `EXPOSURE` | `EXPOSURE` (Fallout-like), `NONZERO` (whenever you have rads), `ALWAYS`, `HIDDEN` |
| `hudLingerSeconds` | 4 | How long the meter stays after exposure ends |
| `showWhileHoldingGeiger` | true | |
| `geigerVolume` | 0.6 | 0 mutes the clicks |
| `geigerClicksPerRad`, `geigerMaxClicksPerSecond` | 2.5, 60 | How busy the Geiger counter sounds |

## For mod developers

Other mods can use `dev.radiation.api.RadiationApi` (server side) to add and remove point sources, read the radiation at a position, and add rads to players, with or without their protection. [RedButton](https://github.com/Lamisator/mc_redbutton) uses it to irradiate ground zero after nuclear detonations.

## Building from source

Requires JDK 25.

```
./gradlew build
```

The mod jar ends up in `build/libs/radiation-1.2.0.jar`. `./gradlew runClient` starts a development
client with the mod loaded.

`tools/gen_assets.py` regenerates the textures and sounds (needs Pillow, numpy and soundfile, plus
the vanilla textures extracted from the Minecraft client jar).
