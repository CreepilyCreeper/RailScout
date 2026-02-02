$p = 'src/main/java/com/creepilycreeper/railscout/data/SurveySerializer.java'
$b = [System.IO.File]::ReadAllBytes($p)
if ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) {
  $nb = New-Object Byte[] ($b.Length - 3)
  [System.Array]::Copy($b, 3, $nb, 0, $nb.Length)
  [System.IO.File]::WriteAllBytes($p, $nb)
  Write-Host "Removed BOM from $p"
} else {
  Write-Host "No BOM present in $p"
}