# Changelog

## v1.0.1 (in testing: 1.0.1-test5, not released yet)

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
