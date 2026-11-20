$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
$imageStreamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime]
try { Add-Type -TypeDefinition $meterSource; $hasMeter = $true } catch { $hasMeter = $false }
try { Add-Type -TypeDefinition $signalSource; $hasSignal = $true } catch { $hasSignal = $false }
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
    $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
$asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1
} | Select-Object -First 1

function Wait-WinRt($operation, [Type] $resultType) {
    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    if (-not $task.Wait(2500)) { throw 'Windows media session request timed out.' }
    return $task.Result
}

function Get-PlayingTrack($session) {
    if ($null -eq $session) { return }
    $source = $session.SourceAppUserModelId
    $appName = switch -Regex ($source) {
        '(?i)(^|[\\/])(spotify(\.exe)?|spotifyab\.spotifymusic_[^!]+(!.*)?)$' { 'Spotify'; break }
        '(?i)(^|[\\/])(applemusic(\.exe)?|appleinc\.applemusicwin_[^!]+(!.*)?)$' { 'Apple Music'; break }
        '(?i)(^|[\\/])(deezer(\.exe)?|deezer\.deezer_[^!]+(!.*)?)$' { 'Deezer'; break }
        '(?i)(^|[\\/])(tidal(\.exe)?|tidal.*_[^!]+(!.*)?)$' { 'TIDAL'; break }
        '(?i)(^|[\\/])(amazon music|amazonmusic)(\.exe)?$' { 'Amazon Music'; break }
        '(?i)(^|[\\/])(musicbee|foobar2000|aimp|itunes)(\.exe)?$' { ($Matches[2]); break }
        '(?i)^Microsoft\.ZuneMusic_[^!]+' { 'Media Player'; break }
    }
    if (-not $appName -or [string]$session.GetPlaybackInfo().PlaybackStatus -ne 'Playing') { return }
    $properties = Wait-WinRt ($session.TryGetMediaPropertiesAsync()) $propertiesType
    if ([string]$properties.PlaybackType -eq 'Video' -or [string]::IsNullOrWhiteSpace($properties.Title)) { return }
    return @{title = $properties.Title; artist = $properties.Artist; album = $properties.AlbumTitle;
        app = $appName; thumbnail = $properties.Thumbnail}
}

function Read-Cover($thumbnail) {
    if ($null -eq $thumbnail) { return }
    $stream = $null
    try {
        $source = Wait-WinRt ($thumbnail.OpenReadAsync()) $imageStreamType
        $stream = $asStream.Invoke($null, @($source))
        if ($stream.Length -le 0 -or $stream.Length -gt 1048576) { return }
        $bytes = New-Object byte[] ([int]$stream.Length)
        $offset = 0
        while ($offset -lt $bytes.Length) {
            $count = $stream.Read($bytes, $offset, $bytes.Length - $offset)
            if ($count -le 0) { return }
            $offset += $count
        }
        return [Convert]::ToBase64String($bytes)
    } catch {
        # A streaming app may withdraw a thumbnail when the track changes.
    } finally {
        if ($stream) { $stream.Dispose() }
    }
}

$manager = Wait-WinRt ($managerType::RequestAsync()) $managerType
$parent = [System.Diagnostics.Process]::GetProcessById([int]$env:MARCE_MEDIA_PARENT)
$clock = [System.Diagnostics.Stopwatch]::StartNew()
$nextTrack = 0L
$nextMeter = 0L
$currentKey = ''
$currentApp = $null
while (!$parent.HasExited) {
    $now = $clock.ElapsedMilliseconds
    if ($now -ge $nextTrack) {
        $nextTrack = $now + 750
        $track = $null
        try {
            $track = Get-PlayingTrack ($manager.GetCurrentSession())
            if (!$track) {
                foreach ($session in $manager.GetSessions()) {
                    $track = Get-PlayingTrack $session
                    if ($track) { break }
                }
            }
        } catch {
            # A session can vanish while Windows is resolving its metadata.
        }
        $key = if ($track) { "$($track.app)`n$($track.title)`n$($track.artist)`n$($track.album)" } else { '' }
        if ($key -cne $currentKey) {
            $currentKey = $key
            if ($track.app -ne $currentApp) {
                $nextMeter = 0L
                if ($hasSignal) { [MarceAudioSignal]::SetTarget(0) }
            }
            $currentApp = $track.app
            $metadata = if ($track) {
                @{title = $track.title; artist = $track.artist; cover = (Read-Cover $track.thumbnail)}
            }
            [Console]::WriteLine('T' + (ConvertTo-Json -InputObject $metadata -Compress))
        }
    }
    if ($hasMeter -and $now -ge $nextMeter) {
        $nextMeter = $now + 1800
        if ($currentApp) {
            $names = switch ($currentApp) {
                'Apple Music' { 'AppleMusic' }
                'Amazon Music' { 'Amazon Music'; 'AmazonMusic' }
                'Media Player' { 'Microsoft.Media.Player'; 'Music.UI' }
                default { $currentApp }
            }
            [void][MarceAudioMeter]::Refresh([string[]]@($names))
            if ($hasSignal) {
                $audioPid = [MarceAudioMeter]::ProcessId
                if ($audioPid -eq 0) {
                    $player = Get-Process -Name $names -ErrorAction SilentlyContinue |
                        Sort-Object StartTime | Select-Object -First 1
                    if ($player) { $audioPid = $player.Id }
                }
                [MarceAudioSignal]::SetTarget($audioPid)
            }
        } else {
            [MarceAudioMeter]::Clear()
        }
    }
    if ($hasMeter -and $currentApp -and (!$hasSignal -or ![MarceAudioSignal]::Available)) {
        [Console]::WriteLine('P' + [MarceAudioMeter]::Peak().ToString('0.0000', [Globalization.CultureInfo]::InvariantCulture))
    }
    Start-Sleep -Milliseconds 16
}
