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
#
# Na primeira vez o script se configura sozinho: pergunta a senha, confere que
# ela abre a chave, e grava o `keystore.properties`. Nas seguintes, nem
# pergunta. Editar um arquivo de propriedades a mao antes do primeiro build era
# o unico passo aqui que exigia abrir um editor -- e o passo em que se erra a
# senha e so se descobre quatro minutos depois, no meio da saida do Gradle.
Passo 'Conferindo a chave de assinatura'
$propriedades = Join-Path $raiz 'keystore.properties'

function Ler-Propriedades($caminho) {
    $d = @{}
    Get-Content $caminho | ForEach-Object {
        if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') { $d[$Matches[1]] = $Matches[2] }
    }
    return $d
}

function Testar-Senha($chave, $alias, $senha) {
    # `-storepass:env` e nao `-storepass`: a senha na linha de comando aparece
    # para qualquer processo que liste os outros. Na variavel de ambiente do
    # processo, nao.
    $env:SENHA_DA_CHAVE = $senha
    & (Join-Path $jdk 'bin\keytool.exe') -list -keystore $chave -alias $alias `
        -storepass:env SENHA_DA_CHAVE | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    Remove-Item Env:\SENHA_DA_CHAVE
    return $ok
}

# Onde a chave esta e como ela se chama: do arquivo de verdade se ele ja
# existe, do exemplo se nao.
$exemplo = Ler-Propriedades (Join-Path $raiz 'keystore.properties.exemplo')
$arquivoDaChave = $exemplo['storeFile']
$aliasDaChave = $exemplo['keyAlias']
$senhaGuardada = $null

if (Test-Path $propriedades) {
    $dados = Ler-Propriedades $propriedades
    if ($dados['storeFile']) { $arquivoDaChave = $dados['storeFile'] }
    if ($dados['keyAlias']) { $aliasDaChave = $dados['keyAlias'] }
    $senhaGuardada = $dados['storePassword']
}

$caminhoDaChave = $arquivoDaChave
if (-not [System.IO.Path]::IsPathRooted($caminhoDaChave)) {
    $caminhoDaChave = Join-Path $raiz $caminhoDaChave
}
if (-not (Test-Path $caminhoDaChave)) {
    Erro "Nao achei a chave de assinatura em:`n    $caminhoDaChave`n`nE o arquivo android.keystore. Se ele estiver noutro lugar, ponha o caminho`nem keystore.properties (linha storeFile) e rode de novo."
}

# Perguntar e o caminho para TRES situacoes, e nao so para a primeira vez: nao
# ha arquivo, ha arquivo com o texto de exemplo (copiou e parou no editor), ou
# ha senha que nao abre a chave (digitou errado, ou a chave mudou). Nas tres a
# resposta util e a mesma - pedir a senha e arrumar o arquivo -, e nao mandar
# quem esta empacotando ir editar propriedade a mao.
$precisaPerguntar = $true
if ($senhaGuardada -and ($senhaGuardada -notlike '*COLOQUE_A_SENHA*')) {
    if (Testar-Senha $caminhoDaChave $aliasDaChave $senhaGuardada) {
        $precisaPerguntar = $false
    } else {
        Aviso 'A senha guardada nao abre a chave. Vou pedir de novo.'
    }
}

if ($precisaPerguntar) {
    Write-Host ''
    Write-Host '   Preciso da senha da chave de assinatura:' -ForegroundColor Yellow
    Write-Host "   $caminhoDaChave" -ForegroundColor Yellow
    Write-Host '   Pergunto uma vez so - guardo em keystore.properties, que o git ignora.' -ForegroundColor Yellow
    Write-Host ''

    $senha = $null
    for ($tentativa = 1; $tentativa -le 3; $tentativa++) {
        $secreta = Read-Host '   Senha' -AsSecureString
        $ponteiro = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secreta)
        $tentada = [Runtime.InteropServices.Marshal]::PtrToStringAuto($ponteiro)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro)

        if (Testar-Senha $caminhoDaChave $aliasDaChave $tentada) { $senha = $tentada; break }
        if ($tentativa -eq 3) { Erro 'Senha errada tres vezes. Nada foi gravado.' }
        Aviso 'Senha errada. Tente de novo.'
    }

    @"
# Escrito por empacotar.ps1. Nao versionado, de proposito: quem tem a chave e a
# senha assina atualizacao em nome do app.
storeFile=$arquivoDaChave
storePassword=$senha
keyPassword=$senha
keyAlias=$aliasDaChave
"@ | Set-Content -Path $propriedades -Encoding UTF8

    Write-Host '   Senha confere, guardada. Nao pergunto de novo.' -ForegroundColor Green
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

# O caminho na area de transferencia e a pasta ja aberta com o arquivo
# selecionado: o passo seguinte e sempre o mesmo, mandar o arquivo, e ele nao
# precisa comecar por procurar onde ele foi parar.
try { Set-Clipboard -Value $destino; Write-Host '   (caminho copiado)' } catch { }
Start-Process explorer.exe "/select,`"$destino`""
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
