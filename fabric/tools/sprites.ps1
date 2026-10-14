# Generates Mineblox Royale's item sprites (our own pixel art; no game art is used).
# Guns: 32x32, muzzle to the right, built from shaded parts, then outlined. Others: 16x16.
param([string]$Out)
Add-Type -AssemblyName System.Drawing

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Shade($c, [double]$f) { [System.Drawing.Color]::FromArgb(255, [Math]::Min(255, [int]($c.R * $f)), [Math]::Min(255, [int]($c.G * $f)), [Math]::Min(255, [int]($c.B * $f))) }

# A part: x y w h colour [slant] - slant shifts each row right by 1 every 'slant' rows (for angled grips/mags).
function Draw-Parts([int]$size, $parts) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    foreach ($p in $parts) {
        $x0 = $p[0]; $y0 = $p[1]; $w = $p[2]; $h = $p[3]; $c = $p[4]; $slant = if ($p.Count -gt 5) { $p[5] } else { 0 }
        for ($y = $y0; $y -lt $y0 + $h; $y++) {
            $shift = if ($slant -ne 0) { [int][Math]::Floor(($y - $y0) / [Math]::Abs($slant)) * [Math]::Sign($slant) } else { 0 }
            for ($x = $x0; $x -lt $x0 + $w; $x++) {
                $px = $x + $shift
                if ($px -lt 0 -or $px -ge $size -or $y -lt 0 -or $y -ge $size) { continue }
                # Per-part shading: light top row, dark bottom row, mid otherwise.
                $f = if ($y -eq $y0) { 1.35 } elseif ($y -eq $y0 + $h - 1) { 0.7 } else { 1.0 }
                $bmp.SetPixel($px, $y, (Shade $c $f))
            }
        }
    }
    # Outline: transparent pixels touching a filled one become near-black.
    $copy = $bmp.Clone()
    $ol = C "#121418"
    for ($y = 0; $y -lt $size; $y++) { for ($x = 0; $x -lt $size; $x++) {
        if ($copy.GetPixel($x, $y).A -ne 0) { continue }
        $hit = $false
        foreach ($d in @(@(1,0),@(-1,0),@(0,1),@(0,-1))) {
            $nx = $x + $d[0]; $ny = $y + $d[1]
            if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt $size -and $ny -lt $size -and $copy.GetPixel($nx, $ny).A -ne 0) { $hit = $true }
        }
        if ($hit) { $bmp.SetPixel($x, $y, $ol) }
    } }
    $copy.Dispose()
    return $bmp
}

$metal = C "#8E98A4"; $dark = C "#2B2F36"; $black = C "#1D1F24"; $wood = C "#8A5A2B"; $woodD = C "#6B4220"; $glass = C "#7FD3F7"; $orange = C "#E08A2E"
$guns = @{
  assault_rifle = @(
    @(1,13,7,5,$dark), @(2,12,5,1,$dark),                     # stock
    @(7,11,15,6,(C "#5C6470")),                               # receiver
    @(9,9,8,2,$black), @(10,8,2,1,$black), @(15,8,2,1,$black), # rail + sights
    @(21,11,6,5,(C "#4A515C")),                               # handguard
    @(27,12,4,2,$metal), @(30,11,1,4,$black),                 # barrel + muzzle
    @(14,17,4,7,$black,-3),                                   # angled magazine
    @(9,17,3,6,$dark,4),                                      # grip
    @(12,17,2,2,$black)                                       # trigger
  )
  pump_shotgun = @(
    @(0,13,4,5,$wood), @(4,12,5,5,$woodD),                   # stock
    @(9,11,8,5,$dark),                                        # receiver
    @(17,11,14,2,$metal), @(30,10,1,3,$black),                # barrel + bead sight
    @(17,13,12,2,(C "#5A6068")),                              # magazine tube
    @(18,14,8,3,$wood),                                       # pump grip
    @(10,16,3,5,$woodD,4), @(13,16,2,2,$black)                # grip + trigger
  )
  smg = @(
    @(3,12,6,1,$metal), @(3,13,1,4,$metal), @(3,16,6,1,$metal), # folding wire stock
    @(9,11,13,6,(C "#3E4A57")),                               # receiver
    @(11,9,6,2,$black), @(12,8,1,1,$orange),                  # top rail + red dot
    @(22,12,4,3,$dark), @(26,12,5,3,$black),                  # barrel + suppressor
    @(15,17,3,8,$black),                                      # straight magazine
    @(10,17,3,6,$dark,4), @(13,17,2,2,$black)                 # grip + trigger
  )
  pistol = @(
    @(8,10,16,4,(C "#5B5F66")),                               # slide
    @(9,10,1,1,$black), @(11,10,1,1,$black), @(13,10,1,1,$black), # slide serrations
    @(22,9,1,1,$black), @(9,9,1,1,$black),                    # sights
    @(8,14,13,2,$dark),                                       # frame
    @(23,11,2,2,$black),                                      # muzzle
    @(9,16,5,8,$black,-5), @(14,16,3,1,$dark), @(16,16,1,3,$dark) # grip + trigger guard
  )
  sniper = @(
    @(0,14,4,5,$wood), @(4,13,6,5,$woodD),                   # stock
    @(10,12,12,5,(C "#3C4A35")),                              # body
    @(11,8,10,3,$black), @(10,9,1,1,$glass), @(21,9,1,1,$glass), @(14,11,2,1,$black), # scope
    @(22,13,9,2,$metal), @(30,12,1,4,$black),                 # barrel + brake
    @(18,11,1,1,$metal), @(18,10,2,1,$metal),                 # bolt handle
    @(12,17,3,5,$woodD,4), @(15,17,2,2,$black)                # grip + trigger
  )
}
foreach ($id in $guns.Keys) { $b = Draw-Parts 32 $guns[$id]; $b.Save("$Out\gun_$id.png", [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }

# 16x16 heals, shields and ammo.
$small = @{
  bandages = @(@(3,4,10,8,(C "#E8E1D0")), @(6,4,4,8,(C "#C9B99A")), @(3,7,10,2,(C "#C9B99A")))
  medkit = @(@(5,2,6,2,$dark), @(2,4,12,9,(C "#D9473A")), @(7,5,2,7,(C "#F5F5F5")), @(4,7,8,2,(C "#F5F5F5")))
  small_shield = @(@(7,2,2,2,(C "#DDE6F0")), @(6,4,4,2,(C "#9FD6FA")), @(5,6,6,6,(C "#5AB4F2")), @(6,12,4,1,(C "#3A8FD0")))
  shield_potion = @(@(7,1,2,2,(C "#DDE6F0")), @(6,3,4,2,(C "#9FD6FA")), @(4,5,8,8,(C "#2F6FE0")), @(5,13,6,1,(C "#1F4FB0")), @(5,7,2,3,(C "#8FC8FF")))
  chug_jug = @(@(6,1,4,2,(C "#F2F4F8")), @(4,3,8,2,(C "#6EC1FF")), @(3,5,10,9,(C "#3FA7E8")), @(12,6,3,5,(C "#F2F4F8")), @(5,7,2,4,(C "#B8E4FF")))
  slurp_juice = @(@(6,1,4,2,(C "#3A3F48")), @(4,3,8,10,(C "#7B3FD1")), @(4,5,8,2,(C "#B88AF0")), @(4,9,8,2,(C "#B88AF0")), @(5,13,6,1,(C "#4E2690")))
}
$small["mat_wood"] = @(@(2,4,12,3,(C "#B0793F")), @(2,8,12,3,(C "#9A6531")), @(2,12,12,2,(C "#B0793F")))
$small["mat_brick"] = @(@(1,3,6,3,(C "#B8483A")), @(8,3,7,3,(C "#A63E32")), @(1,7,3,3,(C "#A63E32")), @(5,7,7,3,(C "#B8483A")), @(13,7,2,3,(C "#A63E32")), @(1,11,6,3,(C "#B8483A")), @(8,11,7,3,(C "#A63E32")))
$small["mat_metal"] = @(@(2,3,12,10,(C "#9AA3AD")), @(4,5,1,1,(C "#5E6670")), @(11,5,1,1,(C "#5E6670")), @(4,10,1,1,(C "#5E6670")), @(11,10,1,1,(C "#5E6670")), @(3,7,10,1,(C "#C9D0D8")))
$ammoCol = @{ light = "#8FB5D9"; medium = "#5E8F4E"; heavy = "#4C4A8A"; shells = "#C9A23C" }
foreach ($a in $ammoCol.Keys) {
  $c = C $ammoCol[$a]; $brass = C "#D8B24A"
  if ($a -eq "shells") { $small["ammo_$a"] = @(@(3,4,4,9,(C "#C0392B")), @(3,12,4,2,$brass), @(9,3,4,9,(C "#C0392B")), @(9,11,4,2,$brass)) }
  else { $small["ammo_$a"] = @(@(2,8,12,6,$c), @(3,4,2,4,$brass), @(7,3,2,5,$brass), @(11,4,2,4,$brass), @(3,3,2,1,(C "#B87333")), @(7,2,2,1,(C "#B87333")), @(11,3,2,1,(C "#B87333"))) }
}
foreach ($id in $small.Keys) { $b = Draw-Parts 16 $small[$id]; $b.Save("$Out\$id.png", [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
"done: $($guns.Count) guns, $($small.Count) small icons"
