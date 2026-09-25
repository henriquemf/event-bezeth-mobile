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

Oito telas do site, na mesma ordem do menu lateral, mais uma que o site não
tem: a de perfil.

| Tela | O que dá para fazer |
| --- | --- |
| **Post-its** | Quadro de verdade, não lista: cada papel tem posição, inclinação e cor. Quatro quadros (Hoje, Amanhã, Semana, Ideias), arrastar para mover, segurar para trocar a cor, e um botão Organizar que enfileira tudo. |
| **Agenda** | Grade do mês no tamanho do site, com os eventos escritos dentro do dia (hora, título e o ponto da cor da tag) e os dias dos meses vizinhos em tom apagado. Ao lado, "Próximos eventos", que olha sempre de hoje para a frente. Tocar num dia acende a célula e já abre o formulário naquela data; tocar num evento abre ele; segurar um evento e largar em outro dia muda a data e preserva a hora. |
| **Planner** | A semana inteira em colunas, com régua de horas, blocos em escala de tempo, sobreposição repartindo a coluna e a linha do "agora". Segurar e arrastar move o bloco, encaixando em 15 minutos — só o arraste encaixa; horário escolhido à mão vale como escolhido, no relógio ou digitado. Marcar vários dias cria um bloco em cada. |
| **To-do** | Uma semana por vez, com o contador de feitas. Criar, marcar, editar e apagar. |
| **Pomodoro** | Ampulheta animada e contagem que sobrevive ao app fechado — o que se guarda é o instante em que termina, não os segundos. Ao chegar ao fim do foco: confete caindo na tela, uma salva de palmas, e o descanso começa sozinho (5 min até 30 de foco, 10 até 59, 15 daí para cima) — a menos que a chave **Descanso automático**, embaixo dos botões, esteja desligada: aí o foco termina parado em "Tempo!", e vale também para os subs. Com o app fechado, quem avisa é a barra de notificação. Abaixo, **até dez outros pomodoros**, cada um com nome, tempo e contagem próprios, em cartão aberto ou minimizado; com algum deles correndo, o item Pomodoro do menu ganha um número. |
| **Beber água** | O copo d'água do site, enchendo até a fração do dia, o quanto em copos e em ml, a fileira do dia com um copinho por copo da meta, e a hora do próximo lembrete. O dia é o do aparelho e zera à meia-noite. **O lembrete liga-se e ajusta-se aqui** — interruptor, intervalo, janela do dia, meta e tamanho do copo — e o que se muda sobe para o site na próxima sincronização. Avisa um intervalo depois do último copo (ou do último lembrete), dentro da janela escolhida — e para de avisar quando a meta do dia é batida. Embaixo, o histórico como o gráfico de contribuições do GitHub: um quadradinho por dia, mais escuro quanto mais perto da meta, com copos e litros ao toque. |
| **Diário** | O ano inteiro em quadradinhos, um por dia — doze colunas de mês, trinta e uma linhas. Tocar num dia abre uma folha por baixo com os seis humores e o espaço de escrever; a cor pinta o quadradinho e um ponto marca "tem texto aqui". O que se digita desce para o banco a cada pausa, então fechar a folha com um arrasto não perde nada. |
| **Aparência** | Dez paletas e dez fontes, e nada mais. Conta e sincronização saíram daqui para o perfil; o modo escuro é a lua da barra de cima, que está em todas as telas. |
| **Perfil** | Foto (escolhida pela galeria, cortada no quadrado e guardada no aparelho), nome de exibição, e-mail e senha. Os números do que já se acumulou — copos, tarefas riscadas, pomodoros, post-its, eventos, blocos — com as frases que eles permitem ("você passou 3 h 40 min focando"). Os avisos: quais existem, com que som (com botão de ouvir), e o que a tela bloqueada mostra. No fim, a sincronização e a saída da conta. |

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

Na tela de entrar há **"Usar só neste aparelho"**. É o app inteiro — as oito
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

**Quatro canais, e não um.** Do Android 8 em diante quem decide som, vibração e se
o aviso toma a tela é a pessoa, canal por canal. Um canal só transformaria "o
lembrete de água está me atrapalhando" em "desliguei os avisos do app" — e junto
iria o fim do pomodoro. E o som também é do canal: é por isso que o **fim do
descanso** tem canal próprio, separado do **fim do foco** — sem isso os dois
teriam de tocar igual, e são recados opostos ("para" e "pode voltar"). O liga e
desliga dos dois continua sendo uma chave só, a do Pomodoro (`Canal.assunto`).

**O som é um arquivo do app** (`res/raw/*.wav`). O toque padrão do sistema muda
de aparelho para aparelho e é desenhado para *chamar*; num app que avisa várias
vezes ao dia, isso cansa — e cansar é o que faz alguém desligar tudo. A água não
vibra, e os outros dão um pulso único de 120 ms em vez do zumbido duplo padrão.

Até a 1.7.1 os toques eram senos gerados por conta, e as palmas, ruído sintético.
A 1.8.0 trocou tudo por gravações do Freesound, e ainda chiava: eram prévias com
compressão, e duas tinham ruído de sala audível. Da 1.9.0 em diante só entra
fonte **sem perda** ou feita digitalmente:

| Recurso | No app | Original |
|---|---|---|
| `aviso_marimba` | Marimba — padrão do fim do foco | Marimba, baqueta de lã (C5, E5, G5), [Univ. de Iowa MIS](https://theremin.music.uiowa.edu/MIS.html) |
| `aviso_vibrafone` | Vibrafone — padrão do fim do descanso | Vibrafone (E5, C5), Univ. de Iowa MIS |
| `aviso_glockenspiel` | Sininho — padrão da agenda | Glockenspiel, baqueta de plástico (G6, C7), Univ. de Iowa MIS |
| `aviso_gotinha` | Gotinha — padrão da água | `drop_002` e `drop_003` do [Interface Sounds](https://kenney.nl/assets/interface-sounds), Kenney (CC0) |
| `festa_marimba` | Marimba em festa — padrão da festa | Marimba (C5, E5, G5, C6), Univ. de Iowa MIS |
| `festa_palmas` | Palmas, opção da festa | [applause-2.wav](https://commons.wikimedia.org/wiki/File:277021_sandermotions_applause-2.wav), Sandermotions (CC0) |

As gravações da Iowa são de estúdio, em AIFF, e "podem ser usadas em qualquer
projeto, sem restrições" (dito na própria página). As da Kenney são feitas
digitalmente: sem microfone, não há ruído de sala. O tratamento é o mesmo do
site (ver o README de lá): o chiado de cada gravação medido no silêncio antes
da nota e subtraído do espectro, tudo 70 dB abaixo do pico virando zero digital
exato, e WAV 16 bits a 48 kHz — a taxa nativa do mixer do Android, para o
aparelho não reamostrar. Medido, o chiado dos sons musicais ficou entre −97 e
−102 dBFS. As palmas não são mais o padrão: palmas *são* rajadas de ruído.

**Chave nova a cada troca de arquivo.** O canal guarda o endereço do som pelo id
*numérico* do recurso, e o build renumera os recursos quando a lista de arquivos
muda. Conferido no emulador, atualizando a 1.7.1 para a 1.8.0: o número que era
da Gotinha passou a ser de outro arquivo — com a chave velha, o canal velho
tocaria o som errado por acaso. Por isso nenhuma chave de toque repete uma de
versão anterior, e `Som.porChave` leva cada chave antiga ao toque novo mais
parecido. Quem tinha escolhido o toque único de antes continua com ele em todo
aviso que ainda não ganhou escolha própria (`resolverSom`).

**Aviso velho na barra cala o próximo.** Do Android 16 em diante o sistema junta
os avisos de um mesmo app num pacote ("Event Beazeth · 3"), e o que entra no
pacote nasce marcado como silencioso — basta um segundo aviso pendurado na barra
para o par já ser empacotado. Como nada tirava os avisos de lá, bastava passar o
dia com o app instalado para *tudo* virar silêncio, e o sintoma era "nenhuma
notificação faz barulho" sem que houvesse nada errado com o som nem com o canal.

A correção é não deixar aviso velho parado: cada um sai sozinho depois de meia
hora (`setTimeoutAfter`) e sai na hora quando a pessoa resolve o assunto dentro
do app — bebeu o copo, começou outro pomodoro. É o certo por si só (um lembrete é
um instante, não um item de lista) e devolve o som de quebra.

**Importância, som e vibração são copiados uma vez só, quando o canal nasce.**
Depois disso o Android guarda a escolha da pessoa e ignora o código — e nem
apagar e recriar resolve, porque ele lembra dos ajustes de um canal apagado e os
restaura quando um canal com o *mesmo id* reaparece.

É por isso que trocar o toque pelo app **troca o canal de lugar**: o id carrega a
escolha (`pomodoro_marimba` é outro canal que `pomodoro_gotinha`), o novo nasce e o
velho é apagado. Quem já tiver ajustado aquele canal à mão nos ajustes do sistema
perde o ajuste — aceitável, porque foi a própria pessoa que acabou de pedir a
troca.

### O que dá para ajustar dentro do app

Na tela de perfil, no cartão **Avisos**:

- **Quais avisos existem** — uma chave por assunto. Parece a mesma chave que o
  Android já tem, e não é: a do sistema decide se o aviso **aparece** (o app
  continua acordando o aparelho e postando um aviso que ninguém vê); a daqui
  decide se o lembrete **existe** — desligada, o alarme é desmarcado e o aparelho
  para de ser acordado.
- **O som de cada aviso**, um por um: beber água, fim do foco, fim do
  descanso, agenda e a festa do fim do foco (que toca com o app aberto). Cada
  linha fechada mostra o som atual; tocar nela abre as opções — quatro toques
  do app, o toque do aparelho e "sem som" —, com um botão **Ouvir** em cada.
  Ouvir antes de escolher não é enfeite: "Gotinha" não diz se o som é discreto
  ou irritante, e sem a prévia o único jeito de descobrir seria escolher e
  esperar o próximo lembrete — que, no caso da agenda, pode levar dias. O
  "Ouvir" toca pelo mesmo caminho e no mesmo volume do aviso de verdade.
- **Se o nome do evento aparece na tela bloqueada.**

### A tela bloqueada

Água e pomodoro são `VISIBILITY_PUBLIC`: "hora de beber água" e "acabou o tempo"
não revelam nada de ninguém, e são justamente os que precisam ser lidos de
relance, sem digitar o PIN.

A agenda é escolha, porque o nome de um evento pode ser assunto de quem o marcou
— "Consulta" na tela de bloqueio é visível para quem passar perto da mesa. O
padrão é mostrar; a chave está no cartão.

E uma descoberta que só apareceu no emulador: **do Android 15 em diante o sistema
ignora o `publicVersion`** — aquela versão reduzida que o app oferece para ser
mostrada no lugar da real. Ele troca a linha inteira por "conteúdo oculto" e
pronto. Conferido com a versão pública chegando corretamente ao `dumpsys` e não
aparecendo na tela. Ela continua sendo enviada porque vale nas versões
anteriores, mas o efeito prático é binário: ou o aviso é legível, ou não há aviso
legível nenhum. Daí a chave existir.

**A água conta a partir do último copo, como o servidor.** O site guarda
`last_sent_at` e beber empurra esse carimbo: quem acabou de beber só é cobrada
um intervalo inteiro depois. O app faz a mesma conta sobre um marco do aparelho
(`aguaMarcoEm`): o último copo ou o último lembrete, o que for mais recente. A
primeira versão usava uma grade presa à abertura da janela (08:00, 09:00...), e
estava errada de um jeito que só aparece usando — quem bebia às 09:55 recebia
"hora de beber água" às 10:00, e parecia que o copo disparava o lembrete. O
lembrete é para quem *não* bebeu.

**O lembrete vencido sai na primeira oportunidade, uma vez.** "Vencido" é a conta
devolver um instante que não está no futuro, e isso é julgado a cada recálculo —
o alarme tocando, o app abrindo, a sincronização de hora em hora, o aparelho
religando. Se o alarme das 10:00 não tocou (aparelho desligado, ou um fabricante
que segura alarmes de app parado), o lembrete sai na próxima dessas
oportunidades; e sai uma vez só, porque a entrega move o marco. A tela de água
mostra para quando o próximo está marcado, para ninguém ter de esperar por ele
para saber se existe.

**A água cala quando a meta foi batida** — e aqui o app se afasta do site de
propósito. O servidor avisa a cada intervalo até a janela fechar, tenha a pessoa
bebido oito copos ou nenhum. Num navegador aberto isso passa; num celular no
bolso, seis avisos depois da meta cumprida são exatamente o que faz alguém
desligar os avisos. Com a meta batida o alarme vai direto para a próxima
abertura da janela, em vez de acordar o aparelho a cada intervalo para descobrir
de novo que não há o que lembrar.

**O dia da água é o do aparelho, e é o app que diz ao servidor em que dia está.**
O servidor do deploy roda em UTC, e o dia dele vira às 21:00 de quem está no
Brasil: um copo das 22:00 caía no dia seguinte, e o contador não zerava à
meia-noite porque a tela mostrava "o dia mais recente que o servidor mandou".
Agora a tela olha a linha de *hoje* pela data local — que ainda não existe quando
o dia começa, e por isso mostra zero —, e `POST /api/hydration/drink` leva `day`.
O servidor aceita ontem, hoje ou amanhã em relação a ele e ignora o resto.

**Fabricantes seguram alarmes.** Samsung, Xiaomi e outros põem por cima do Doze
um gerenciador próprio que, depois de dias sem o app ser aberto, para de
entregar os alarmes dele — e o sintoma é "os lembretes só chegam quando eu mexo
no app". O cartão **Avisos** do perfil percebe isso e oferece "Liberar em segundo
plano", que abre a pergunta do sistema para tirar o app da otimização de
bateria. É a única coisa que o app pode pedir a esse respeito.

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
│   ├── SubPomodoro.kt       os outros dez pomodoros: o registro, o relógio e o disco
│   └── local/               Room: entidades, DAOs e o banco
├── avisos/
│   ├── Avisos.kt            os quatro canais, a visibilidade e o ato de postar
│   ├── Som.kt               os toques, o som de cada canal, e por que trocar de som troca o canal de lugar
│   ├── Alarmes.kt           o AlarmManager: um alarme por assunto, sempre o próximo
│   ├── Lembretes.kt         quem decide o que avisar e quando tocar de novo
│   ├── Agua.kt              o próximo copo: um intervalo depois do último, dentro da janela
│   ├── Festa.kt             o som da festa do fim do foco, e quanto dura o descanso
│   ├── Eventos.kt           os gatilhos da agenda, com as regras do site
│   └── Receptores.kt        o alarme tocou / o aparelho religou
├── sync/SyncWorker.kt       quem roda a sincronização, e quando
└── ui/
    ├── CascaApp.kt          lateral ou barra inferior, e a barra de cima
    ├── SessaoViewModel.kt   entrar, criar conta, usar sem conta, sair
    ├── componentes/         o que se repete: cartão, campo, botão, chave, lateral, retrato
    ├── telas/               uma pasta por tela grande, um arquivo por assunto
    │                        (diario/ é a grade do ano, os humores e a folha;
    │                        SubsDoPomodoro.kt são os outros dez cronômetros)
    └── theme/               tokens, paletas, fontes e tipografia
```

São 74 arquivos Kotlin, ~17,9 mil linhas. O limite é **700 linhas**: quando um
chega perto, ele se divide por assunto (foi assim que `postits/`, `planner/` e
`calendario/` viraram pastas, e por isso `avisos/` nasceu com seis arquivos em
vez de um). Hoje só `data/Repositorio.kt` passou disso, com 731 — está na fila
para se dividir.

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
