param(
    [string]$SourceImage = (Join-Path $PSScriptRoot "..\assets\icon.png")
)
Add-Type -AssemblyName System.Drawing

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$src = $SourceImage
$baseDir = Join-Path $repoRoot "mobile-android\app\src\main\res"

$densities = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

$img = [System.Drawing.Image]::FromFile($src)

foreach ($key in $densities.Keys) {
    $size = $densities[$key]
    $dir = Join-Path $baseDir $key
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $graph = [System.Drawing.Graphics]::FromImage($bmp)
    $graph.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graph.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graph.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graph.DrawImage($img, 0, 0, $size, $size)
    
    $targetPath = Join-Path $dir "ic_launcher.png"
    $targetRoundPath = Join-Path $dir "ic_launcher_round.png"
    $bmp.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($targetRoundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    
    $graph.Dispose()
    $bmp.Dispose()
    Write-Output "Generated $targetPath at ${size}x${size}"
}

# Also save high-res 512x512 for store / desktop icon
$desktopIcoDir = Join-Path $repoRoot "assets"
if (-not (Test-Path $desktopIcoDir)) {
    New-Item -ItemType Directory -Path $desktopIcoDir -Force | Out-Null
}
$bmp512 = New-Object System.Drawing.Bitmap 512, 512
$graph512 = [System.Drawing.Graphics]::FromImage($bmp512)
$graph512.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graph512.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$graph512.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graph512.DrawImage($img, 0, 0, 512, 512)
$bmp512.Save((Join-Path $desktopIcoDir "icon.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$graph512.Dispose()
$bmp512.Dispose()

# Also save an icon.ico for Windows desktop executable
$bmp256 = New-Object System.Drawing.Bitmap 256, 256
$graph256 = [System.Drawing.Graphics]::FromImage($bmp256)
$graph256.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graph256.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$graph256.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graph256.DrawImage($img, 0, 0, 256, 256)
$hIcon = $bmp256.GetHicon()
$ico = [System.Drawing.Icon]::FromHandle($hIcon)
$fs = New-Object System.IO.FileStream((Join-Path $desktopIcoDir "icon.ico"), [System.IO.FileMode]::Create)
$ico.Save($fs)
$fs.Close()
$graph256.Dispose()
$bmp256.Dispose()

$img.Dispose()
Write-Output "Successfully generated all launcher icons and desktop assets!"

