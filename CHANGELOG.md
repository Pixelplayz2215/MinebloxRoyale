# Changelog

## v2.0.0 (in testing: 2.0.0-test2, not released yet)

A big update on top of v1.0.1: new modes, new weapons, cars, bosses, a shop, and a new look and sound.

### Modes and the hub
- **No Build:** Solo, Duos, Trios and Squads without building. There are four new portals for them in their own zone of a bigger, calmer hub. No materials are gathered, and bots don't build either.
- **The parked Battle Bus in the hub is solid:** you can't walk through it any more.

### Gold and the Locker shop
- **Gold:** earned in matches for eliminations, a top 10 or top 5 finish, playing a match, a Victory Royale and eliminating a boss. Gold follows you into every world.
- **Shop tab in the Locker:** buy emotes, cosmetics, pickaxes and contrails with gold. What you buy is yours for good.
- **The Shop says what everything is:** its items sit under their category's title (Pickaxes, Contrails, Emotes...), and hovering one shows it on you: a pickaxe in your hand, a contrail trailing as you skydive, an emote danced. Something you've bought leaves the Shop and sits in its own tab, and buying it tells you which.
- **The Pickaxes tab shows your pickaxe** in your hand on the preview.
- **Pickaxe skins:** golden, candy, diamond, ice, lava, lollipop and neon, made in Blender.
- **New emotes:** the Griddy, Default Dance, Orange Justice, Boneless, Take the L, Electro Shuffle, Fresh and Hype (400 to 800 gold). All are animated in Blender with nothing clipping.

### Gliders and contrails
- **Gliders now animate:** they pop open out of your back, sway as they ride the air, bank into turns, and fold away when you land. The animation is keyframed in Blender.
- **Contrails:** a new Locker tab with 14 of them. Light trails stream from your hands while skydiving and from your glider's wingtips while gliding: streak, rainbow, candy stripes, neon, sparkle, flame, ghost wave and bubbles. Some are free, some are unlocked, and some cost gold.
- **Smooth contrails:** every trail follows a smooth curve and starts right at your hand, and striped ones (Candy Stripe) have sharp stripes that slide along instead of jumping.

### Shooting
- **Hip and aim:** a gun is carried at the hip and raised into the aim when you aim down the sights.
- **Recoil on every gun:** the arms and body kick on each shot, and so does your view, each gun by its own amount.
- **Visible bullets:** tracers fly from the muzzle to where each shot lands, with a muzzle flash, sparks and dust on impact, and a red puff on a hit. The sniper leaves a smoke trail.
- **Rocket launcher:** a slow rocket you can see, which explodes where it hits. It hurts players and bots, breaks builds and buildings, and does 300 damage to cars. Rockets have their own ammo slot, never a hotbar slot.
- **Grenades:** left click throws one in an arc. It bounces and explodes after 2.5 seconds. Up to 6 fit in one slot.
- Bots use both: grenades against players behind cover, rockets against players among builds or in groups.
- Rocket launchers, rockets and grenades turn up about twice as often as in the first test.

### Cars
- **Drivable cars:** F gets in, W A S D drive, Space boosts and Shift gets out. Up to 4 fit in a car: the first one in drives, and only their teammates can join.
- **Fuel:** about 5 minutes per car, with no refuelling. Each car has 4 boosts, and each boost costs 30 seconds of fuel.
- **No shooting from a car.** Boosting smashes through buildings, but hills and water stop you.
- **Car HUD:** the fuel, boosts and the car's durability (out of 600) show where the ammo usually is.
- **Bots drive too,** heading for the safe zone.

### Bosses
- **Four bosses per match, out of six:** The Blockbuster (pump shotgun), Captain Bloxbeard (sniper), Agent Noob (assault rifle), Sergeant Bacon (SMG), Doctor Kaboom (rocket launcher) and Sheriff Noobly (pistol), each with a mythic gun. They guard lairs you can see from a distance, and their icons show on the minimap and the map.
- **Boss loot:** a boss drops its own named mythic gun and loot, and gives 50 gold to whoever eliminates it.

### The island
- **Bigger buildings:** warehouses, two-storey houses and forts, with more chests. There are now 26 buildings per island.
- **Reboot vans on every island, in every mode:** 8 of them, half near the middle and half out towards the edge, so some are inside the circle and some outside. They're all on the map and the minimap from the start. In Solo they're just there, with no one to bring back.

### Look and sound
- **A cleaner, smoother HUD in Fortnite's style:** rounded panels, icons made in Blender, smooth lettering, and a Fortnite-style hotbar in matches. Fortnite's own HUD is locked in its encrypted files, so all of this is ours.
- **A bigger HUD:** every panel is about 15% bigger: health and shield, the hotbar, ammo and materials, the minimap, the compass, the storm banner, the teammate list and the "Hold F" prompts. On a screen narrower than 1920x1080 it shrinks just enough that nothing overlaps.
- **3D items in the hotbar and inventory:** every gun, heal, shield, grenade and pickaxe shows as its own 3D model, guns in their rarity colour, instead of a flat pixel picture. The hub's Gun Rarity and Heals & Shields boards show the 3D models too.
- **Our own sounds:** a shot sound for each gun type (rarities sound the same), a new storm warning, rocket and grenade blasts, and two battle music tracks in matches. The harder track plays in the last circles.

### Fixes
- Opening the creative inventory crashed the game (2.0.0-test1).
- The log notes for Minecraft's sign-in, Realms and e4mc from Melty's 1.0.0.3, so Melty doesn't mistake them for errors loading the mod.

## v1.0.1 (in testing: 1.0.1-test6, not released yet)

A graphics update: almost everything you see in a match was remade in Blender.

### New models
- **Guns:** all 5 remade. The whole body of a gun is now in its rarity colour, not a thin stripe: Common grey, Uncommon green, Rare blue, Epic purple, Legendary gold. This shows in your hand, on the ground and in the hotbar.
- **Heals and shields:** all 6 remade.
- **Pickaxe:** a Fortnite-style pickaxe replaces the iron pickaxe look.
- **Cars:** all 7 kinds remade, each with 4 damage stages: dents, scratches, cracked and then smashed glass, a hanging bumper, a lost hubcap and an open door.
- **Battle Bus:** more detail, including the grille, framed windows, door, ladder, roof rack and a striped balloon.
- **Reboot van:** new model. Its screens and roof light glow green when it's ready and red while it recharges.
- **Gliders:** all 46 remade, and 1.45x bigger.
- **Hair:** every style sculpted from real locks of hair: fringes, ponytails, pigtails, buns, ringlets, a soft afro. Each one fits cleanly under every hat.
- **Hats:** smoother, with the details real ones have: cap seams and stitching, knitted beanies with ribbed cuffs and pompoms, buckles, bands and brim binding, crown cushions and pearls, rivets and straps on helmets.
- **Shoes:** proper soles with tread, toe bumpers, laces and tongues on sneakers; lug soles, heels and hooks on boots; straps and tassels on loafers.
- **Back bling:** all 37 remade smoother.
- **Fixes found checking every new picture:** the army helmet's camo is painted flat on the helmet; the mohawk's crest is a thin fin and its crown has no bare disc; cap stitching sits on the brim; the halo has no stray sparkles; bunny and cat ears have one headband, and the bunny's pink insides no longer flicker; boot and loafer heels sit under the sole; pigtails no longer have strands like eyebrows over the temples; the Battle Bus door no longer covers the front wheel; the bandages' loose end is a flat strip; wrecked car bonnets buckle up into a peak.
- **Shirts and pants:** 3D collars, cuffs, belts and buckles, pockets, overall straps, suit lapels and ties, badges, sashes with medals, knee pads and side piping, on top of their printed designs.
- **Loot chests:** both chests remade in Blender: the wooden one with gold bands, corner brackets and a lock, and the rare one in deep blue with silver trim. Real planks with grooves between them, a rounded lid, and a glowing floor inside. When you search a chest, its lid swings open with a little bounce. A chest that's already open when you arrive is simply open.
- Cars, gliders and cosmetics use lighter models far away, so lots of bots on screen stay smooth.

### Animations
- **Knees and elbows:** every avatar's arms and legs now bend in the middle. The bend is smooth on the outside and keeps the limb the same length, so feet stay on the ground. Shirts, pants, shoes and their stripes, cuffs and knee pads bend with them. A held gun or pickaxe follows the forearm. The deeper a joint bends, the longer the stretch it curves over, so the inside of the knee or elbow is squeezed but never folds over itself (that fold made the pants and sleeves flicker behind the knees and inside the elbows).
- **Arms that twist, bodies that flip:** an arm can now turn along its length, so an elbow can fold any way (across the face for the Dab), and the whole body can turn over (a headspin).
- **New moves, all animated in Blender:**
  - Walking heel to toe with soft knees, arms swinging.
  - Running: leaning in, knees up, heels kicking back, arms pumping with bent elbows.
  - Sneaking: low on bent knees, leaning in.
  - Jumping and falling: knees tuck on the way up, legs reach for the ground coming down, arms and legs spread when falling fast.
  - Aiming: the forearm holding the gun follows exactly where you look, whether you look up or down, while standing, walking or sprinting.
  - Pickaxe: a big over-the-shoulder chop on every hit. The body turns and leans into it when you stand still; on the move only the arm chops, so the hips don't sink into the legs. Holding the button down to mine flows from one chop into the next.
  - Healing: bandages and med kits are wrapped in front of you; shield potions, Slurp Juice and the Chug Jug are drunk at the mouth.
  - Reviving: down on one knee next to your teammate, both hands working.
  - Rebooting: typing on the Reboot Van's screen.
  - Searching a chest: bent over it, rummaging.
  - Swimming front crawl, treading water, climbing ladders hand over hand, gliding with the legs dangling.
  - Changing from one move to another blends smoothly, including going straight from healing to drinking.
  - The moves mixed together in the game (aiming, chopping, healing or drinking while walking, sprinting or sneaking) are checked on the real body too: nothing goes through anything.
- **Everyone sees it:** you can see other players and bots reviving and rebooting, and other players searching chests.
- **Emotes:** all 40 animated again in Blender, now with elbows and knees: waving from the elbow, robot arms at 90 degrees, a boxing guard, double biceps, hands on hips, knees tucked in jumps. They're smoother, with the head nodding, turning and tilting and the body leaning, twisting and bouncing. Nothing goes through anything: every frame is checked on the real Roblox body, so arms and legs never go into the body, the head or each other, and dips bend the knees instead of sinking the feet into the floor. Emotes that crossed the arms over the body (like Arm Swing) now swing them to the middle, in front or behind, because a Roblox R6 arm can't cross in front without going into the chest.
  - **Floss:** slower, and the arms swing right across the body, in front and then behind, with the hips swaying the other way. Like Roblox's own R6 Floss, the arms pass a little into the edge of the body as they cross (the one emote allowed to).
  - **Dab:** a real dab: one arm straight out and up, the other folded across the face with the head bowed into the elbow.
  - **Raise the Roof:** palms up by the shoulders with the elbows forward, pushing up into a V overhead, twice a beat (it looked like flexing before).
  - **Breakdance Spin:** a headspin: upside down on the head, legs up in a V, spinning.
- **Knocked down:** a new crawl along the ground: one arm reaches ahead while the other pulls back, the legs push, the head looks up.

### Gameplay
- **Cars:** hitting a parked car with the pickaxe works again (it wouldn't start breaking).
- **Reboot vans:** always placed out in the open, never inside or under a tree.
- **Fairer bots at the start:**
  - A bot without a gun loots instead of chasing players.
  - No bot comes after you while you have no gun.
  - At most 2 bots chase you at once.
  - A bot you shoot will still fight back.

### Other
- **Shirts and pants no longer flicker:** their printed designs were drawn twice in the same place, once facing each way, and Minecraft 26 shades a face seen from behind darker, so the two fought (white and grey speckles on the shirt). Now each is drawn once.
- **No dark patches on hats:** a few rims and brims (the tricorne's, the pith helmet's, the buzz cut's edge, some gliders) had triangles facing the wrong way, which Minecraft 26 draws dark. They're turned the right way when the game loads them.
- **Starting the game:** the Prism Launcher package now carries Minecraft's and Fabric's version details and Java 25, so Prism doesn't have to download them before the game starts. On some PCs that download never got through and the game closed right after starting. The same fix went to Melty as 1.0.0.1.
- **Playing with friends:** the host's game writes its join address the moment the relay gives it out, without waiting for the chat message, and starts a fresh note every launch, so friends are never sent an old address. (Also in 1.0.0.2.)
- **Sign-in errors:** when Minecraft can't check the Microsoft sign-in (an offline account, or one whose sign-in has run out), each check writes one plain line in the log instead of a long error. Playing works as normal; joining friends needs a signed-in account. (Also in 1.0.0.2.)
- Test builds show their version on the hub's main board, for example "v1.0.1 - TEST 4".

## v1.0.0
The first public release.
