<#
.SYNOPSIS
    Gera o .apk assinado do Event Beazeth, pronto para instalar no tablet.

.DESCRIPTION
    Um comando so, do zero ao arquivo na mao:

        .\empacotar.ps1

    O .apk sai em `dist\EventBeazeth-<versao>.apk`. Como esta pasta vive dentro
    do OneDrive, o arquivo ja sobe para a nuvem sozinho -- da para mandar o link
    em vez do anexo, que o WhatsApp costuma recusar por tamanho.

    O que ele faz, em ordem: confere que a chave de assinatura esta configurada,
    confere que o app vai falar com o servidor de producao, compila em modo
    release (com R8 e corte de recursos), assina, copia com um nome que diz a
    versao, e por fim RELE o arquivo pronto para provar que a assinatura e a
    certa -- e nao a chave de depuracao, que o Android instala mas nunca deixa
    atualizar por cima.

.PARAMETER Versao
    Numero da versao nova, no formato x.y.z. Ex.: -Versao 1.1.0

    Sem ele, empacota a versao que ja esta no `build.gradle.kts`. Com ele, o
    `versionName` vira o que voce pediu e o `versionCode` sobe um -- o codigo e
    o numero que o Android compara para saber se o .apk e mais novo que o
    instalado, e ele precisa subir a cada entrega.

.PARAMETER Instalar
    Alem de gerar, instala no aparelho conectado (emulador ou tablet no cabo).
    Use antes de mandar: e o unico jeito de exercitar o R8 de verdade. Uma
    regra de `proguard-rules.pro` faltando compila, instala e so quebra quando
    a tela que usa aquela classe abre.

.EXAMPLE
    .\empacotar.ps1
    .\empacotar.ps1 -Versao 1.1.0 -Instalar
#>
param(
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Versao,
    [switch]$Instalar
)

$ErrorActionPreference = 'Stop'
$raiz = $PSScriptRoot
$gradleKts = Join-Path $raiz 'app\build.gradle.kts'

function Passo($texto) { Write-Host "`n== $texto" -ForegroundColor Cyan }
function Aviso($texto) { Write-Host "   $texto" -ForegroundColor Yellow }
function Erro($texto) { Write-Host "`n!! $texto`n" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------------- Java
#
# O Gradle deste projeto pede JDK 21. Procurar em vez de exigir a variavel de
# ambiente porque quem instala o Android Studio ganha um JDK junto e nunca
# ficou sabendo.
Passo 'Procurando o JDK'
$candidatos = @(
    $env:JAVA_HOME,
    'C:\Users\Windows\.jdks\jbr-21.0.11',
    'C:\Program Files\Android\Android Studio\jbr'
)
$jdk = $null
foreach ($c in $candidatos) {
    if ($c -and (Test-Path (Join-Path $c 'bin\java.exe'))) { $jdk = $c; break }
}
if (-not $jdk) {
    Erro "Nenhum JDK encontrado. Instale o JDK 21 ou aponte JAVA_HOME para ele."
}
$env:JAVA_HOME = $jdk
Write-Host "   $jdk"

# ------------------------------------------------------------------- a chave
Passo 'Conferindo a chave de assinatura'
$propriedades = Join-Path $raiz 'keystore.properties'
if (-not (Test-Path $propriedades)) {
    Erro @"
Falta o arquivo keystore.properties.

    copy keystore.properties.exemplo keystore.properties

e preencha a senha. Sem ele o .apk sai sem assinatura, e um .apk sem
assinatura nao instala em aparelho nenhum.
"@
}
$dados = @{}
Get-Content $propriedades | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') { $dados[$Matches[1]] = $Matches[2] }
}
$caminhoDaChave = $dados['storeFile']
if (-not [System.IO.Path]::IsPathRooted($caminhoDaChave)) {
    $caminhoDaChave = Join-Path $raiz $caminhoDaChave
}
if (-not (Test-Path $caminhoDaChave)) {
    Erro "keystore.properties aponta para uma chave que nao existe:`n    $caminhoDaChave"
}
if ($dados['storePassword'] -like '*COLOQUE_A_SENHA*') {
    Erro 'A senha em keystore.properties ainda e o texto de exemplo.'
}
Write-Host "   $caminhoDaChave"

# --------------------------------------------------------------- o servidor
#
# O .apk de release fala com quem estiver no `apiBase` padrao. Um release
# apontado para `10.0.2.2` (o servidor local) instala e abre, e so falha no
# login, sem dizer por que -- entao a conferencia e aqui, antes de compilar.
Passo 'Conferindo para onde o app vai falar'
$fonte = Get-Content $gradleKts -Raw -Encoding UTF8
if ($fonte -notmatch '\?:\s*"(https://[^"]+)"') {
    Erro 'O apiBase padrao em build.gradle.kts nao e um endereco https. Um release nao fala em texto puro.'
}
$servidor = $Matches[1]
Write-Host "   $servidor"

# ----------------------------------------------------------------- a versao
if ($Versao) {
    Passo "Marcando a versao $Versao"
    if ($fonte -notmatch 'versionCode\s*=\s*(\d+)') { Erro 'Nao achei versionCode em build.gradle.kts.' }
    $codigoNovo = [int]$Matches[1] + 1
    $fonte = $fonte -replace 'versionCode\s*=\s*\d+', "versionCode = $codigoNovo"
    $fonte = $fonte -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$Versao`""
    # Sem BOM: o Gradle le, mas o BOM aparece no diff e suja o arquivo.
    [System.IO.File]::WriteAllText($gradleKts, $fonte, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "   versionName $Versao, versionCode $codigoNovo"
}

if ($fonte -notmatch 'versionName\s*=\s*"([^"]+)"') { Erro 'Nao achei versionName em build.gradle.kts.' }
$versaoAtual = $Matches[1]
$null = $fonte -match 'versionCode\s*=\s*(\d+)'
$codigoAtual = $Matches[1]

# --------------------------------------------------------------- compilando
Passo "Compilando a versao $versaoAtual (codigo $codigoAtual)"
Aviso 'A primeira vez demora alguns minutos; o R8 percorre o app inteiro.'
& (Join-Path $raiz 'gradlew.bat') ':app:assembleRelease' '--console=plain' '-q'
if ($LASTEXITCODE -ne 0) { Erro 'A compilacao falhou. A saida acima diz onde.' }

$gerado = Join-Path $raiz 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $gerado)) { Erro "O build terminou mas nao achei o .apk em:`n    $gerado" }

# ------------------------------------------------------------------ o nome
#
# Nome com a versao dentro, e nao `app-release.apk`: quem recebe tres .apk com
# o mesmo nome na pasta de downloads nao sabe qual e qual.
$pastaFinal = Join-Path $raiz 'dist'
if (-not (Test-Path $pastaFinal)) { $null = New-Item -ItemType Directory -Path $pastaFinal }
$destino = Join-Path $pastaFinal "EventBeazeth-$versaoAtual.apk"
Copy-Item $gerado $destino -Force

# ------------------------------------------------------------- a assinatura
#
# Conferir o arquivo PRONTO, e nao confiar na configuracao: o build sai
# feliz sem assinar quando falta a chave, e a chave de depuracao instala mas
# nunca deixa atualizar por cima -- as duas falhas so aparecem no aparelho.
Passo 'Conferindo a assinatura do arquivo pronto'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools'
$apksigner = $null
if (Test-Path $sdk) {
    $maisNova = Get-ChildItem $sdk -Directory | Sort-Object Name | Select-Object -Last 1
    if ($maisNova) { $apksigner = Join-Path $maisNova.FullName 'apksigner.bat' }
}
if ($apksigner -and (Test-Path $apksigner)) {
    $saida = & $apksigner verify --print-certs $destino
    $dono = ($saida | Select-String 'certificate DN:').ToString()
    $digital = ($saida | Select-String 'SHA-256 digest:').ToString()
    if ($dono -match 'CN=Android Debug') {
        Erro @"
O .apk saiu assinado com a CHAVE DE DEPURACAO.

Ele ate instala, mas nenhuma atualizacao futura entra por cima: para trocar
de versao seria preciso desinstalar, e o que estiver so no aparelho some.
Confira o keystore.properties.
"@
    }
    Write-Host "   $($dono.Trim())"
    Write-Host "   $($digital.Trim())"
} else {
    Aviso 'apksigner nao encontrado; nao deu para conferir a assinatura.'
}

# ------------------------------------------------------------------- pronto
$tamanho = [math]::Round((Get-Item $destino).Length / 1MB, 1)
Write-Host "`n== Pronto" -ForegroundColor Green
Write-Host "   $destino"
Write-Host "   $tamanho MB, versao $versaoAtual, falando com $servidor"
Write-Host @"

   Como mandar
     Esta pasta esta dentro do OneDrive, entao o arquivo ja sobe sozinho.
     Clique com o botao direito nele, "Compartilhar", e mande o link -- o
     WhatsApp recusa anexo grande, o link nao.

   Como ela instala
     Abre o link, baixa, toca no arquivo. Na primeira vez o Android pergunta
     se aquele aplicativo pode instalar outros; e so autorizar uma vez.
"@
if ($codigoAtual -eq '2') {
    Write-Host @"
   Atencao, primeira entrega assinada
     Se ela ja tem uma versao de teste instalada, esta NAO entra por cima:
     assinatura diferente. Precisa desinstalar a antiga antes. O que esta na
     conta volta no login; o que foi escrito sem conta, nao.
"@ -ForegroundColor Yellow
}

# ---------------------------------------------------------------- instalar
if ($Instalar) {
    Passo 'Instalando no aparelho conectado'
    $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (-not (Test-Path $adb)) { Erro "adb nao encontrado em $adb" }
    $r = & $adb install -r $destino
    Write-Host "   $($r -join "`n   ")"
    if ($r -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Aviso 'O aparelho tem uma versao assinada com outra chave. Desinstale antes:'
        Aviso '    adb uninstall com.beazeth.notifier'
    }
}
