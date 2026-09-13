# ScooterMC — research notes

Everything below was verified against primary sources (the 1.21.11 client jar / mappings, the
Paper 1.21.11 API jar, or manufacturer spec sheets) rather than assumed. Where a claim came from
bytecode, the class and method are named so it can be re-checked.

---

## 1. The reference vehicle — KuKirin G2 Pro

| Property | Value |
| --- | --- |
| Unfolded size (L×W×H) | 1187 × 620 × 1315 mm |
| Folded height | 560 mm |
| Weight | 25 ± 0.5 kg |
| Max load | 120 kg |
| Motor | 600 W brushless rear hub, 48 V, 23 N·m |
| Battery | 48 V 15.6 Ah 18650 Li-ion (≈ 749 Wh) |
| Top speed | 45 km/h |
| Speed modes | 20 / 40 / 45 km/h (three) |
| Range | up to 58 km |
| Climb | up to 19° |
| Brakes | front + rear 120 mm disc, 5–12 m stopping distance |
| Tyres | 9 × 3 in tubeless off-road, 340 kPa |
| Suspension | four-arm, front + rear dual coil spring |
| Display | 118 × 73 mm LED dash |
| Ingress rating | IP54 |
| Frame | aluminium alloy, matte black with orange accents |
| Extras | one-step fold with hook, telescopic 3-position stem, 6-light system, detachable saddle |

Sources: [kukirin-scooter.com G2 Pro](https://www.kukirin-scooter.com/products/kukirin-g2-pro-electric-scooter),
[maxblinker](https://www.maxblinker.com/en/electric-scooters/kukirin-g2-pro),
[kukirin-europe](https://kukirin-europe.com/products/kukirin-g2-pro-electric-scooter).

These numbers drive the plugin defaults directly: `config.yml` ships 20/40/45 km/h drive modes, a
749 Wh battery, 120 kg load limit, a 19° climb limit and a 5–12 m braking envelope.

### How the thing is actually ridden

* The rider **stands** with a staggered, skateboard-like stance, knees unlocked, both hands on the
  bars.
* Almost every e-scooter controller has a **zero-start lock**: the throttle stays dead until the
  scooter is already rolling at roughly 3–5 km/h, so the rider must *kick off* first. This exists so
  a bumped throttle cannot launch the scooter while it is parked.
* Throttle is a right-hand thumb/trigger lever; brakes are hand levers, usually left = rear.
* Steering at speed is mostly **leaning**, with very little bar input — real scooters understeer
  noticeably as speed rises.

Sources: [naveetech beginner guide](https://naveetech.us/blogs/news/how-to-ride-an-electric-scooter-for-beginners),
[VORO MOTORS](https://www.voromotors.com/blogs/news/how-to-ride-an-electric-scooter),
[RiderGuide](https://riderguide.com/safety/how-to-ride-an-electric-scooter/),
[Wikipedia — motorized scooter](https://en.wikipedia.org/wiki/Motorized_scooter).

All four behaviours are modelled: standing pose, kick-to-start, thumb-throttle ramp, and
speed-dependent steering authority.

---

## 2. Why the rider is *not* a passenger

The obvious implementation — mount the player on an invisible vehicle — cannot work here, because
the client renders a passenger in a **sitting** pose whenever
`vehicle.shouldRiderSit()` is true, which it is for every entity a plugin can spawn and control.
A seated player on a kick scooter looks wrong, and the brief explicitly asks for the scooter to be
visible *under the rider's feet*.

So ScooterMC never mounts the player. Instead it:

1. sets the rider's **walk speed** to zero, so WASD no longer moves them client-side;
2. reads the real key state from `Player#getCurrentInput()`;
3. drives the player with one `ClientboundSetEntityMotionPacket` per tick (`Player#setVelocity`).

Movement therefore stays **client-authoritative**: the client keeps doing its own collision,
step-up, slope handling and gravity, which is what makes the ride feel smooth instead of rubber-band
teleporty. The player is a normal standing entity the whole time.

### Why walk speed and not the movement-speed attribute

The obvious way to stop WASD is an attribute modifier zeroing `minecraft:generic.movement_speed`.
That would also **halve the rider's field of view**. From `AbstractClientPlayer.getFieldOfViewModifier`
(obf `hne.a`), decompiled from the 1.21.11 client:

```java
float g = 1.0F;
if (abilities.flying) g *= 1.1F;
float walking = abilities.getWalkingSpeed();
if (walking != 0.0F) {                                     // <- the guard that matters
    g *= (getAttributeValue(MOVEMENT_SPEED) / walking + 1.0F) / 2.0F;
}
```

With the attribute at 0 and `walkingSpeed` at its usual 0.1, that factor is `(0 + 1) / 2 = 0.5`, and
the rider's view zooms hard. Zeroing the **ability** instead makes `walking == 0.0F`, the whole term
is skipped, and the view is untouched — while still stopping the player from walking. The saved
value is restored on dismount, disconnect, death, world change, plugin disable, and defensively on
join.

Vertical motion is integrated by the plugin, because a velocity packet overwrites all three axes;
`Physics.gravityStep` reproduces vanilla's `(v - 0.08) * 0.98` exactly so falls behave normally.

### The part that had to be proven: is player input sent outside a vehicle?

Before 1.21.2 the serverbound input packet was a *steer vehicle* packet and only arrived while
riding. In 1.21.2 it became a general `player_input` packet, but the API javadoc does not say
whether the client still gates it on having a vehicle — and the entire control scheme depends on the
answer.

Verified directly from the 1.21.11 client. In `net.minecraft.client.player.LocalPlayer` (obf `hnh`),
the per-tick send method contains:

```
 0: connection.isAcceptingMessages() ? continue : return
22: if (!this.lastSentInput.equals(this.input.keyPresses)) {
43:     connection.send(new ServerboundPlayerInputPacket(this.input.keyPresses));   // obf ajk
60:     this.lastSentInput = this.input.keyPresses;
    }
71: if (this.isControlledCamera()) { ...send movement packets... } else { ... }
```

The input send at offsets 22–68 sits **before** any camera/vehicle branch and its only guard is
`isAcceptingMessages()`. `player_input` is therefore sent unconditionally whenever the key-press set
changes, and `Player#getCurrentInput()` is reliable for a player on foot.

Because it is only sent *on change*, the value is a latched key state — exactly right for polling
held keys once per tick.

One consequence handled in code: sprint-jumping still applies vanilla's fixed `0.2` forward impulse
whatever the speed setting. It does not matter, because the plugin rewrites horizontal velocity every
tick, so any client-side impulse is corrected within one tick.

---

## 3. Paper 1.21.11 API surface actually used

Verified present by `javap` against `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`:

| Need | API |
| --- | --- |
| key state | `org.bukkit.Input` (forward/backward/left/right/jump/sneak/sprint), `Player#getCurrentInput()`, `PlayerInputEvent` |
| geometry | `ItemDisplay`, `Display#setTransformationMatrix(org.joml.Matrix4f)`, `setInterpolationDuration/Delay`, `setTeleportDuration`, `Display.Brightness` |
| click target | `Interaction` with `setInteractionWidth/Height`, `setResponsive` |
| custom item look | `ItemMeta#setItemModel(NamespacedKey)` (1.21.4+ `minecraft:item_model` component) |
| movement lock | `Player#setWalkSpeed(float)` (see section 2 for why, not the speed attribute) |
| fall handling | `Attribute.SAFE_FALL_DISTANCE`, `Attribute.FALL_DAMAGE_MULTIPLIER` + `AttributeModifier(NamespacedKey, double, Operation, EquipmentSlotGroup)` |
| commands | `io.papermc.paper.command.brigadier.Commands` via `LifecycleEvents.COMMANDS` |
| headlight | `Player#sendBlockChange(Location, BlockData)` (client-only fake light, never touches the world) |
| custom audio | `Player#playSound(Location, String, SoundCategory, float, float, long)` — the `String` overload is what allows resource-pack sound keys |

`org.joml:joml:1.10.8` and `com.mojang:brigadier:1.3.10` are both listed in the Paper server jar's
`META-INF/libraries.list`, so they are on the runtime classpath and need no shading.

---

## 4. Resource pack facts

* **`pack_format` = 75.** Read from `version.json` inside the 1.21.11 server jar:
  `"pack_version": {"resource_major": 75, "resource_minor": 0, ...}`. Protocol 774, Java 21.
* **Item model definitions** live at `assets/<ns>/items/<id>.json` and are pointed at by the
  `minecraft:item_model` component. Confirmed against vanilla `assets/minecraft/items/diamond_sword.json`:

  ```json
  { "model": { "type": "minecraft:model", "model": "minecraft:item/diamond_sword" } }
  ```

* **`texture_size` is a Blockbench hint only.** In-game, face `uv` is always in a 0–16 space that
  maps to the whole sprite whatever its pixel size, so the generator packs every face into one
  16×16 UV square and is free to pick any power-of-two PNG size. `texture_size` is still emitted so
  the models open correctly in Blockbench.
* **Element rotation** is limited to a single axis per element at one of `-45, -22.5, 0, 22.5, 45`
  degrees. Circles are therefore built from four overlapping full-diameter bars at
  `0°, 22.5°, 45°, -22.5°`; because a bar covers both `φ` and `φ+90°`, that yields 8 evenly spaced
  edge directions — a 16-gon whose radius varies by under 2 %.

### The UV axis convention (needed to paint textures procedurally)

Taken from `FaceBakery.defaultFaceUV(from, to, direction)` (obf `hqi.a`), which is the code vanilla
uses when `uv` is omitted and therefore the ground truth for which geometry axis becomes U and which
becomes V:

| face | `uv` = |
| --- | --- |
| down  | `from.x, 16−to.z, to.x, 16−from.z` |
| up    | `from.x, from.z, to.x, to.z` |
| north | `16−to.x, 16−to.y, 16−from.x, 16−from.y` |
| south | `from.x, 16−to.y, to.x, 16−from.y` |
| west  | `from.z, 16−to.y, to.z, 16−from.y` |
| east  | `16−to.z, 16−to.y, 16−from.z, 16−from.y` |

Inverting it gives the texel → geometry map the texture painter uses, with
`s = (u−minU)/(maxU−minU)` and `t = (v−minV)/(maxV−minV)`:

```
down :  x = from.x + s·Δx     z = to.z   − t·Δz
up   :  x = from.x + s·Δx     z = from.z + t·Δz
north:  x = to.x   − s·Δx     y = to.y   − t·Δy
south:  x = from.x + s·Δx     y = to.y   − t·Δy
west :  z = from.z + s·Δz     y = to.y   − t·Δy
east :  z = to.z   − s·Δz     y = to.y   − t·Δy
```

Every texel is therefore painted from its true position on the scooter, which is what lets tyre
tread follow the wheel's circumference and lets the wheel sidewall be painted as concentric rings
that stay correct no matter which of the four rotated bars the texel belongs to.

---

## 5. Display-entity placement maths

An `ItemDisplay` renders its model centred on the entity: model point `(8, 8, 8)` sits at the entity
position, 1 model unit = 1/16 block. The transformation is applied as `T · LR · S · RR` about that
centre, and `T` is in **unrotated world axes**.

So for a part whose pivot is at design-space offset `p` (mm) from the scooter origin, on a scooter
with Minecraft yaw `θ`:

```
T  = R_y(−θ) · (p / 1000)          # mm → blocks; −θ because MC yaw 0 = +Z and increases toward −X
LR = R_y(−θ) ∘ (part articulation)
S  = 16 · mm_per_unit / 1000       # uniform, chosen per part by the generator
```

Articulation composes on the right, i.e. innermost first:

* wheel — `R_y(−θ) ∘ R_x(rollAngle)`, pivot = axle centre, `rollAngle = distance / radius`
* the whole steering column — stem, bars, dash, headlight, fork and front wheel — rotates about the
  **raked** steering axis, not a vertical one. The stem leans back 10°, so turning about a vertical
  axis would swing the handlebars sideways through an arc instead of turning them in place.
* pivots are rotated about that same axis before the yaw is applied

The generator emits `assembly.json` (part scale + pivot + geometry constants) into the plugin jar so
the model and the Java rig can never drift out of sync.

---

## 6. Sound design

Minecraft cannot stream a looping sound with continuously varying pitch, so a motor is built by
re-triggering a short seamless loop every N ticks with the pitch and volume computed from current
wheel speed and throttle load — the standard approach, and it cross-fades cleanly because the loop
is generated to be phase-continuous at its own boundary.

All samples are synthesised (`tools/gen_sounds.py`): a BLDC motor is modelled as a fundamental at
`poles · rpm` with odd harmonics, a PWM switching whine an octave and a half up, bearing noise, and
a tyre-roll noise bed; the disc brake is band-limited noise plus a squeal formant that rises as the
rotor slows.

---

## 7. What live testing changed

Everything above was established before the plugin ran. These came out of actually driving it on a
Paper 1.21.11 server with a scripted protocol client (`tools/ridetest/ridetest.js`), and each one
changed the implementation.

### Bukkit's `addPassenger` forces past the one-passenger rule

`Entity.canAddPassenger` returns `passengers.isEmpty()` and `Player` does not override it, so the
rig was originally chained (player → body → fork → …). `/scooter selftest` then showed a single
carrier happily accepting all eight displays, because `CraftEntity.addPassenger` calls
`startRiding(vehicle, **true**)` and the force flag skips `canAddPassenger` entirely.

Direct mounting is now the primary path — simpler, fewer packets, and one part failing to attach
cannot detach every part below it. The chain survives as a fallback, and teleporting behind that.

### The drop key raises no event with an empty hand

The headlight was bound to Q via `PlayerDropItemEvent`. In game it did nothing: with nothing in
hand there is no item to drop, so the server never fires the event, and riders very often ride
empty-handed.

Rebound to inputs that always produce an event:

| control | event | fires empty-handed |
| --- | --- | --- |
| scroll wheel — drive mode | `PlayerItemHeldEvent` | yes |
| F — headlight | `PlayerSwapHandItemsEvent` | yes |
| right-click — horn | `PlayerInteractEvent` | yes |

Because the held-slot event is cancelled, `getPreviousSlot()` always reports the real slot, so the
wheel delta is measured against a fixed origin. It is wrapped through the 9-slot hotbar, which
makes scrolling up select a faster mode and down a slower one, with no wrap-around between SPORT
and ECO.

Interaction entities also turned out not to deliver a damage event reliably, so folding a scooter
back up is primarily **sneak + right-click**; the left-click path is kept as a convenience.

### One stalled tick is not a collision

Crash detection compared each tick's actual movement against what was asked for. The test client
tripped it constantly on open ground, because movement packets arrive on the *client's* schedule:
under any jitter one server tick sees two position updates and the next sees none, so a perfectly
clear tick can look like a wall.

It now takes three consecutive short ticks to count as an obstruction, with a cooldown so scraping
along a wall does not produce a stream of crashes. A real wall still registers within 0.15 s.

### Braking did not meet the spec it was written against

The unit test comparing stopping distance to KuKirin's quoted 5–12 m failed at the original
5.0 m/s²: from 45 km/h that is `12.5² / (2 × 5) = 15.6 m`. The default is now **6.6 m/s²** (0.67 g),
giving 11.8 m — inside the spec, and a believable figure for twin 120 mm discs.

### Test evidence

A full scripted run mounts, kick-starts, reaches 31 km/h, selects all three drive modes, toggles
the headlight, sounds the horn, steers, brakes into walk-assist reverse, dismounts, folds the
scooter into an item and puts it back down — with zero spurious crashes, zero exceptions in the
server log, and the scooter still present after a restart.

---

## 8. Rebuilding the model against a photograph

The first model was built from the spec sheet alone. A spec sheet gives overall size, wheel size
and weight; it does not give the things that decide whether a shape reads as *this* scooter. The
result looked generic, and wrong in one specific, very visible way.

`tools/measure_reference.py` extracts the missing proportions from a studio side-on photo by
thresholding it and reading pixel positions, scaled from one known dimension:

```
scale        1.32 mm/px
stem rake    10.3 deg back from vertical
deck top     ~200 mm above the ground
accent bands 975..1008 mm (folding collar), 96..234 mm (suspension links)
```

What that changed:

| | before | after |
| --- | --- | --- |
| stem | vertical | raked back **10°** |
| what steers | the fork only | the **whole column** — stem, bars, dash, headlight, fork, front wheel |
| headlight | 925 mm, high on the stem | **430 mm**, low, just above the wheel |
| front axle | 192 mm ahead of the stem | **70 mm** ahead, wheel tucked under the column |
| suspension | visible coil springs | the **orange swing links** that dominate the real machine |
| fork crown | a 152 × 106 × 135 mm slab that swallowed the front wheel | a compact plate |

The rake is the big one: it is the first thing the eye reads and no amount of detail compensates
for getting it wrong.

Two techniques were needed to build it:

* **Sub-degree lean.** Element rotation only offers ±22.5° and ±45°, so the 10° stem is stacked
  from forty short boxes each stepped back along Z. The step is about 4 mm — under a pixel on
  screen — while the silhouette comes out at exactly the right angle. Interior caps are skipped
  so the extra segments cost geometry, not texture space.
* **A raked steering axis.** Because the whole column turns, the axis it turns about is tilted
  too. `ScooterRig` rotates about that arbitrary axis rather than about Y; turning about a
  vertical axis instead would swing the handlebars sideways through an arc rather than rotating
  them in place.

Building the fork also exposed a bug in the generator's auto-scaler: it sized the model from
*post*-rotation corners, but model files store an element's corners **before** its rotation. A
45° fork leg sits much further from the pivot un-rotated than it ever does in place, and the raw
coordinates overflowed Minecraft's `[-16, 32]` element range. The scaler now satisfies whichever
of the two is larger.

---

## 9. The rider stands, and what that costs

The brief asks for the rider to stand, and they do — they are never a passenger, so they are never
put in the sitting pose. There is a consequence worth stating plainly, because it is a Minecraft
limitation and not something the plugin can configure away.

From `LivingEntity.calculateEntityAnimation` in the 1.21.11 client:

```java
if (this.isPassenger() || !this.isAlive()) {
    this.walkAnimation.stop();      // passengers' limbs are frozen
} else {
    this.updateWalkAnimation(distanceMovedThisTick);
}
```

Limb swing is frozen **only for passengers**. A standing player who is moving animates their legs,
and `walkAnimation` saturates at any speed above about 0.25 blocks per tick — so at scooter speeds
the rider's legs swing at full sprint rate.

The obvious escape would be a vehicle whose rider does not sit. That hook is gone: pre-1.21
versions had `Entity.shouldRiderSit()`, but in 1.21.11 the humanoid render state carries a plain
`boolean isPassenger` with nothing to override. **Every** passenger sits.

So the two options are exclusive:

| | pose | legs |
| --- | --- | --- |
| not a passenger (**what ScooterMC does**) | stands upright | animate as if running |
| a passenger | sits down | frozen |

Standing was the requirement, so standing is what ships.
