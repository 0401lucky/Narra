param(
    [string]$SourceImage = "",
    [string]$ResRoot = "D:\code\AndroidStudioprojects\MyApplication\app\src\main\res"
)

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function New-GradientBrush {
    param(
        [int]$Width,
        [int]$Height,
        [System.Drawing.Color]$StartColor,
        [System.Drawing.Color]$EndColor
    )
    return [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Rectangle]::new(0, 0, $Width, $Height),
        $StartColor,
        $EndColor,
        45
    )
}

function Get-Alpha {
    param([System.Drawing.Color]$Color)

    $average = ($Color.R + $Color.G + $Color.B) / 3.0
    $max = [Math]::Max($Color.R, [Math]::Max($Color.G, $Color.B))
    $min = [Math]::Min($Color.R, [Math]::Min($Color.G, $Color.B))
    $saturation = $max - $min

    if ($average -ge 248 -and $saturation -le 18) {
        return 0
    }

    if ($average -ge 232 -and $saturation -le 30) {
        $fade = 1.0 - (($average - 232.0) / 16.0)
        $value = [Math]::Round(255.0 * [Math]::Max(0.0, [Math]::Min(1.0, $fade)))
        return [int]$value
    }

    return 255
}

function Save-Png {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string]$Path
    )
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Clamp-Color {
    param([double]$Value)
    return [int][Math]::Max(0, [Math]::Min(255, [Math]::Round($Value)))
}

$drawableDir = Join-Path $ResRoot "drawable"
$anydpiDir = Join-Path $ResRoot "mipmap-anydpi-v26"
$densityMap = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

Ensure-Directory $drawableDir
Ensure-Directory $anydpiDir

$sourceBitmap = [System.Drawing.Bitmap]::FromFile($SourceImage)
$preparedBitmap = [System.Drawing.Bitmap]::new($sourceBitmap.Width, $sourceBitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

$minX = $preparedBitmap.Width
$minY = $preparedBitmap.Height
$maxX = 0
$maxY = 0

for ($x = 0; $x -lt $sourceBitmap.Width; $x++) {
    for ($y = 0; $y -lt $sourceBitmap.Height; $y++) {
        $sourceColor = $sourceBitmap.GetPixel($x, $y)
        $alpha = Get-Alpha -Color $sourceColor
        $red = Clamp-Color ($sourceColor.R * 0.98 + 2)
        $green = Clamp-Color ($sourceColor.G * 1.01 + 1)
        $blue = Clamp-Color ($sourceColor.B * 0.96 + 1)
        $targetColor = [System.Drawing.Color]::FromArgb($alpha, $red, $green, $blue)
        $preparedBitmap.SetPixel($x, $y, $targetColor)

        if ($alpha -gt 10) {
            if ($x -lt $minX) { $minX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}

if ($maxX -le $minX -or $maxY -le $minY) {
    throw "No visible foreground was extracted from the source image."
}

$padding = 24
$minX = [Math]::Max(0, $minX - $padding)
$minY = [Math]::Max(0, $minY - $padding)
$maxX = [Math]::Min($preparedBitmap.Width - 1, $maxX + $padding)
$maxY = [Math]::Min($preparedBitmap.Height - 1, $maxY + $padding)

$cropWidth = $maxX - $minX + 1
$cropHeight = $maxY - $minY + 1
$cropRect = [System.Drawing.Rectangle]::new($minX, $minY, $cropWidth, $cropHeight)
$croppedBitmap = $preparedBitmap.Clone($cropRect, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

$adaptiveSize = 432
$foregroundBitmap = [System.Drawing.Bitmap]::new($adaptiveSize, $adaptiveSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$foregroundGraphics = [System.Drawing.Graphics]::FromImage($foregroundBitmap)
$foregroundGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$foregroundGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$foregroundGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$foregroundGraphics.Clear([System.Drawing.Color]::Transparent)

$targetMax = 244.0
$scale = [Math]::Min($targetMax / $croppedBitmap.Width, $targetMax / $croppedBitmap.Height)
$drawWidth = [int][Math]::Round($croppedBitmap.Width * $scale)
$drawHeight = [int][Math]::Round($croppedBitmap.Height * $scale)
$drawX = [int][Math]::Round(($adaptiveSize - $drawWidth) / 2.0)
$drawY = [int][Math]::Round(($adaptiveSize - $drawHeight) / 2.0) - 2
$foregroundGraphics.DrawImage($croppedBitmap, $drawX, $drawY, $drawWidth, $drawHeight)
$foregroundGraphics.Dispose()

$backgroundBitmap = [System.Drawing.Bitmap]::new($adaptiveSize, $adaptiveSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$backgroundGraphics = [System.Drawing.Graphics]::FromImage($backgroundBitmap)
$backgroundGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$backgroundGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$backgroundGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$backgroundGraphics.Clear([System.Drawing.Color]::FromArgb(255, 253, 253, 251))
$backgroundGraphics.Dispose()

$monochromeBitmap = [System.Drawing.Bitmap]::new($adaptiveSize, $adaptiveSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($x = 0; $x -lt $foregroundBitmap.Width; $x++) {
    for ($y = 0; $y -lt $foregroundBitmap.Height; $y++) {
        $pixel = $foregroundBitmap.GetPixel($x, $y)
        if ($pixel.A -gt 10) {
            $monochromeBitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, 28, 88, 52))
        } else {
            $monochromeBitmap.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
        }
    }
}

Save-Png -Bitmap $foregroundBitmap -Path (Join-Path $drawableDir "ic_launcher_clover_foreground.png")
Save-Png -Bitmap $backgroundBitmap -Path (Join-Path $drawableDir "ic_launcher_clover_background.png")
Save-Png -Bitmap $monochromeBitmap -Path (Join-Path $drawableDir "ic_launcher_clover_monochrome.png")

$legacyCanvas = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$legacyGraphics = [System.Drawing.Graphics]::FromImage($legacyCanvas)
$legacyGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$legacyGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$legacyGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$legacyGraphics.DrawImage($backgroundBitmap, 0, 0, 512, 512)
$legacyGraphics.DrawImage($foregroundBitmap, 0, 0, 512, 512)
$legacyGraphics.Dispose()

foreach ($entry in $densityMap.GetEnumerator()) {
    $directory = Join-Path $ResRoot $entry.Key
    Ensure-Directory $directory

    $size = [int]$entry.Value
    $target = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.DrawImage($legacyCanvas, 0, 0, $size, $size)
    $graphics.Dispose()

    $normalPath = Join-Path $directory "ic_launcher_clover.png"
    $roundPath = Join-Path $directory "ic_launcher_clover_round.png"
    Save-Png -Bitmap $target -Path $normalPath
    Save-Png -Bitmap $target -Path $roundPath
    $target.Dispose()
}

$legacyCanvas.Dispose()
$monochromeBitmap.Dispose()
$backgroundBitmap.Dispose()
$foregroundBitmap.Dispose()
$croppedBitmap.Dispose()
$preparedBitmap.Dispose()
$sourceBitmap.Dispose()

Write-Output "DONE"
