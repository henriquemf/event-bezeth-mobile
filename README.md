# Event Beazeth — app Android

App nativo em Kotlin + Jetpack Compose para o Event Beazeth. Post-its, agenda,
planner semanal, to-do, pomodoro e lembrete de água — as mesmas coisas do site,
no mesmo desenho, mas guardadas no próprio aparelho e disponíveis sem internet.

Fala com o servidor do repositório irmão `../event-beazeth/` (Flask + Postgres),
pela API declarada em `app/api_contract.py`. Também dá para usar sem conta
nenhuma, e aí não fala com ninguém.

---

## O aparelho

O alvo é **um só: um Galaxy Tab A9+ deitado** — 1920×1200 a 240 dpi, ou seja uma
janela de **1280×800 dp**. Não é "um app de celular que também roda em tablet": é
essa tela, e é por isso que a barra lateral do site cabe inteira ao lado do
conteúdo.

O layout ainda se adapta abaixo disso — sob 720 dp de largura a lateral dá lugar
a uma barra inferior, como o site faz na sua `@media (max-width: 860px)` — mas
quem manda nas decisões de tamanho é o tablet.

O emulador de trabalho é esse aparelho (`Galaxy_Tab_A9_Plus`, com
`hw.lcd.width=1920`, `hw.lcd.height=1200`, `hw.lcd.density=240` e
`hw.initialOrientation=landscape`).

---

## O que o app faz

Sete telas do site, na mesma ordem do menu lateral, mais uma que o site não
tem: a de perfil.

| Tela | O que dá para fazer |
| --- | --- |
| **Post-its** | Quadro de verdade, não lista: cada papel tem posição, inclinação e cor. Quatro quadros (Hoje, Amanhã, Semana, Ideias), arrastar para mover, segurar para trocar a cor, e um botão Organizar que enfileira tudo. |
| **Agenda** | Grade do mês no tamanho do site, com os eventos escritos dentro do dia (hora, título e o ponto da cor da tag) e os dias dos meses vizinhos em tom apagado. Ao lado, "Próximos eventos", que olha sempre de hoje para a frente. Tocar num dia acende a célula e já abre o formulário naquela data; tocar num evento abre ele; segurar um evento e largar em outro dia muda a data e preserva a hora. |
| **Weekly Planner** | A semana inteira em colunas, com régua de horas, blocos em escala de tempo, sobreposição repartindo a coluna e a linha do "agora". Segurar e arrastar move o bloco, encaixando em 15 minutos — só o arraste encaixa; horário escolhido à mão vale como escolhido, no relógio ou digitado. Marcar vários dias cria um bloco em cada. |
| **To-do** | Uma semana por vez, com o contador de feitas. Criar, marcar, editar e apagar. |
| **Pomodoro** | Ampulheta animada e contagem que sobrevive ao app fechado — o que se guarda é o instante em que termina, não os segundos. Ao chegar ao fim, avisa na barra de notificação, mesmo com o app fechado e a tela apagada. |
| **Beber água** | O copo d'água do site, enchendo até a fração do dia, o quanto em copos e em ml, e a fileira do dia com um copinho por copo da meta. Avisa no intervalo e na janela escolhidos no site — e para de avisar quando a meta do dia é batida. |
| **Aparência** | Dez paletas e dez fontes, e nada mais. Conta e sincronização saíram daqui para o perfil; o modo escuro é a lua da barra de cima, que está em todas as telas. |
| **Perfil** | Foto (escolhida pela galeria, cortada no quadrado e guardada no aparelho), nome de exibição, e-mail e senha. Os números do que já se acumulou — copos, tarefas riscadas, pomodoros, post-its, eventos, blocos — com as frases que eles permitem ("você passou 3 h 40 min focando"). No fim, o estado dos avisos, o da sincronização e a saída da conta. |

A **Agenda** também avisa: na hora do evento, e — nas tags de regra "curso" — 15
e 7 dias antes, seguindo a mesma regra que o site usa para mandar e-mail. Tocar
num aviso abre o app já na tela do assunto.

Fora das telas, na casca: a lateral com os widgets vivos de pomodoro e água (os
mesmos do site), o botão de três barrinhas que a esconde, a barra de cima que
recolhe ao rolar, e tela cheia — sem barra de notificação nem de navegação até
que se puxe da borda.

No alto da lateral fica o retrato: foto e nome, e nada mais. A caixa inteira
leva ao perfil. **Não há marca ali** — um app já foi aberto pelo ícone com o
nome embaixo, e repetir "Event Notifier" dentro custava o lugar mais nobre da
tela para informar o que ninguém perguntou. **Nem interruptor de modo escuro no
rodapé dela**, que existia também na barra de cima e também na tela de
Aparência: três controles para a mesma chave.

---

## Como funciona por dentro

### A regra que define tudo: a tela nunca espera a rede

O app anterior era o site embrulhado num `.apk`, e cada troca de tela era uma ida
ao Oregon. Um app nativo que buscasse do servidor a cada tela teria o mesmo
problema — trocaria o Chrome por Compose e continuaria esperando. Então:

> A tela lê do **Room**, o banco do aparelho. A rede sincroniza por baixo,
> quando dá.

Toda escrita entra no Room primeiro e aparece no mesmo quadro; a subida acontece
depois, em segundos com rede e em horas sem ela. Marcar uma tarefa como feita
não tem latência nenhuma, e sem sinal o app inteiro continua funcionando.

O preço é conflito: a mesma linha editada no site e no aparelho offline. A regra
é a mais simples que resolve — **quem escreveu por último vence**, comparando
`updated_at`.

### A fila de escrita e os ids provisórios

O que ainda não subiu fica numa fila (`pendencias`). Uma linha criada offline
nasce com **id negativo** — "o servidor ainda não sabe disto" — e recebe o id de
verdade quando a criação sobe. Nesse instante a linha provisória é apagada e
outra entra no lugar, e a fila é reescrita para as escritas seguintes apontarem
para o id novo.

Essa troca de identidade é a parte delicada do app, e as regras que saíram dela
estão em `CONSTITUTION.md` (seção 10b), no repositório do site. As três que mais
importam:

- a fila é drenada **uma pendência por vez, lida do banco** — percorrer uma lista
  em memória perde o efeito da reescrita de ids;
- gravar por id é *upsert*, então escrever numa linha apagada a **ressuscita**;
  toda escrita pergunta antes se a linha ainda existe;
- "obra única" do WorkManager é única **por nome**, então duas sincronizações
  podem rodar juntas; há uma tranca de processo para que não rodem.

### A sincronização

Duas metades, nesta ordem — e a ordem não é arbitrária:

1. **sobe** o que está na fila, na ordem em que aconteceu;
2. **baixa** o que mudou no servidor desde a última vez (`GET /api/sync?since=`).

Ao contrário, uma tarefa criada offline seria sobrescrita pela resposta do
servidor — que ainda não sabe dela — e sumiria da tela antes de chegar a subir.

O carimbo do `since` vem do **relógio do banco**, devolvido pelo próprio
servidor, nunca do relógio do aparelho: qualquer diferença entre os dois viraria
linha perdida na consulta seguinte, sem erro nenhum aparecer.

Quem roda isso é o WorkManager, e não uma corrotina do ViewModel: o pedido
sobrevive ao app ser fechado e à falta de rede. Dispara depois de toda escrita,
ao abrir o app, e de hora em hora como rede de segurança.

### Sem conta

Na tela de entrar há **"Usar só neste aparelho"**. É o app inteiro — as sete
telas, as dez paletas —, sem login, sem servidor e sem rede. O que sai é a
metade que fala com o mundo: a fila de envio não recebe nada e o worker nunca é
agendado.

E entrar numa conta depois **não perde nada**: post-its, tarefas, blocos e
eventos escritos sem conta são enfileirados como criações e sobem. Copos de água
e aparência ficam de fora de propósito — copos são uma contagem do dia, e somar a
local por cima da que a conta já tem contaria o mesmo copo duas vezes.

### Os avisos são do aparelho, não do servidor

O site avisa por *web push*: o servidor varre o banco de minuto em minuto e
empurra a mensagem. O caminho óbvio seria repetir isso aqui com FCM — projeto no
Firebase, chave de servidor, uma dependência a mais e uma ida à rede **no
instante do aviso**.

Não foi o escolhido, porque o aparelho **já sabe tudo o que precisa para avisar
sozinho**. A configuração de água (ligado, intervalo, janela) e a agenda inteira
já estão no Room, trazidas pela sincronização, e o pomodoro nunca saiu daqui. Um
aviso local acerta com o celular em modo avião; um push dependeria de rede
justamente no minuto em que ela pode faltar.

**Um alarme por assunto, sempre o próximo.** Não há um alarme por evento: há um
alarme de evento, marcado para o lembrete que vencer primeiro, e quando ele toca
o próprio despertador calcula o seguinte e remarca. Cem eventos continuam sendo
um alarme — e um evento apagado no site some do celular na sincronização
seguinte, sem sobrar alarme órfão.

**Nada é guardado sobre "o que já foi agendado".** Toda rodada lê o banco,
recalcula os gatilhos e remarca, o que torna "recalcular" seguro de repetir — e
ele é repetido de propósito, de cinco lugares: ao abrir o app, ao terminar cada
sincronização, ao criar ou apagar um evento, ao religar o aparelho e a cada
alarme que toca. O que impede o mesmo lembrete de ser entregue duas vezes é uma
marca d'água (`avisoDeEventoAte`), e não uma tabela de entregues.

**As regras dos eventos são as do site**, lidas de `_build_reminders` em
`scheduler_service.py`: a tag diz se o evento avisa só na hora (`dia`) ou também
15 e 7 dias antes (`curso`). Inventar um cronograma aqui seria o mesmo evento
avisando em horas diferentes no e-mail e no celular.

**O que o Android apaga sem avisar.** Religar o aparelho limpa a tabela de
alarmes; instalar o `.apk` novo por cima faz o mesmo; parar o app à força
também. Os dois primeiros chegam como *broadcast* e são atendidos por `AoLigar`;
o terceiro se resolve no `onResume` da activity, que recalcula tudo a cada volta
para o app.

**Atraso tem limite.** Um aviso pode vencer com o aparelho desligado. Entregar
todos os vencidos de uma vez seria uma avalanche de lembretes do que já passou,
então cada tipo diz quanto atraso ainda vale: uma hora para "agora" (um aviso de
compromisso da manhã que chega à tarde não lembra nada) e doze horas para os
antecipados de curso, onde "faltam 15 dias" continua verdade meio dia depois.

**Três canais, e não um.** Do Android 8 em diante quem decide som, vibração e se
o aviso toma a tela é a pessoa, canal por canal. Um canal só transformaria "o
lembrete de água está me atrapalhando" em "desliguei os avisos do app" — e junto
iria o fim do pomodoro.

**O som é um arquivo do app** (`res/raw/aviso_suave.wav`): um sino curto, de
ataque macio, gerado com pico em 30% da escala. O toque padrão do sistema muda
de aparelho para aparelho e é desenhado para *chamar*; num app que avisa várias
vezes ao dia, isso cansa — e cansar é o que faz alguém desligar tudo. A água não
vibra, e os outros dois dão um pulso único de 120 ms em vez do zumbido duplo
padrão.

Uma armadilha que vale saber: **importância, som e vibração são copiados uma vez
só, quando o canal nasce.** Depois disso o Android guarda a escolha da pessoa e
ignora o código. Trocar o toque padrão de verdade exigiria canais com ids novos.

**A água cala quando a meta foi batida** — e aqui o app se afasta do site de
propósito. O servidor avisa a cada intervalo até a janela fechar, tenha a pessoa
bebido oito copos ou nenhum. Num navegador aberto isso passa; num celular no
bolso, seis avisos depois da meta cumprida são exatamente o que faz alguém
desligar os avisos. A comparação confere a *data* da linha antes de calar: um
aparelho sem sincronizar desde ontem, com a meta de ontem cumprida, ficaria mudo
o dia inteiro de hoje. Na dúvida, avisa.

---

## A aparência vem do site

Não é "parecido": é porte token a token, com o site aberto do lado.

| No site | No app |
| --- | --- |
| `:root` do `base.css` | `ui/theme/Tokens.kt`, nome por nome |
| `themes.css`, 10 paletas | `ui/theme/Paletas.kt`, **geradas** do CSS, não digitadas |
| Quicksand, Baloo 2 e as outras | as mesmas dez, dentro do `.apk` |
| `body` + `.bg-candy` | `FundoDoce`, os quatro degradês desenhados |
| `.auth-card`, `.btn-primary`, `.event-form` | `ui/componentes/Doces.kt` |
| `partials/sidebar.html` | `ui/componentes/BarraLateral.kt` |

As paletas são geradas porque são 17 cores × 10 temas: um valor digitado errado
não quebraria nada — só deixaria um tema levemente diferente do site, para
sempre.

**Onde o app diverge, é de propósito e está escrito.** Os cantos são mais retos
que os do site (ver `ui/theme/Tokens.kt`), o degradê do botão primário é
calculado a partir do destaque em vez de terminar sempre em rosa, e o zoom do
planner é em dois botões em vez de barra deslizante — um controle de 2 px de
passo pede precisão que o dedo não tem.

O `<footer class="site-love-footer">` (o "EU TE AMO MOMO") **não existe aqui**.
No site ele é a última coisa de uma página que rola inteira, e só aparece quando
se chega ao fim. No app não há esse fim: teria que ficar fixo na base, à vista o
tempo todo, roubando 50 dp de altura de toda tela. Ficou no site, onde nasceu.

---

## Construir e rodar

Pelo Android Studio: abrir a pasta e apertar Run.

Pela linha de comando, com o **JDK 21**:

```bash
export JAVA_HOME="C:/Users/Windows/.jdks/jbr-21.0.11"
./gradlew assembleDebug
```

O `.apk` sai em `app/build/outputs/apk/debug/app-debug.apk`.

**Para onde o app aponta.** O build de debug aponta para **produção** de
propósito: é ele que se instala num aparelho para experimentar, e `10.0.2.2` só
existe dentro do emulador. Para desenvolver contra um servidor local:

```bash
./gradlew assembleDebug -PapiBase=http://10.0.2.2:5055      # emulador
./gradlew assembleDebug -PapiBase=http://192.168.x.x:5055   # aparelho na rede
```

**Tamanho.** O debug sai perto de 13 MB porque carrega tudo. A release liga R8 e
`shrinkResources` e fica em torno de 3 MB — a maior parte do que sobra são as dez
fontes, que existem justamente para o app não parecer outro produto.

---

## Gerar o .apk para instalar no tablet

```powershell
.\gerar-apk.bat
```

Só isso. Na primeira vez ele pede a senha da chave, confere que ela abre a chave
**antes** de compilar, e guarda em `keystore.properties`; nas seguintes não
pergunta mais. No fim abre a pasta com o arquivo já selecionado e deixa o caminho
na área de transferência.

Sai `dist\EventBeazeth-<versão>.apk`, assinado e pronto para mandar. Para subir
a versão junto, `.\gerar-apk.bat -Versao 1.1.0`; para instalar no aparelho
conectado antes de mandar, acrescente `-Instalar`.

O `.bat` é uma linha só chamando o `empacotar.ps1`, e existe por causa do
`-ExecutionPolicy Bypass`: o Windows recusa rodar `.ps1` por clique duplo, com
uma mensagem que parece defeito do script. A permissão vale só para aquela
chamada; nada muda na máquina.

O script existe porque quase toda etapa de "gerar o `.apk`" falha calada — o
build termina bem e o problema só aparece no aparelho de quem recebeu:

| Passo | O que falha sem ele |
| --- | --- |
| achar o JDK 21 | o Gradle para com um erro que não diz qual Java ele queria |
| exigir a chave, e testar a senha antes | o `.apk` sai **sem assinatura**, e não instala em lugar nenhum |
| conferir que o `apiBase` é `https` | o `.apk` instala, abre, e só o login falha — sem dizer por quê |
| conferir a assinatura do arquivo **pronto** | assinado com a chave de depuração, ele instala hoje e nunca aceita atualização |
| nomear com a versão | três `app-release.apk` na pasta de downloads e ninguém sabe qual é qual |

### A chave

A assinatura é o que permite **atualizar por cima**. O Android só aceita uma
versão nova se ela vier assinada com a mesma chave da instalada; assinatura
diferente é, para ele, outro aplicativo. Perder a chave não tem conserto: quem
já tem o app teria que desinstalar, e o que estiver só no aparelho (post-it
escrito offline) some junto.

Por isso ela não está aqui. `keystore.properties` diz onde ela está e qual a
senha, é escrito pelo próprio script na primeira execução, e é ignorado pelo
git; ao lado dele há um `keystore.properties.exemplo` versionado, que explica o
que preencher sem guardar o segredo. É a mesma chave
que assina o app antigo (o TWA), na pasta vizinha — uma chave pode assinar dois
apps, e é um arquivo insubstituível para guardar em vez de dois.

### O ícone

Adaptativo, em `mipmap-anydpi-v26/` — fundo, frente e a camada monocromática
que o Android 13 tinge com as cores do papel de parede. A arte é a do site
(`static/icon-512.png`), redesenhada em vetor: a bolinha de degradê e o sorriso,
encolhidos a 80% para caber na zona segura de 66 dp, que é o que o launcher
garante não cortar no recorte redondo.

Antes havia só o PNG legado, e o Android 8 em diante trata ícone legado como
peça de museu: recorta num círculo, encolhe e põe fundo branco em volta. No meio
da gaveta o app aparecia pequeno e desbotado dentro de uma bolha branca.

---

## Mapa do projeto

```
app/src/main/java/com/beazeth/notifier/
├── MainActivity.kt          tela cheia, tema, e qual das quatro situações de sessão
├── data/
│   ├── Api.kt               as chamadas HTTP
│   ├── Modelos.kt           o formato das respostas do servidor, e a conversão para o Room
│   ├── Repositorio.kt       o que as telas usam: lê do Room, escreve no Room, enfileira
│   ├── Sincronizador.kt     sobe a fila, baixa o que mudou
│   ├── Remapeamentos.kt     avisa a interface quando um id provisório vira definitivo
│   ├── TokenStore.kt        token, nome, e-mail, carimbo de sync e a marca do modo local
│   ├── Perfil.kt            a foto (corte, giro e disco) e o nome de quem usa sem conta
│   ├── Estatisticas.kt      as contas da tela de perfil, todas saindo do Room
│   ├── Preferencias.kt      tema, fonte, modo escuro, estado do pomodoro e as marcas d'água dos avisos
│   └── local/               Room: entidades, DAOs e o banco
├── avisos/
│   ├── Avisos.kt            os três canais, o som e o ato de postar
│   ├── Alarmes.kt           o AlarmManager: um alarme por assunto, sempre o próximo
│   ├── Lembretes.kt         quem decide o que avisar e quando tocar de novo
│   ├── Agua.kt              a grade de horários da janela de hidratação
│   ├── Eventos.kt           os gatilhos da agenda, com as regras do site
│   └── Receptores.kt        o alarme tocou / o aparelho religou
├── sync/SyncWorker.kt       quem roda a sincronização, e quando
└── ui/
    ├── CascaApp.kt          lateral ou barra inferior, e a barra de cima
    ├── SessaoViewModel.kt   entrar, criar conta, usar sem conta, sair
    ├── componentes/         o que se repete: cartão, campo, botão, lateral, retrato
    ├── telas/               uma pasta por tela grande, um arquivo por assunto
    └── theme/               tokens, paletas, fontes e tipografia
```

São 63 arquivos Kotlin, ~14,5 mil linhas. **Nenhum passa de 700 linhas** — quando
um chega perto, ele se divide por assunto (foi assim que `postits/`, `planner/` e
`calendario/` viraram pastas, e por isso `avisos/` nasceu com seis arquivos em
vez de um).

---

## Verificação

O app é conferido contra um **servidor local**, nunca contra produção: um Flask
com o Postgres de desenvolvimento, semeado com dados, e o app compilado com
`-PapiBase=http://10.0.2.2:5055`.

| Ferramenta | O que garante |
| --- | --- |
| `auditar_kotlin.py` | código morto em sete categorias: função sem chamada, import órfão, recurso sem uso |
| `test_contract.py` | o servidor não divergiu de `api_contract.py` — rota nova que o app não conhece faz falhar |
| `test_api.py`, `test_sync_api.py` | formato das respostas, isolamento entre contas, nada perdido entre duas sincronizações |
| `varrer_rotas.py` | as 48 regras do `url_map` do Flask, exercitadas: responde, recusa o anônimo do jeito certo, e o ciclo criar→ler→mudar→apagar fecha |

E o que nenhuma ferramenta pega: o ciclo inteiro no emulador, com a rede
desligada no meio. Foi assim que apareceu o defeito que só existe fora do
laboratório — o emulador em GMT e o servidor em GMT-3, com a tela de água pedindo
o dia do *aparelho* enquanto o servidor conta pelo dia *dele*.

### Conferir os avisos

Aviso não se testa lendo código: ou ele chega, ou não chega. O que o emulador
mostra, e nenhuma outra coisa mostra:

- `adb shell dumpsys alarm` — se o alarme foi mesmo marcado, para que instante, e
  se `exactAllowReason` diz que a permissão de hora exata valeu. É onde se vê que
  remarcar SUBSTITUI o alarme em vez de empilhar outro.
- `adb shell dumpsys notification` — o que foi postado: canal, texto, cor, e o
  `mSound` do canal (que precisa apontar para `android.resource://…`, e não para
  o toque do sistema).
- **Religar o emulador sem abrir o app** e conferir se os alarmes voltaram. É a
  única prova de que `BOOT_COMPLETED` está de pé; o app abrindo mascara o defeito,
  porque o `onResume` remarcaria tudo de qualquer jeito.
- **Reinstalar por cima** (`adb install -r`), que dispara `MY_PACKAGE_REPLACED` —
  e, feito com `-PminifyDebug`, é o teste de R8 dos avisos: um receptor perdido no
  encolhimento compila, instala e só falha calado.

Foi um desses ciclos que pegou "Os **1** minutos de foco acabaram" — plural que
nenhuma leitura de código tinha visto, porque um minuto é justamente o menor
tempo que o slider oferece.

---

## O que ainda não existe

- **Redimensionar bloco do planner** arrastando a borda. Mover funciona; mudar a
  duração é pelo formulário — uma alça útil pede uns 24 dp, e um bloco de 15
  minutos tem 13 dp de altura no zoom padrão.
- **Redimensionar post-it** pelo canto. Arrastar para mover funciona; o tamanho é
  o padrão de 232×216.
- **Configurar o lembrete de água** (meta do dia, tamanho do copo, intervalo, janela do
  dia). O app lê a configuração, conta os copos com ela **e avisa por ela**; mudar
  continua sendo no site, porque a API do app só expõe leitura. É a lacuna mais
  incômoda que sobrou: quem usa só o celular não tem como escolher o intervalo, e
  quem usa sem conta nunca recebeu uma configuração — e por isso não recebe o
  lembrete de água. Pomodoro e agenda avisam nos dois casos.
- **Adiar um aviso** pelo próprio aviso ("mais 10 minutos"). Hoje ele só abre a
  tela do assunto.
- **Foto de perfil na conta.** Ela fica no aparelho: reinstalar o app pede a foto
  de novo, e o site não a mostra. Guardá-la na conta pediria uma coluna de bytes
  num Postgres cobrado por byte, ou um serviço de arquivos novo — o disco do
  Render é efêmero. Nome e e-mail, esses sim, moram na conta.
- **Estatística de foco anterior a esta versão.** O histórico de pomodoros nasceu
  com a tela de perfil e conta dali para a frente; nunca existiu antes, nem aqui
  nem no site. Copos, tarefas e o resto vêm do servidor e já nascem completos.
- **Criar tags** pelo app. Dá para usar as que existem; criar e apagar é no site,
  porque a API de tags só expõe leitura.

---

## Documentos irmãos

- `../event-beazeth/CONSTITUTION.md` — as regras que valem para os dois lados.
  A seção **10b** é sobre este app: a fila, os ids provisórios, o que morre sem
  avisar no Compose, e por que o modo local precisa de caminho de volta.
- `../event-beazeth/app/api_contract.py` — o contrato da API, e o teste que
  falha quando as duas pontas divergem.
