# Changelog

## v1.0.1.2 (2026-10-16)

A fix for friends who pressed Join in Melty and were left on "Waiting for the host's game to open".

- **Friends get in when e4mc can't:** the host's world used to wait for an address from e4mc's relay, and on networks that block it (or when it doesn't answer) that address never came, so friends sent by Melty waited for ever. Now, if e4mc can't be reached, or hasn't given an address 20 seconds after the world opens, the game gives Melty the host PC's own address and port: Melty brings friends in over its own connection, and friends on the same home network connect straight in. e4mc is still asked again in the background, and its address takes over once it comes.
- **No more dead addresses:** leaving your own world (back to the title screen, or into a friend's room with `/rc`) now takes its address away from Melty, so friends aren't sent to a world that has closed. Opening the world again gives Melty its new address.
- **A join that doesn't work ends in your own world:** when Melty's Join can't get you into your friend's game after three tries, you go to your own world after a few seconds (it used to stay on the error screen), and the chat says what happened and how to try again with `/rc`.

## v1.0.1.1 (2026-10-16)

Fixes for the problems players' Melty reported on 1.0.1.

- **Starting through Melty:** Melty now sets up Minecraft itself (its own Prism Launcher, Java and the player's sign-in), so the Melty download carries only the mods and the `minebloxroyale` folder (94 MB instead of 197 MB). The Prism copy the package used to carry sometimes gave up checking its Java on slow PCs, or stopped at a sign-in window, and the game closed right after starting. The first start moves the world and game settings across from the earlier install (on another drive it copies the world without its old islands).
- **Playing with friends:** e4mc's two relay libraries come with the game (`minebloxroyale/e4mc`, e4mc's own builds from natives.e4mc.link, checked by their SHA-256), so e4mc never has to download them. Where that download was blocked, the world never got an address and friends sent by Melty waited for ever ("Waiting for the host's game to open").
- **The relay unreachable:** when e4mc can't reach its relay (no internet yet), the game asks it again after 20 s, then 40, 80, 160 and every 5 minutes, and melty.log says why friends can't join yet.
- **Low disk space:** a new island is only made with 1 GB free; otherwise the next match is on the same island (the hub and chat say so). An island being made stops under 300 MB free: a next island is dropped for the one just played, and a world's first island waits for room. A full disk crashed the game mid-match.
- **Demo World:** old islands are removed in Minecraft's demo world too (an account that doesn't own Minecraft plays there), where they used to pile up until the disk was full.
- **Plain lines instead of errors:** a minimized window (wherever Minecraft logs it), e4mc's relay failing, and a file the game couldn't save (a full disk, another program holding it: the island restore list is now tried three times and swapped in whole) each write one plain line instead of an error with a stack trace.
- Roblox and Fortnite are found on the PC by the game itself (Melty no longer passes their folders, which stopped Play for players without Fortnite).

## v1.0.1 (2026-10-15)

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
- **Your team can still bring you back:** in Duos, Trios and Squads the match no longer ends the moment you (the only real player) are eliminated. While your reboot card is out and a teammate still stands, you watch your team and the match goes on; it ends when your team is out or the card runs out.
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
- **Fewer false errors in the log:** Realms and e4mc messages (as in 1.0.0.3), a minimized window, Windows performance counters being off, and Windows blocking the bundled ffmpeg (as in 1.0.0.4) each write one plain line instead of a long error.
- **Old islands off your disk:** every match is played on a new island, and the old ones used to stay in the world folder for good; they're removed when the world opens now. (Also in 1.0.0.4.)
- Test builds show their version on the hub's main board, for example "v1.0.1 - TEST 4".

## v1.0.0
The first public release.
