# Recopie sur ce poste Windows les sauvegardes faites sur le serveur par sauvegarder.sh.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File deploy\copier-sauvegardes.ps1
#
# Une sauvegarde qui ne quitte pas le serveur ne protege que de l'erreur logicielle : une panne du
# disque ou le vol de la machine emporterait la base et ses copies ensemble. C'est le poste qui
# va chercher, par SSH, et non le serveur qui depose : le serveur n'a ainsi aucun acces a ce poste.
#
# Lance chaque jour par la tache planifiee « Sauvegardes GestionStock », et a chaque ouverture de
# session : le poste n'est pas toujours allume a l'heure dite, et une copie manquee se rattrape
# au prochain demarrage.
#
# Chaque fichier est verifie par son empreinte SHA-256, calculee des deux cotes : une copie
# tronquee par une coupure reseau serait sinon gardee comme bonne.
#
# Code de sortie, lisible dans le Planificateur de taches (« Resultat de la derniere execution ») :
#   0  tout va bien
#   1  le serveur est injoignable, ou une copie a echoue
#   2  les copies sont a jour, mais la derniere sauvegarde du serveur a plus de deux jours :
#      c'est sauvegarder.sh qui ne tourne plus, et il faut aller voir son journal.
#   3  les copies locales sont faites, mais l'envoi vers Google Drive a echoue.
#
# Puis tout est renvoye sur Google Drive, chiffre par rclone avant de partir (remote « crypt ») :
# ce poste et le serveur sont dans les memes murs, et un incendie ou un cambriolage emporterait
# les deux. Chiffre, parce que ces fichiers contiennent les donnees de chaque commerce inscrit et
# la cle privee de l'autorite du magasin ; Google ne voit passer que des noms et des contenus
# illisibles. Les cles sont dans la configuration de rclone de ce poste, et doivent aussi etre
# rangees ailleurs — voir le README.

param(
    [string]$Serveur = 'jumpy@192.168.1.100',
    [string]$Distant = 'apps/gestionstock/sauvegardes',
    [string]$Local = 'D:\Sauvegardes\GestionStock',
    # Les quotidiennes au-dela de ce delai sont purgees ; celles du 1er du mois sont gardees.
    [int]$RetentionJours = 90,
    # Le remote rclone chiffre ; vide pour ne pas envoyer vers Google Drive.
    [string]$Drive = 'gdrive-chiffre:'
)

$ErrorActionPreference = 'Stop'
$ssh = "$env:WINDIR\System32\OpenSSH\ssh.exe"
$scp = "$env:WINDIR\System32\OpenSSH\scp.exe"
$optionsSsh = @('-o', 'BatchMode=yes', '-o', 'ConnectTimeout=15')

New-Item -ItemType Directory -Force -Path $Local | Out-Null
$journalFichier = Join-Path $Local 'journal.log'

function Journal([string]$message) {
    $ligne = '{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $message
    Add-Content -Path $journalFichier -Value $ligne -Encoding UTF8
    Write-Output $ligne
}

# L'archive du Caddy contient la cle privee de l'autorite de certification du magasin : le dossier
# n'est lisible que par ce compte, SYSTEM et les administrateurs, et n'herite de rien.
icacls $Local /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null

# L'inventaire du serveur : empreinte et nom de chaque sauvegarde, en un seul aller-retour.
$inventaire = & $ssh @optionsSsh $Serveur "cd $Distant && sha256sum gestionstock-*.dump caddy-*.tar.gz 2>/dev/null"
if ($LASTEXITCODE -ne 0 -and -not $inventaire) {
    Journal "ERREUR : serveur $Serveur injoignable, ou aucune sauvegarde dans ~/$Distant."
    exit 1
}

$distants = foreach ($ligne in $inventaire) {
    $empreinte, $nom = $ligne -split '\s+', 2
    [pscustomobject]@{ Nom = $nom.Trim(); Empreinte = $empreinte.ToUpper() }
}

$copies = 0
$echecs = 0
foreach ($f in $distants) {
    $cible = Join-Path $Local $f.Nom
    if ((Test-Path $cible) -and (Get-FileHash -Algorithm SHA256 $cible).Hash -eq $f.Empreinte) {
        continue
    }

    $partiel = "$cible.partiel"
    & $scp @optionsSsh -q "${Serveur}:$Distant/$($f.Nom)" $partiel
    if ($LASTEXITCODE -eq 0 -and (Get-FileHash -Algorithm SHA256 $partiel).Hash -eq $f.Empreinte) {
        Move-Item -Force $partiel $cible
        Journal ("  copiee : {0} ({1:N0} Ko)" -f $f.Nom, ((Get-Item $cible).Length / 1KB))
        $copies++
    } else {
        Remove-Item -Force -ErrorAction SilentlyContinue $partiel
        Journal "  ERREUR : $($f.Nom) n'a pas pu etre copiee intacte."
        $echecs++
    }
}

# La purge locale. Plus longue que celle du serveur (trente jours) : ce poste est la ou l'on vient
# chercher une donnee perdue depuis longtemps.
$limite = (Get-Date).AddDays(-$RetentionJours)
Get-ChildItem $Local -File | Where-Object { $_.Name -match '^(gestionstock|caddy)-(\d{8})-' } | ForEach-Object {
    $jour = [datetime]::ParseExact($Matches[2], 'yyyyMMdd', $null)
    if ($jour -lt $limite -and $jour.Day -ne 1) {
        Remove-Item -Force $_.FullName
        Journal "  purgee : $($_.Name)"
    }
}

# La fraicheur de la derniere sauvegarde. Copier fidelement des fichiers qui ont cesse d'arriver
# donnerait un faux sentiment de securite : sauvegarder.sh peut s'arreter sans que rien d'autre ne
# le montre.
$derniere = $distants | Where-Object { $_.Nom -match '^gestionstock-(\d{8})-' } |
    ForEach-Object { [datetime]::ParseExact(($_.Nom -replace '^gestionstock-(\d{8})-.*', '$1'), 'yyyyMMdd', $null) } |
    Sort-Object -Descending | Select-Object -First 1

# L'envoi vers Google Drive. `copy` et non `sync` : un dossier local vide par accident ferait
# sinon effacer les copies distantes, c'est-a-dire les seules restantes. La purge distante est a
# part, et suit la meme regle que la locale : les quotidiennes au-dela de $RetentionJours jours,
# jamais celles du 1er du mois (nom en AAAAMM01).
#
# Seules les sauvegardes partent, par liste d'inclusion : ni le journal, ni surtout le fichier des
# cles, qui s'il etait envoye rendrait le chiffrement inutile.
$driveEchoue = $false
if ($Drive) {
    $rclone = (Get-Command rclone.exe -ErrorAction SilentlyContinue).Source
    if (-not $rclone) {
        $rclone = (Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Rclone.Rclone*\rclone-*\rclone.exe" -ErrorAction SilentlyContinue |
            Select-Object -First 1).FullName
    }
    if (-not $rclone) {
        Journal "ERREUR : rclone est introuvable, rien n'est envoye vers Google Drive. « winget install Rclone.Rclone »."
        $driveEchoue = $true
    } else {
        # Le compte rendu de rclone passe par un fichier et non par stderr : sous PowerShell 5.1,
        # rediriger stderr emballe les lignes dans des erreurs qui les repetent, et le decompte
        # des fichiers envoyes en etait fausse.
        $compteRendu = Join-Path $env:TEMP 'copier-sauvegardes-rclone.log'
        Remove-Item -Force -ErrorAction SilentlyContinue $compteRendu
        $filtres = @('--include', 'gestionstock-*.dump', '--include', 'caddy-*.tar.gz')
        & $rclone copy $Local $Drive @filtres -v --log-file $compteRendu
        $codeRclone = $LASTEXITCODE
        $lignes = if (Test-Path $compteRendu) { Get-Content $compteRendu } else { @() }
        if ($codeRclone -ne 0) {
            Journal "  ERREUR : l'envoi vers Google Drive a echoue (rclone $codeRclone)."
            ($lignes | Where-Object { $_ -match 'ERROR|CRITICAL' } | Select-Object -First 3) |
                ForEach-Object { Journal "    $_" }
            $driveEchoue = $true
        } else {
            $envoyes = @($lignes | Where-Object { $_ -match 'INFO\s+:.*: Copied \(' }).Count
            # Des --filter, lus dans l'ordre, et non --include avec --exclude : rclone ne garantit
            # pas l'ordre de ces deux-la entre eux, et la regle qui epargne le 1er du mois pouvait
            # passer apres celle qui designe le fichier a purger.
            $purge = @('--filter', '- *-??????01-*', '--filter', '+ gestionstock-*.dump',
                       '--filter', '+ caddy-*.tar.gz', '--filter', '- *')
            & $rclone delete $Drive @purge --min-age "${RetentionJours}d" --log-file $compteRendu
            Journal "  Google Drive : $envoyes fichier(s) envoye(s), chiffres."
        }
    }
}

if ($echecs -gt 0) {
    Journal "Termine avec $echecs echec(s) : $copies copie(s)."
    exit 1
}
if (-not $derniere -or $derniere -lt (Get-Date).Date.AddDays(-2)) {
    Journal "ATTENTION : la derniere sauvegarde du serveur date du $('{0:dd/MM/yyyy}' -f $derniere). sauvegarder.sh ne tourne plus : voir ~/$Distant/journal.log."
    exit 2
}
if ($driveEchoue) {
    Journal "Copies locales a jour, mais Google Drive n'a pas ete mis a jour."
    exit 3
}
Journal "A jour : $copies nouvelle(s) copie(s), derniere sauvegarde du $('{0:dd/MM/yyyy}' -f $derniere)."
exit 0
