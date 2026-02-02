Get-ChildItem -Path src/client -Filter *.java -Recurse | ForEach-Object {
  $path = $_.FullName
  try {
    $b = [System.IO.File]::ReadAllBytes($path)
  } catch {
    Write-Host "READ_ERROR: $path - $($_.Exception.Message)"
    return
  }
  if ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) {
    $nb = New-Object Byte[] ($b.Length - 3)
    [System.Array]::Copy($b, 3, $nb, 0, $nb.Length)
    [System.IO.File]::WriteAllBytes($path, $nb)
    Write-Host "Removed BOM from $path"
  } else {
    Write-Host "No BOM in $path"
  }
}