$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
function Wait-WinRt($op) {
    while ($op.Status -eq [Windows.Foundation.AsyncStatus]::Started) { Start-Sleep -Milliseconds 15 }
    if ($op.Status -eq [Windows.Foundation.AsyncStatus]::Error) { throw $op.ErrorCode }
    return $op.GetResults()
}
function Encode-Text([string]$value) { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($value)) }
while ($true) {
    try {
        $manager = Wait-WinRt ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync())
        $session = $manager.GetCurrentSession()
        if ($null -eq $session) { '{"title":"","artist":"","album":"","positionMs":0,"playing":false,"artwork":""}' }
        else {
            $props = Wait-WinRt ($session.TryGetMediaPropertiesAsync())
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $playing = $playback.PlaybackStatus -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing
            $art = ''
            if ($null -ne $props.Thumbnail) {
                $stream = Wait-WinRt ($props.Thumbnail.OpenReadAsync())
                if ($stream.Size -gt 0 -and $stream.Size -le 524288) {
                    $reader = [Windows.Storage.Streams.DataReader]::new($stream.GetInputStreamAt(0))
                    $null = Wait-WinRt ($reader.LoadAsync([uint32]$stream.Size))
                    $bytes = [byte[]]::new([int]$stream.Size)
                    $reader.ReadBytes($bytes)
                    $art = [Convert]::ToBase64String($bytes)
                    $reader.Dispose(); $stream.Dispose()
                }
            }
            $positionMs = [Math]::Max(0, [long]$timeline.Position.TotalMilliseconds)
            $result = @{title=$props.Title; artist=$props.Artist; album=$props.AlbumTitle; positionMs=$positionMs; playing=$playing; artwork=$art}
            ConvertTo-Json -InputObject $result -Compress
        }
    } catch { '{"title":"","artist":"","album":"","positionMs":0,"playing":false,"artwork":""}' }
    Start-Sleep -Seconds 2
}