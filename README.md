# Event Beazeth — app Android nativo

App Android em Kotlin + Jetpack Compose, consumindo a API do
`event-beazeth` (Flask + Postgres no Neon).

Repositório irmão: `../event-beazeth/` — o site e o servidor.
Não confundir com `../event-beazeth-android/`, que é o TWA (o site embrulhado
num `.apk`). Este aqui é o app de verdade; aquele fica até este ficar pronto.

## Por que existe

O TWA resolvia distribuição, não experiência. Quatro queixas concretas o
condenaram, e todas têm a mesma raiz — **cada tela vinha do servidor**:

| Queixa | Causa | O que o nativo faz |
| --- | --- | --- |
| Parece site | rolagem, animação e teclado são os do navegador | componentes do Android |
| Demora para abrir e trocar de tela | ida ao Brasil→Oregon a cada navegação | lê do banco local, instantâneo |
| Não funciona sem internet | a tela é HTML que vem do servidor | banco local é a fonte da tela |
| Notificações falhas | Web Push depende do Chrome vivo | FCM, entregue pelo sistema |

## A decisão que define tudo: offline-first

Um app nativo que busca do servidor a cada tela teria **as mesmas** três
primeiras queixas — trocaria o Chrome por Compose e continuaria esperando a
rede. Então a regra é:

> A tela nunca espera a rede. Ela lê do banco do aparelho. A rede sincroniza
> por baixo, quando dá.

Na prática:

- **Room** (SQLite) é a fonte de verdade da interface
- Toda leitura vem dele; toda escrita entra nele primeiro e aparece na hora
- Um worker envia as mudanças pendentes e puxa as do servidor
- Sem sinal, tudo continua funcionando; a fila drena quando o sinal volta

O custo é conflito: a mesma tarefa editada no site e no celular offline. A
regra inicial é a mais simples que funciona — **quem escreveu por último
vence**, comparando `updated_at`. Se um dia isso doer, aí sim entra algo mais
esperto.

## Como as duas pontas ficam sincronizadas

Não dá para sincronizar duas interfaces. O que se sincroniza é o **contrato**:

```
event-beazeth/app/api_contract.py    <- a lista de rotas, métodos e campos
        |
        +-- test_contract.py  falha se o servidor divergir do contrato
        +-- test_api.py       falha se o formato das respostas mudar
```

Mudou a API? O contrato muda no mesmo commit, os testes acusam, e este app
descobre pelo diff — não em produção. **Rota nova que não entrar no contrato
faz o teste falhar**, justamente para não existir funcionalidade que o site tem
e o app não sabe que existe.

## Estado atual

**Fase 1 — API: completa e testada.**

| | |
| --- | --- |
| `POST /api/auth/login` e `/signup` | token `Bearer`, assinado com a `SECRET_KEY`; sem tabela nova |
| `GET /api/me` | valida na abertura se o token guardado ainda vale |
| `GET /api/todo`, `/api/hydration`, `/api/tags` | não existiam; sem elas não há o que mostrar |
| `GET /api/sync?since=` | **a espinha dorsal**: o que mudou e o que sumiu |
| `updated_at` em 7 tabelas + tabela `deletions` | migração aditiva, aplicada sozinha no deploy |
| Gatilhos no Postgres | carimbam e registram exclusão em toda escrita |

**Por que gatilhos e não código Python.** São 21 pontos de escrita em sete
módulos. Bastaria esquecer um para aquela tabela parar de sincronizar — e o
modo de falhar é cruel: a linha grava, o site mostra tudo certo, e semanas
depois se descobre que o celular nunca recebeu. Nada quebra, nada avisa.

No gatilho é uma regra só, e vale para todo caminho de escrita: o app, um
script de manutenção, ou um `UPDATE` feito na mão no painel do Neon. O teste
confirma os três.

**Testes:** 124 checks (migração sobre banco com dados, gatilhos, sincronização,
API, contrato). Os que mais importam:

- token de uma conta não lê dado de outra
- token assinado com outra chave é recusado
- o hash da senha não aparece em nenhuma resposta
- nada se perde entre duas sincronizações
- apagar a conta não explode (sem a proteção no gatilho, a exclusão inteira
  falharia por chave estrangeira)

**Fase 2 — login e identidade visual: rodando em emulador.**

Kotlin + Compose, Material 3, `minSdk 26`, `compileSdk 36`. O que já funciona:
tela de login contra a API real, token guardado em DataStore, e a sessão
restaurada na abertura via `GET /api/me`.

O `.apk` sai em `app/build/outputs/apk/debug/app-debug.apk` — **11,1 MB**,
contra 1,17 MB do TWA. A diferença é o app existir de verdade: nenhum HTML, CSS
ou JS lá dentro.

### O design é o do site, não um parecido

A primeira tela de login usou os componentes prontos do Material 3 e emprestou
só as cores. O veredito de quem olhou foi "totalmente diferente do site", e
estava certo: fonte do Android, rótulo flutuante em vez de fixo, sem cartão,
sem degradê, sem as bolhas de luz.

O que existe agora é porte token a token, com o site aberto do lado:

| No site | No app |
| --- | --- |
| `:root` do `base.css` | `ui/theme/Tokens.kt`, nome por nome |
| Quicksand e Baloo 2 | as mesmas, dentro do `.apk` |
| `body` + `.bg-candy` | `FundoDoce`, quatro degradês desenhados |
| `.auth-card`, `.event-form`, `.btn-primary` | `ui/componentes/Doces.kt` |

Os componentes moram fora da tela de login de propósito: as sete telas da Fase
3 usam os mesmos cartão, campo e botão.

**Como se confere.** Captura do site em 360 px com Playwright, captura do
emulador com `adb exec-out screencap`, e as duas recortadas lado a lado. Foi
assim que apareceram os dois erros que a olho nu passariam: o peso do rótulo e
a opacidade do cartão. Comparar de memória não pega nenhum dos dois.

### Construir

Pelo Android Studio: abrir a pasta e apertar Run. O JDK do Gradle ja fica
gravado em `.gradle/config.properties`, fora do controle de versao.

Pela linha de comando, apontando para o **JDK 21**:

```bash
export JAVA_HOME="C:/Users/Windows/.jdks/jbr-21.0.11"
./gradlew assembleDebug
```

O `.apk` de debug aponta para **produção** de propósito: é ele que se instala
num celular para experimentar, e `10.0.2.2` só existe dentro do emulador.
Para desenvolver contra o servidor local:

```bash
./gradlew assembleDebug -PapiBase=http://10.0.2.2:5055      # emulador
./gradlew assembleDebug -PapiBase=http://192.168.x.x:5055   # celular na rede
```

### Pedras do caminho, para não tropeçar de novo

**O JDK 25 embutido no Android Studio não compila este projeto.** O plugin do
Android 8.7.3 não sabe interpretar a versão `25.0.2` e o build morre com uma
mensagem que é literalmente `* What went wrong: 25.0.2` — nenhuma pista do que
aconteceu. Use o JDK 21, que o proprio Studio baixa. O Gradle 8.11.1 roda em
ambos; quem recusa é o plugin.

**O emulador não acha a imagem do sistema.** `FATAL | Cannot find AVD system
path. Please define ANDROID_SDK_ROOT`. As imagens de sistema foram baixadas no
SDK do Bubblewrap, e o binário do emulador que roda é o do SDK do Android
Studio — que resolve `image.sysdir` na própria raiz. Suba assim:

```bash
export ANDROID_SDK_ROOT="C:/Users/Windows/.bubblewrap/android_sdk"
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Pixel_7
```

**O relógio do emulador não é o da máquina.** Ele roda em GMT; o servidor local,
em GMT-3. Fora que é chato para conferir horários, isso já revelou um bug de
verdade — ver a tela de água em "Como isto foi verificado".

**A fonte variável ignorou o peso, sem avisar.** A primeira versão empacotou
Quicksand e Baloo 2 como fontes variáveis e pediu os pesos por
`variationSettings`. O Android renderizou tudo na instância padrão — que na
Quicksand é 300 —, então os rótulos em negrito saíram mais leves que o texto
normal. Nenhum erro, nenhum aviso: só fica evidente ao lado do site. A solução é
um arquivo por peso, gerado com `fonttools varLib.instancer`. De quebra, o
`subset` para o alfabeto latino levou as três fontes de 808 KB para 145 KB.

**`Family-Italic[wght].ttf` vem antes de `Family[wght].ttf` na ordem
alfabética.** O script pegava o primeiro arquivo variável da pasta da família no
`google/fonts` — e como `-` (0x2D) vem antes de `[` (0x5B), o primeiro é a
itálica. Cinco das dez duplas foram empacotadas tortas: Glam (Montserrat),
Bubble (Nunito), Cotton (Raleway), Diary (Rubik) e Chic (Urbanist). O que
denunciou não foi o texto — uma sem-serifa inclinada de leve passa por escolha
de design — mas os emoji da barra de baixo: a fonte de texto não tem emoji,
então o Android ia buscar o desenho na fonte do sistema e o inclinava para casar
com o estilo que o arquivo declarava. Ícone italico é coisa que não existe, e aí
salta aos olhos. O filtro é uma linha (`"italic" not in nome.lower()`), e a
conferência é mais barata ainda:

```python
TTFont(f)["OS/2"].fsSelection & 1   # 1 = itálica
```

**Desligar o formulário inteiro empurra o foco para fora dele.** Enquanto o
login estava no ar, os dois campos e o botão ficavam `habilitado = false` — e o
único clicável que sobrava no cartão era o link "Criar uma agora". O foco
escorregava para lá sozinho, e como no Compose um `clickable` com foco responde
ao Enter, a tecla seguinte abria a tela de criar conta em vez de tentar entrar
de novo. Quem errava a senha caía nisso *toda vez*. São duas correções: o link
também se desliga durante o pedido (um `clickable` desligado não recebe foco), e
quando o servidor recusa o cursor volta para o campo de senha — que é onde a
pessoa precisa digitar, já que o e-mail quase sempre estava certo.

**Arrastar com `offset(Dp)` e com `graphicsLayer` não são a mesma coisa.** A
primeira versão do arrasto usava `Modifier.offset(x.dp, y.dp)`, que roda na
composição e refaz medida a cada quadro. Trocar por `graphicsLayer { translationX = ... }`
parecia o equivalente exato da regra do site ("arrastar post-it é `transform`,
não `position`") — mas mover o cartão só na pintura tira o Compose do lugar
onde ele calcula toque e ancoragem de menu, e o resultado medido foi um arrasto
que movia dois post-its de uma vez. `Modifier.offset { }`, a versão com
lambda, é o meio-termo certo: pula recomposição e medida, roda só no
posicionamento, e o cartão continua onde o Compose acha que ele está. Depois da
troca, um arrasto de 330×465 px moveu o post-it 119×168 dp — exatos, na
densidade 2,75 do aparelho.

**`Modifier.pointerInput(chave)` congela a lambda.** Ela é relembrada só
quando a chave muda, então qualquer valor de recomposição capturado ali dentro
fica velho para sempre. Estado (`var x by remember`) continua certo, porque o
que se captura é o acessor; valor derivado, não. Custou um arrasto que não
fazia nada e não dizia por quê.

**A skin do AVD manda mais que o `config.ini`.** Copiar um AVD de celular e
trocar `hw.lcd.width/height` não muda nada enquanto `skin.name` apontar para o
aparelho antigo — o emulador subia 1080x2400 com o config dizendo 1920x1200.

**`--` é ilegal dentro de comentário XML.** Um comentário no
`AndroidManifest.xml` com um travessão derrubou o build com
`ManifestMerger2$MergeFailureException: Error parsing`, sem dizer a linha nem o
motivo.

**Trocar arquivo em `res/` pede `clean`.** Depois de substituir as fontes, o
build incremental deu `BUILD SUCCESSFUL` e o `.apk` continuou do tamanho antigo,
com os recursos velhos empacotados. Um `./gradlew clean assembleDebug` resolveu.
Se o tamanho do `.apk` não mexer depois de uma troca de recurso, é isto.

**Em arquivo `.properties`, barra invertida é escape.** Um `sdk.dir` escrito com
barra invertida vira um caminho sem separador nenhum, em silêncio, e o build
falha com "sintaxe do nome do arquivo incorreta" — mensagem que não diz nada
sobre a causa. Use barra normal.

**Um BOM no início do `gradle.properties` cola na primeira chave.** A linha vira
`\uFEFForg.gradle.jvmargs`, que não é chave nenhuma, e o ajuste de memória é
ignorado sem aviso. O arquivo tem que começar em texto puro.

O teto de memória já foi 1 GB por necessidade — o JDK do Bubblewrap era de 32
bits e não reservava mais que isso. Com o JDK 21 de 64 bits isso saiu do
caminho, e hoje são 3 GB.

**Fase 3 — banco local, sincronização e as sete telas: funcionando.**

O app abre e desenha do banco do aparelho. A rede acontece por baixo.

| | |
| --- | --- |
| **Room** | sete tabelas espelhando o que `/api/sync` entrega, mais a fila de escritas |
| **Sincronizador** | sobe a fila, depois baixa a diferença — nessa ordem, sempre |
| **WorkManager** | roda de hora em hora e a cada escrita; sobrevive ao app fechado |
| **Sete telas** | Post-its, Agenda, Planner, To-do, Pomodoro, Água, Aparência |
| **Criar conta** | mesma casca da tela de entrar, contra `/api/auth/signup` |

**A ordem da sincronização não é detalhe.** Primeiro sobe o que está na fila,
depois baixa o que mudou. Ao contrário, uma tarefa criada offline seria
sobrescrita pela resposta do servidor — que ainda não sabe dela — e sumiria da
tela antes de chegar a subir.

**Ids negativos** marcam a linha que ainda não existe no servidor. Quando ele
emite o id de verdade, a linha provisória é trocada e as pendências seguintes
têm o caminho reescrito — senão, criar uma tarefa offline e já marcá-la como
feita mandaria um PATCH para um id que nunca existiu lá.

### Onde o app diverge do site de propósito

Três telas não copiam o layout do site, e em todas o motivo é o mesmo: o gesto
de lá é de mouse numa tela larga.

| Tela | No site | No app | Por quê |
| --- | --- | --- | --- |
| Calendário | grade do mês | lista por dia | 30 células em 360 dp dão 45 dp cada: nenhum título cabe |
| Planner | 7 colunas de 24 h | um dia por vez, em abas | coluna de 50 dp não comporta o nome de um bloco |

**Os post-its saíram desta lista.** A primeira versão os transformava em lista,
e estava errado: posição e inclinação são memória espacial — "o do dentista fica
no canto de cima" — e uma lista ordenada por id apaga isso. Hoje o app tem o
quadro de verdade, com arrastar, as seis cores, a inclinação derivada do id e o
botão Organizar; o que se adapta é o enquadramento, que rola nos dois eixos e só
cresce até onde há post-it.

### Como isto foi verificado

Não contra produção. Um servidor Flask local com o Postgres de desenvolvimento,
semeado com post-its, tarefas, blocos, eventos e água, e o app compilado com
`-PapiBase=http://10.0.2.2:5055`. O ciclo inteiro, medido no emulador:

- login → `/api/sync` → Room → tela pintada
- segunda sincronização usou `?since=` com o carimbo do relógio do banco
- **rede desligada** → tarefa criada aparece na hora, fila mostra 1 pendência
- **rede de volta** → `POST /api/todo 201` sem ninguém tocar em nada
- criar conta pela tela do app → 201 → entra direto

E foi assim que apareceu um bug que só existe fora do laboratório: o emulador
roda em GMT, o servidor em GMT-3. A tela de água pedia o dia do APARELHO e via
zero copo depois de beber três, porque `app/db/hydration.py` diz — de propósito
— que o dia do consumo é o dia local do SERVIDOR. Hoje a tela segue o dia que o
servidor mandou, e mostra a data quando ela não é a de hoje, em vez de escrever
"Hoje" e mentir.

### Aparência completa: 10 paletas, 10 fontes, modo escuro

As paletas são **geradas** de `themes.css` (`gerar_paletas.py` no scratchpad),
não digitadas: são 17 cores × 20 combinações, e um valor errado não quebraria
nada — só deixaria um tema levemente diferente do site, para sempre.

As 18 famílias de fonte estão dentro do `.apk`, instanciadas por peso com
`fonttools varLib.instancer` e recortadas para o alfabeto latino: **1,8 MB** no
lugar de vários megabytes, com todos os acentos conferidos.

**Como as telas não precisaram mudar.** `Doce` e `TipografiaBeazeth` continuam
sendo escritos como antes (`Doce.destaque`), mas agora são propriedades com
getter `@Composable` que leem um `CompositionLocal`. Trocar de tema reexecuta
quem as leu, e nada mais. A alternativa era passar a paleta por toda a árvore,
ou ler `LocalPaleta.current` em cada tela — dezenas de pontos para alguém
esquecer um. O preço: dentro de `drawBehind` não há contexto de composição, e lá
é preciso capturar `val cores = Doce` antes do modificador.

**O sol e a lua ficam no topo, ao lado da paleta.** Escolher paleta é coisa de
uma vez por mês; acender e apagar a luz é de todo dia — enterrado numa tela de
ajuste, custaria três toques por anoitecer. Os dois traços são os `d` dos
`<svg>` de `partials/sidebar.html`, lidos pelo `PathParser`: redesenhar a lua
com dois círculos daria uma lua parecida, e "parecida" é o que aparece quando se
põe o app do lado do site. As curvas de tempo também são as do CSS — opacidade
em 0,26 s, giro e escala em 0,32 s com uma bezier que passa de 1 e volta. São
duas animações e não uma justamente porque o CSS tem duas: com um valor só, o
astro que sai desaparece no mesmo instante em que para de girar, e a troca fica
dura.

### Uma divergência deliberada do site

O CSS escreve o botão primário como
`linear-gradient(110deg, var(--accent), #ff96c2)` — com o rosa **cravado**,
igual nos dez temas. No site isso passa despercebido; no app, onde trocar de
tema é recurso de vitrine, um botão azul terminando em rosa leria como defeito.
O valor cravado é exatamente o destaque clareado em 78%, então o app o calcula:
o tema padrão sai byte a byte igual, e os outros fazem o que o autor quis.

### Cantos retos, e a barra que responde ao dedo

Duas correções vindas de olhar o app rodando:

**A semana do to-do é UMA folha.** Havia um cartão por dia — sete caixas
flutuando, cada uma com sombra própria, e a semana virava uma pilha em vez de
uma página. No site é um `panel-card` só, com os dias separados por
`border-bottom: 1px dashed`. Agora é assim aqui.

**Os cantos ficaram retos.** O CSS usa 24 px nos cartões; num celular, com a
tela inteira ocupada por cartões empilhados, essa curva toda lê como uma pilha
de balas. Os raios agora vêm de `Canto` (6 dp no cartão, 4 dp nas caixas, 3 dp
nos marcadores), e a curva fica reservada ao que se toca — botões e abas
continuam com 10 dp. O cartão de entrar/criar conta é a exceção e mantém os
26 px do site: aparece uma vez, centrado, e é o primeiro contato com o app.

**A barra inferior responde ao toque.** Ícones maiores (23 sp), rótulos em
negrito, e duas animações que respondem a perguntas diferentes: o item ativo
cresce e sobe — *onde você está* —, e o ícone afunda enquanto o dedo está em
cima, voltando com mola — *o toque foi registrado*. A mola tem amortecimento
baixo de propósito: o repique ao soltar é o que dá sensação de coisa física; sem
ele o retorno é uma rampa e parece que a tela só redesenhou.

### O post-it: três defeitos no arrasto e um na cor

Todos vieram de olhar o app rodando, e nenhum aparecia em teste automático.

**Voltava para o lugar de origem por um instante.** O arrasto acumulava o
deslocamento e o zerava em `onDragEnd`, antes de o Room ter gravado a posição
nova. Nos quadros entre uma coisa e outra o cartão era desenhado na posição
ANTIGA com deslocamento zero — pulava de volta e só depois ia para onde foi
largado. Com o Room rápido ninguém via; daí o "às vezes fica onde deixei". A
posição mostrada agora é do próprio cartão, e só volta a seguir o banco depois
que o banco confirma o que foi enviado.

**Largar perto da borda de cima ou da esquerda jogava o post-it no canto.**
`moverNota` limita a `0..MAX` e a tela não sabia disso: o dedo levava o cartão
para −40, o banco gravava 0. O limite agora é o mesmo dos dois lados, então o
dedo para na borda — o que se vê é o que se grava.

**Arrastava torto.** O `pointerInput` fica dentro da camada girada, então o
deslocamento chega no sistema de coordenadas inclinado do papel; somado direto,
um arrasto reto virava um arrasto na diagonal (até 7% de erro nos 4 graus de
inclinação). Seno e cosseno devolvem o vetor para o espaço do quadro.

**A cor não pegava.** As seis cores eram bolinhas de 18 dp dentro do modo de
edição, e o cartão inteiro escuta arrasto: um dedo sobre um alvo de 18 dp
desliza uns poucos pixels sem querer, e isso bastava para o arrasto vencer o
toque. Com `input tap` — que não move um pixel — funcionava sempre, e foi por
isso que nenhum teste pegou; foi preciso alguém usar com o dedo para aparecer.

Agora **segurar abre um balão de cores acima do post-it, e tocar numa cor
aplica na hora**. Segurar é um gesto que o arrasto não disputa: um exige parar,
o outro exige mover. Os alvos são de 40 dp, o balão fica fora do papel (o dedo
taparia justamente o que a escolha muda) e não há confirmação — confirmar uma
cor que se vê seria pedir duas vezes a mesma coisa.

Um detalhe do caminho: `Modifier.clickable` devolve clique mesmo depois de meio
segundo segurando, então o texto do post-it engolia o toque longo e abria a
edição. Os dois gestos passaram para um `combinedClickable` só.

### Segurança do aparelho

**`android:allowBackup` era `true`, e passou a `false`.** O que o app guarda
localmente é (a) um cache do que já está no servidor e (b) o token de acesso,
que vale noventa dias e não é revogável um a um. Com backup ligado esse token
sai do aparelho: entra no backup automático do Google e, em muitos aparelhos,
num `adb backup`. O comentário do `TokenStore` dizia que guardar em texto claro
equivale ao cookie de sessão do navegador — e equivale, para quem tem o
aparelho na mão. O backup é outra história: leva o token para longe do
aparelho.

O que se perde desligando: nada. Restaurar o celular pede um login, e a
primeira sincronização traz tudo de volta.

O resto do aparelho continua como estava, e por decisão: o banco do Room não é
cifrado (é dado de aplicativo, ilegível sem root, e cifrar custaria SQLCipher
mais a chave a guardar em algum lugar), e não há certificate pinning (o ganho
sobre a validação de cadeia padrão só aparece contra uma CA comprometida, e o
custo é o app parar de funcionar no dia em que o certificado do Render mudar).

### A auditoria, e o que ela achou

`auditar_kotlin.py` (no scratchpad) é o `audit.py` do site traduzido para
Kotlin: sete categorias de código morto — declaração sem uso, recurso sem uso,
fonte empacotada sem uso, import sem uso, marca de pendência, código
comentado e arquivo acima de 700 linhas.

Duas lições da ferramenta, ambas do `CONSTITUTION.md`:

- **Falso positivo se conserta na ferramenta.** A primeira versão apagava os
  literais de texto antes de procurar usos, e por isso `val consulta` — lida só
  dentro de `"...$consulta..."` — aparecia como morta. A correção foi guardar
  só a parte interpolada do literal: `$nome` e `${...}` entram, prosa não.
- **`Destino.TODO` não é um TODO.** A busca por pendência agora só olha dentro
  de comentário.

Encontrou dez coisas mortas. Cinco eram lixo mesmo e saíram: `RespostaAgua`
(ninguém desserializava), `Preferencias.temaAtual`, três consultas do Room que
nenhuma tela observava e `RodapeDoCartao`, substituído pelo `RodapeComLink`
logo abaixo dele. Mais cinco imports e um KDoc de três parágrafos sobre o
logout que estava colado em `criarConta`.

**As outras três eram buracos de funcionalidade, não lixo.**
`Repositorio.editarTarefa`, `criarBloco` e `atualizarBloco` estavam corretas e
sem ninguém para chamá-las — porque as telas correspondentes não faziam aquilo.
Apagar teria sido cumprir a regra e perder a informação de que faltava algo;
foram ligadas:

- **texto da tarefa virou campo**, como o `<input class="todo-text">` do site;
  salva ao sair ou no Enter. Antes só dava para marcar e apagar, e corrigir um
  "compar pão" exigia refazer a linha. A caixinha ganhou 34 dp de alvo, porque
  era a linha inteira que alternava.
- **o planner ganhou o formulário de bloco** (`EditorDeBloco.kt`): título,
  notas, início/fim na grade de 15 minutos, dia, as seis cores e rotina.
  Arredondar na tela e não só no servidor é de propósito — o servidor também
  arredonda, mas de lá isso volta como mudança silenciosa.

De quebra, a cor `sun` faltava no `corDoPlanner`: um bloco amarelo criado no
computador caía no interpretador de hex e chegava cinza no celular.

### O `.apk` de 12 MB virou 2,9 MB

`isMinifyEnabled` estava `false`. Sem R8 não há tree-shaking nenhum: o pacote
levava Compose, OkHttp, Room e WorkManager inteiros, com tudo o que este app
nunca chama. Ligado, com `shrinkResources`, o release caiu **76%** — e as 29
fontes (1,7 MB) continuam lá, porque são alcançadas por `R.font.*` no código.

O que o R8 não enxerga é o que se alcança por reflexão, e cada regra de
`proguard-rules.pro` tem escrito o que quebra sem ela. O modo de falhar é
sempre o mesmo e sempre só no release: compila, instala, e estoura numa tela
específica. Por isso existe `-PminifyDebug`, que liga o R8 também no debug: o
release exige HTTPS e não fala com `10.0.2.2`, então é a única forma de
exercitar as regras contra o servidor local.

### O aparelho é um só: Galaxy Tab A9+

O app deixou de ser "um app de celular que também roda em tablet". O alvo é um
**Galaxy Tab A9+ de 11 polegadas**, 1920x1200 a 240 dpi — uma janela de
**800x500 dp deitada**. Isso muda o desenho inteiro, e para melhor: com essa
largura não há o aperto que justificava as adaptações do celular.

O emulador de trabalho passou a ser esse aparelho (`Galaxy_Tab_A9_Plus`, criado
a partir da imagem de sistema já instalada, com `hw.lcd.width=1920`,
`hw.lcd.height=1200`, `hw.lcd.density=240` e a skin do Pixel removida — era ela
que cravava 1080x2400 por cima do que estava no `config.ini`).

### A lateral do site, com os widgets

Onde há largura (a partir de 720 dp), a navegação é a **mesma barra lateral da
versão web**, com as mesmas partes e na mesma ordem de `partials/sidebar.html`:
marca, caixa da conta com "Sair", menu dos sete destinos, widget do pomodoro,
widget da água e o interruptor de modo escuro. As três barrinhas no topo a
escondem e a trazem de volta. Abaixo de 720 dp continua valendo a barra
inferior, pelo mesmo motivo que o site troca a lateral pela `.bottom-nav` no
celular.

**Os widgets funcionam, não são atalhos.** O do pomodoro conta de verdade, com
a ampulheta escorrendo; o da água soma copo de verdade. E os dois leem a MESMA
fonte que as telas — o que exigiu um conserto no meio do caminho:

> O `PomodoroViewModel` lia as preferências uma vez no `init` e contava num
> campo próprio. Com dois lugares olhando para o mesmo relógio, isso seriam dois
> cronômetros: apertar "Começar" na lateral não moveria a tela do pomodoro, e
> vice-versa. O estado virou um `Flow` derivado do DataStore mais um tique por
> segundo, e as funções de começar/pausar/zerar saíram do ViewModel para poderem
> ser chamadas dos dois lados. Agora quem manda é o disco, e não há como as duas
> discordarem — medido: 24:40 na tela e 24:39 no widget, um segundo de
> diferença, os dois correndo.

O widget de água só existe com o lembrete ligado, igual ao
`{% if current_user['water_enabled'] %}` do site.

### Tela cheia

Sem barra de notificação e sem barra de navegação, até que se puxe da borda
(`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). São uns 90 dp de altura de volta —
numa janela de 500 dp, quase um quinto da tela. O sistema traz as barras de
volta sozinho em várias situações (voltar do fundo, fechar o teclado, sair de
uma janela de permissão), então `onWindowFocusChanged` as esconde de novo;
sem isso a tela cheia dura até o primeiro desvio.

### A barra de cima recolhe

Rolar para baixo empurra a barra para fora, rolar para cima a traz de volta. A
conta é de um `NestedScrollConnection` na casca, então vale para qualquer tela
que role lá dentro — nenhuma delas precisa saber que existe uma barra. O
modificador ENCOLHE a altura medida junto com o deslocamento; deslocar sozinho
deixaria uma faixa vazia do tamanho da barra entre o topo e o conteúdo.

### O planner virou a semana inteira

Era uma aba por dia com uma lista de blocos, com o argumento de que sete colunas
não cabem em 360 dp. O argumento estava errado sobre o que importa: um planner
semanal existe para mostrar a FORMA da semana — o buraco da terça, as três horas
seguidas de quinta —, e uma lista de um dia apaga exatamente isso.

Agora é a grade do site: cabeçalho de dias, régua de horas, blocos em escala de
tempo, sobreposição repartindo a largura da coluna e uma linha marcando "agora".
No tablet os sete dias cabem sem rolar de lado; numa janela estreita as colunas
param na largura mínima legível e o quadro rola. Os controles do site vieram
junto (fins de semana, horário útil, zoom, novo bloco), com o zoom em dois
botões em vez de barra deslizante — um controle de 2 px de passo pede precisão
que o dedo não tem.

**Segurar e arrastar move o bloco**, encaixando na grade de 15 minutos e
preservando a duração. Tem de ser segurar: a grade está dentro de dois
roladores, e um arrasto simples seria ambíguo entre "mover o bloco" e "rolar o
quadro". Verificado: Cinema saiu de sábado 20:00 e caiu em domingo 18:00.
Redimensionar continua só no formulário — uma alça de borda útil pede uns 24 dp,
e um bloco de 15 minutos tem 13 dp de altura no zoom padrão.

### A agenda ganhou a grade do mês

Mesma história da anterior: a lista respondia "o que vem depois", mas só a grade
responde "como está o mês". Agora estão as duas, e tocar num dia liga uma na
outra — sem dia escolhido a lista mostra tudo, com um dia escolhido mostra só
aquele e o botão de criar já nasce naquela data (o `dateClick` do site). No
tablet elas ficam lado a lado, como o `calendar-layout` da versão web.

**Segurar um evento e largar num dia muda a data e preserva a hora.** Um evento
não tem duração — é um instante em `event_datetime` —, então não há o que
aumentar ou diminuir: só realocar. Dia que não pode receber não acende, porque o
servidor recusa evento no passado e um destino que falha em silêncio é pior do
que um destino que não existe.

Um defeito no caminho, que vale registrar porque não dá erro nenhum:
`Modifier.pointerInput(chave)` guarda a lambda de quando a chave mudou pela
última vez. O `aoSoltar` ficava preso na versão criada ANTES de existir arrasto,
e o alvo que ela enxergava era sempre vazio — nenhum evento mudava de dia, sem
uma linha de log. A correção foi calcular o alvo no instante em que se solta, e
não guardar o valor já calculado.

### Usar sem conta

Dá para abrir o app e escrever sem login, sem internet e sem servidor: na tela
de entrar há **"Usar só neste aparelho"**. Não é uma versão reduzida — é o app
inteiro, com post-its, agenda, planner, to-do, pomodoro, água e as dez paletas.
O que some é a metade que fala com o mundo.

Isso já era quase verdade por acidente de arquitetura: o Room sempre foi a fonte
da verdade das telas, e nenhuma delas jamais esperou a rede. Faltava desligar as
duas coisas que pressupõem conta:

- **A fila de envio.** A barreira fica num lugar só, em `Repositorio.enfileirar`
  — o único ponto por onde toda escrita passa, e portanto o único onde seria
  possível esquecer uma. Sem ela a fila cresceria para sempre com pedidos que
  nunca saem, e o app avisaria "N escritas pendentes" para quem nunca pediu
  para enviar nada.
- **O worker.** Sem conta não há o que puxar. Agendar mesmo assim acordaria o
  aparelho de hora em hora para uma tentativa que só pode falhar.

O selo "não enviado" também some. Ele quer dizer "isto ainda não chegou ao
servidor"; sem servidor a frase não significa nada, e viraria um aviso
permanente de pendência. O id negativo continua lá, mas passa a querer dizer só
"criado aqui" — a distinção vive em `LocalModoLocal`.

**E entrar numa conta depois não perde nada.** É o ponto que separa um modo
local de uma armadilha: quem escreveu por um mês e então cria uma conta veria o
próprio conteúdo ficar para trás — ou, pior, ser apagado pela primeira
sincronização, que só traz o que o servidor já conhece. `adotarDadosLocais`
enfileira uma criação por linha provisória e deixa o sincronizador fazer o
resto; ele já sabia trocar id negativo pelo do servidor e reescrever a fila,
porque é o mesmo caminho de qualquer post-it criado offline. Água e aparência
ficam de fora de propósito: copos são uma CONTAGEM do dia, e somar a local por
cima da que a conta já tem contaria o mesmo copo duas vezes.

Verificado ponta a ponta no tablet, em modo avião: dois post-its e uma tarefa
escritos sem conta, servidor sem receber uma requisição sequer, app reaberto
ainda em modo local, conta criada em seguida — e exatamente duas notas e uma
tarefa do outro lado.

### O post-it que não salvava

O relato foi "escrevo numa nota nova e nem 'Pronto' nem clicar fora salva". O
log do servidor local mostrou o defeito inteiro em três linhas:

```
POST  /api/notes      201   <- a nota sobe e ganha o id 66
PATCH /api/notes/-1   404   <- o texto sobe para o id provisório, que não existe lá
GET   /api/sync       200   <- e a descida traz a nota vazia por cima
```

A drenagem da fila percorria `pendencias().todas()` — uma **fotografia**. Quando
o POST voltava com o id de verdade, `trocarId` reescrevia os caminhos NO BANCO,
mas as pendências seguintes já estavam na mão, com o caminho velho. O texto
subia para `/api/notes/-1`, tomava 404, era descartado como "recusa do servidor"
e a nota voltava vazia na sincronização seguinte. Agora a drenagem pede **uma
por vez, sempre ao banco** (`pendencias().primeira()`).

Consertar isso descobriu mais quatro coisas na mesma vizinhança:

**O cartão morria no meio da frase.** A linha provisória não é editada quando o
servidor responde: ela é APAGADA e outra, com o id de verdade, entra no lugar.
Para o Compose isso é a morte do item — o cartão sai da composição e leva junto
o que estava só na memória dele, e o `LaunchedEffect` que gravaria é cancelado
antes de rodar. Agora o texto desce sozinho enquanto se escreve (Room quase de
imediato, fila só depois de uma pausa de verdade) e a tela acompanha a troca de
id por um aviso do sincronizador (`Remapeamentos`), então a edição não fecha
sozinha.

**O cursor voltava para o início.** Consequência do mesmo renascimento: um campo
de texto recém-criado começa com o cursor no zero. Escrever "MEIODAFRASE", a
nota trocar de id e continuar digitando dava "-FORAMEIODAFRASE". O campo passou
a guardar `TextFieldValue`, com o intervalo junto do texto.

**Um post-it fantasma.** `gravar` é um upsert, então escrever numa nota apagada
não dá erro: ela VOLTA. O cartão que estava aberto gravava por cima da linha que
o sincronizador acabara de trocar, e apareciam duas folhas empilhadas. Todas as
escritas de nota agora perguntam antes se a linha ainda existe — escrever numa
nota que não existe mais não é escrita nenhuma.

**Duas sincronizações ao mesmo tempo duplicavam tudo.** `sync-agora` e
`sync-periodico` são duas obras únicas no WorkManager, e "única" vale por nome:
nada impedia as duas de rodarem juntas, lerem a mesma fila e mandarem o mesmo
POST antes de qualquer uma apagar a pendência. Apareceu inteiro na primeira
adoção de dados locais — cada post-it e cada tarefa duplicados na conta nova. Um
`Mutex` de processo em `Sincronizador.rodar` resolve, porque todos os workers
rodam no processo do app.

### Todas as rotas, exercitadas

`scratchpad/varrer_rotas.py` percorre o `url_map` inteiro do Flask — as 48
regras, não só a API — e cobra três coisas de cada uma: que responde, que o
privado se recusa do jeito certo sem sessão (302 para `/entrar` nas páginas, 401
em JSON na API) e que, onde há ciclo, o ciclo fecha: o que foi criado aparece na
listagem e some depois do DELETE. No fim ele confere que nenhuma regra ficou de
fora, e é essa conta que impede a varredura de envelhecer — rota nova sem teste
faz falhar ali.

São 113 conferências, e junto com as suítes que já existiam dão 390 no total,
todas verdes.

### Ainda não existe

- **Notificações.** É a quarta queixa que iniciou tudo isto, e a única que
  continua de pé. Precisa de FCM no servidor e de canal de notificação no app.
- **Arrastar e redimensionar blocos** no planner: escala de tempo, encaixe em
  intervalos e sobreposição. Criar, editar e apagar funcionam pelo formulário.
- **Redimensionar post-it** pelo canto. Arrastar para mover funciona; o tamanho
  ainda é o padrão de 232×216.
- **Timer do Pomodoro com notificação ao terminar.** Ele já sobrevive ao app
  fechado (o que se guarda é o instante em que termina, não os segundos), mas
  ninguém é avisado se a tela estiver desligada.

## Ordem, e por quê

Não adianta começar pelo app: sem a Fase 1 completa ele não tem o que mostrar.
E não adianta fazer sincronização antes das marcas de tempo no banco.

Enquanto isso, o TWA continua instalado e funcionando — ninguém fica sem app
durante a travessia.
