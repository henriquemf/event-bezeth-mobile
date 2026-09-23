package com.beazeth.notifier.avisos

import android.content.Context
import androidx.core.app.NotificationCompat
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.data.nomeDoSub
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.data.local.PomodoroEntity
import com.beazeth.notifier.ui.componentes.Destino
import com.beazeth.notifier.ui.telas.creditarPomodoroTerminado
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Quem decide o que avisar e quando tocar de novo.
 *
 * ## Recalcular do zero, sempre
 *
 * Nao ha estado guardado sobre "o que ja foi agendado". Toda rodada le o banco,
 * calcula os gatilhos de novo e remarca. E o que torna [rearmar] seguro de
 * chamar de qualquer lugar e quantas vezes for -- e chamam mesmo: ao abrir o
 * app, ao terminar cada sincronizacao, ao criar um evento, ao religar o
 * aparelho e a cada alarme que toca.
 *
 * A alternativa -- manter uma lista do que ja esta marcado e ir corrigindo --
 * seria um segundo estado para sair de sincronia com o primeiro. Um evento
 * apagado no site continuaria avisando no celular ate alguem notar.
 *
 * ## Marca d'agua em vez de tabela de entregues
 *
 * Se tudo e recalculado, o que impede o mesmo lembrete de ser entregue a cada
 * rodada e `Preferencias.avisoDeEventoAte`: um instante ate o qual a agenda ja
 * foi resolvida. Gatilho anterior a marca nao volta a ser considerado. A agua
 * tem a sua (`aguaMarcoEm`, o ultimo copo ou lembrete) e o pomodoro a dele
 * (`pomodoroAvisado`).
 *
 * ## Atraso tem limite
 *
 * Um aviso pode vencer com o aparelho desligado, ou com o app parado a forca --
 * e o Android CANCELA todos os alarmes de um app que foi parado a forca, ate
 * ele rodar de novo. Quando a agenda volta a ser calculada, ha gatilhos
 * vencidos na mao, e entregar todos seria uma avalanche de lembretes de coisas
 * que ja passaram. Cada [Antecedencia] diz quanto atraso ainda vale a pena; o
 * que passou disso avanca a marca d'agua em silencio.
 */
object Lembretes {

    /**
     * Recalcula e remarca os tres alarmes, entregando o que estiver vencido
     * dentro da tolerancia.
     *
     * O contexto vira `applicationContext` na entrada: isto e chamado de
     * `BroadcastReceiver` e de composables, e guardar um contexto de tela num
     * `PendingIntent` que vive horas e vazamento na certa.
     */
    suspend fun rearmar(context: Context) {
        val app = context.applicationContext
        garantirCanais(app, somEscolhido(app))
        resolverAgenda(app)
        remarcarAgua(app)
        remarcarPomodoro(app)
        remarcarSubs(app)
    }

    /** O alarme de [tipo] tocou. */
    suspend fun tocou(context: Context, tipo: Tipo) {
        val app = context.applicationContext
        garantirCanais(app, somEscolhido(app))
        when (tipo) {
            // A agua tambem: quem decide se "venceu" e a propria conta do
            // proximo copo, entao entregar e remarcar sao um passo so.
            Tipo.AGUA -> remarcarAgua(app)
            Tipo.POMODORO -> {
                entregarPomodoro(app)
                remarcarPomodoro(app)
            }
            Tipo.SUBPOMODORO -> {
                entregarSubs(app)
                remarcarSubs(app)
            }
            // A agenda resolve entrega e remarcacao na mesma passada: as duas
            // saem da mesma lista ordenada de gatilhos, e percorre-la duas
            // vezes so daria chance de as duas discordarem.
            Tipo.EVENTO -> resolverAgenda(app)
        }
    }

    // ---------------------------------------------------------------- agua

    /**
     * Alguem tocou no contador de agua -- um copo a mais ou a menos.
     *
     * Move o marco para agora e remarca: o proximo lembrete passa a ser um
     * intervalo depois DESTE copo, e nao do anterior. E o `last_sent_at` que o
     * servidor empurra em `/api/hydration/drink`, feito aqui porque o alarme e
     * daqui.
     *
     * O servidor so empurra ao ADICIONAR; aqui o "menos" tambem conta, de
     * proposito. Quem acabou de corrigir o contador esta com a agua na cabeca
     * -- e, com a meta desfeita, o lembrete que estava calado voltaria a valer e
     * sairia no mesmo instante, em cima da tela em que a pessoa esta.
     */
    suspend fun mexeuNaAgua(context: Context) {
        val app = context.applicationContext
        Preferencias(app).marcarAgua(System.currentTimeMillis())
        // Quem bebeu respondeu ao lembrete: ele sai da barra. Alem de ser o
        // certo, e o que impede a barra de juntar avisos deste app -- e avisos
        // juntos chegam mudos (ver `TEMPO_NA_BARRA`).
        desavisar(app, idDoAviso("agua"))
        remarcarAgua(app)
    }

    /**
     * A configuracao do lembrete mudou: ligou, desligou, trocou o intervalo ou
     * a janela.
     *
     * Irmao de [pomodoroMudou] e [subsMudaram], e pelo mesmo motivo: recalcular
     * a agenda inteira a cada arrasto de slider seria trabalho a toa.
     */
    suspend fun aguaMudou(context: Context) = remarcarAgua(context.applicationContext)

    /**
     * Entrega o lembrete de agua se ele estiver vencido, e marca o proximo.
     *
     * Entrega e remarcacao na mesma passada, como na agenda: as duas saem da
     * mesma conta ([proximoCopo]), e "venceu" e ela devolver um instante que
     * nao esta no futuro. E o que faz este metodo servir igualmente ao alarme
     * que tocou, ao app abrindo, a sincronizacao de hora em hora e ao aparelho
     * religando: se o alarme das 10:00 nao tocou -- aparelho desligado, ou um
     * fabricante que segura alarmes de app parado -- o lembrete sai na primeira
     * dessas oportunidades, e sai UMA vez, porque a entrega move o marco.
     *
     * Com a meta do dia batida nao ha o que entregar ate a janela reabrir, e o
     * alarme vai direto para la em vez de acordar o aparelho a cada intervalo
     * para descobrir isso de novo.
     */
    private suspend fun remarcarAgua(context: Context) {
        val prefs = Preferencias(context)

        // Desligado no app desmarca o alarme, e nao so cala a entrega: sem
        // isto o aparelho continuaria sendo acordado de hora em hora para
        // descobrir que nao ha nada a fazer.
        if (!ligado(context, Canal.AGUA)) {
            desmarcarAlarme(context, Tipo.AGUA)
            prefs.marcarProximoCopo(0L)
            return
        }

        val banco = BancoLocal.obter(context)
        val config = banco.agua().observarConfig().first()
        val agora = LocalDateTime.now()
        val marco = prefs.aguaMarcoEm.first().takeIf { it > 0L }?.let(::emHoraLocal)
        val batida = metaJaBatida(banco.agua().buscar(LocalDate.now().toString()), config)

        var proximo = proximoCopo(config, marco, agora)

        if (proximo != null && !proximo.isAfter(agora)) {
            if (!batida) {
                avisar(
                    context = context,
                    canal = Canal.AGUA,
                    som = somEscolhido(context),
                    id = idDoAviso("agua"),
                    titulo = TITULO_DA_AGUA,
                    texto = TEXTO_DA_AGUA,
                    rota = Destino.AGUA.rota,
                )
            }
            // O marco anda mesmo sem entrega: o vencimento ja foi julgado.
            prefs.marcarAgua(emMilissegundos(agora))
            proximo = proximoCopo(config, agora, agora)
        }

        if (batida && proximo != null) proximo = proximaAbertura(config, agora)

        if (proximo == null) {
            desmarcarAlarme(context, Tipo.AGUA)
            prefs.marcarProximoCopo(0L)
        } else {
            val instante = emMilissegundos(proximo)
            marcarAlarme(context, Tipo.AGUA, instante)
            prefs.marcarProximoCopo(instante)
        }
    }

    // -------------------------------------------------------------- agenda

    /**
     * Entrega os lembretes vencidos e marca o alarme para o proximo.
     *
     * A lista vem ordenada por instante, entao o primeiro gatilho no futuro e
     * exatamente o proximo alarme -- e o laco pode parar ali.
     */
    private suspend fun resolverAgenda(context: Context) {
        if (!ligado(context, Canal.EVENTOS)) {
            desmarcarAlarme(context, Tipo.EVENTO)
            return
        }

        val prefs = Preferencias(context)
        val eventos = BancoLocal.obter(context).eventos().observar().first()
        val agora = System.currentTimeMillis()
        val marca = prefs.avisoDeEventoAte.first()
        // Lidos uma vez, e nao dentro do laco: varios eventos podem vencer na
        // mesma passada, e cada leitura e uma ida ao disco.
        val som = somEscolhido(context)
        val mostrarNome = prefs.nomeDoEventoNaTelaBloqueada.first()

        var ultimoVencido = 0L
        var proximo = 0L

        for (gatilho in gatilhosDaAgenda(eventos)) {
            val quando = emMilissegundos(gatilho.quando)
            if (quando > agora) {
                proximo = quando
                break
            }

            // Ja resolvido numa rodada anterior. Nao mexe na marca: ela so
            // anda para a frente.
            if (quando <= marca) continue

            ultimoVencido = quando
            if (agora - quando <= gatilho.antecedencia.tolerancia.toMillis()) {
                avisar(
                    context = context,
                    canal = Canal.EVENTOS,
                    som = som,
                    id = gatilho.id,
                    titulo = gatilho.titulo,
                    texto = gatilho.texto,
                    rota = Destino.CALENDARIO.rota,
                    // Mostrando o nome, o aviso e PUBLIC e nao ha o que
                    // esconder; escondendo, ele e PRIVATE e leva o resumo.
                    resumoPublico = if (mostrarNome) null else gatilho.textoPublico,
                    visibilidade = if (mostrarNome) {
                        NotificationCompat.VISIBILITY_PUBLIC
                    } else {
                        NotificationCompat.VISIBILITY_PRIVATE
                    },
                )
            }
        }

        // Avanca mesmo pelo que NAO foi entregue por atraso: eles ja foram
        // julgados, e deixa-los para tras faria a proxima rodada julga-los de
        // novo, para sempre.
        if (ultimoVencido > 0L) prefs.marcarAvisoDeEventoAte(ultimoVencido)

        if (proximo > 0L) {
            marcarAlarme(context, Tipo.EVENTO, proximo)
        } else {
            desmarcarAlarme(context, Tipo.EVENTO)
        }
    }

    /**
     * O cronometro mudou: começou, pausou, zerou ou trocou de tempo.
     *
     * Existe separado de [rearmar] porque estes quatro toques acontecem no meio
     * de uma interacao, e recalcular a agenda inteira -- que nao mudou -- a
     * cada aperto de botao seria trabalho a toa na thread de quem esta olhando.
     */
    suspend fun pomodoroMudou(context: Context) {
        val app = context.applicationContext
        // "Tempo, momo!" deixou de ser verdade no instante em que alguem tocou
        // em Começar ou Zerar -- e um aviso vencido na barra cala o proximo.
        desavisar(app, idDoAviso("pomodoro"))
        remarcarPomodoro(app)
    }

    /**
     * Algum dos outros pomodoros mudou: nasceu, começou, pausou, zerou, trocou
     * de tempo ou foi removido.
     *
     * Irmao de [pomodoroMudou], e existe pelo mesmo motivo: sao toques no meio
     * de uma interacao, e recalcular a agenda inteira a cada um seria trabalho
     * a toa na thread de quem esta olhando.
     */
    suspend fun subsMudaram(context: Context, idDoSub: String? = null) {
        val app = context.applicationContext
        // O id chega quando a mudanca foi num sub especifico; criar um novo nao
        // tem aviso velho para tirar.
        if (idDoSub != null) desavisar(app, idDoAviso("sub-" + idDoSub))
        remarcarSubs(app)
    }

    // ------------------------------------------------------------ pomodoro

    /**
     * Anuncia o fim da contagem -- e credita o pomodoro na conta do perfil.
     *
     * O credito acontece aqui tambem, e nao so nas telas, porque este e o
     * primeiro momento em que alguem SABE que o pomodoro acabou: um pomodoro
     * que termina com o app fechado so entrava na conta na proxima vez que o
     * app fosse aberto. Reusa a funcao que ja existia em vez de repetir a
     * regra; o carimbo `pomoCreditado` faz a chamada repetida nao contar duas
     * vezes.
     */
    private suspend fun entregarPomodoro(context: Context) {
        val prefs = Preferencias(context)
        val agora = System.currentTimeMillis()

        // O DESCANSO acabou. Vem antes do foco de proposito: enquanto ele corre,
        // o `pomoFimEm` do foco ja esta vencido e gravado, e sem esta saida o
        // fim do foco seria julgado de novo a cada rodada.
        //
        // Zerar e o proprio carimbo de "ja anunciado" -- por isso nao ha um
        // segundo carimbo para o descanso. Sem zerar, comecar outro pomodoro
        // depois nunca teria o fim entregue, porque este ramo venceria sempre.
        val descansoAte = prefs.pomoDescansoAte.first()
        if (descansoAte > 0L) {
            if (agora < descansoAte) return
            prefs.marcarDescansoAte(0L)
            if (!ligado(context, Canal.POMODORO)) return
            avisar(
                context = context,
                canal = Canal.POMODORO,
                som = somEscolhido(context),
                id = idDoAviso("pomodoro"),
                titulo = "Fim do descanso 🍎",
                texto = "Se estiver pronta, começa outro foco. Se não, tudo bem também 💗",
                rota = Destino.POMODORO.rota,
            )
            return
        }

        val fimEm = prefs.pomoFimEm.first()
        if (fimEm <= 0L || agora < fimEm) return
        if (prefs.pomodoroAvisado.first() == fimEm) return

        creditarPomodoroTerminado(prefs, BancoLocal.obter(context))
        prefs.marcarPomodoroAvisado(fimEm)

        val minutos = prefs.pomoMinutos.first()

        // O descanso comeca sozinho: e a regra do pomodoro, e um botao "agora
        // descansar" seria so um jeito de esquecer de aperta-lo. O contrario
        // nao vale -- quando o descanso acaba, nada recomeca; voltar a focar e
        // decisao de quem esta ali.
        prefs.marcarDescansoAte(agora + descansoDe(minutos) * 60_000L)

        // Depois de creditar, e nao antes: o pomodoro entra na conta do perfil
        // mesmo com o aviso desligado. Uma coisa e nao querer ser interrompida;
        // outra e perder as horas de foco do proprio historico.
        if (!ligado(context, Canal.POMODORO)) return

        // Com o app na frente quem comemora e a tela, com confete e palmas (ver
        // `Visibilidade`). Postar tambem aqui seria o mesmo fim anunciado duas
        // vezes, com dois sons por cima um do outro.
        if (Visibilidade.appNaFrente) return

        avisar(
            context = context,
            canal = Canal.POMODORO,
            som = somEscolhido(context),
            id = idDoAviso("pomodoro"),
            titulo = "Tempo, momo! 🍎",
            // O caso de um minuto nao e hipotese: e o menor tempo que o slider
            // oferece, e foi assim que "Os 1 minutos de foco acabaram" apareceu
            // no primeiro teste de verdade.
            texto = if (minutos == 1) {
                "Um minuto de foco. Agora levanta, espreguiça e bebe uma água 💗"
            } else {
                "$minutos minutos de foco. Agora levanta, espreguiça e bebe uma água 💗"
            },
            rota = Destino.POMODORO.rota,
        )
    }

    /**
     * Marca o alarme para o fim da contagem, se ela estiver correndo.
     *
     * Contagem parada ou ja vencida desmarca. Nao anuncia nada: um pomodoro que
     * venceu com o app parado ja aparece como "Tempo!" na tela e no widget da
     * lateral assim que alguem olha -- avisar na barra horas depois seria um
     * susto, nao um lembrete.
     */
    private suspend fun remarcarPomodoro(context: Context) {
        val prefs = Preferencias(context)
        val agora = System.currentTimeMillis()

        // Um dos dois esta correndo, nunca os dois: o descanso so existe depois
        // que o foco venceu. O `firstOrNull` escolhe o que ainda esta no futuro.
        val proximo = listOf(prefs.pomoDescansoAte.first(), prefs.pomoFimEm.first())
            .firstOrNull { it > agora }

        if (proximo != null && ligado(context, Canal.POMODORO)) {
            marcarAlarme(context, Tipo.POMODORO, proximo)
        } else {
            desmarcarAlarme(context, Tipo.POMODORO)
        }
    }

    // -------------------------------------------------------- sub-pomodoros

    /**
     * Anuncia os sub-pomodoros que chegaram ao fim, credita e comeca o descanso
     * de cada um.
     *
     * A mesma ordem de [entregarPomodoro] -- descanso antes do foco -- e pelo
     * mesmo motivo: enquanto o intervalo corre, o `fimEm` do foco ja venceu e
     * continua gravado, e sem esta saida o fim do foco seria julgado de novo a
     * cada rodada.
     *
     * A lista inteira e percorrida numa passada so e gravada uma vez: sao ate
     * dez, e dez gravacoes no DataStore seriam dez idas ao disco para uma
     * mudanca que aconteceu no mesmo instante.
     */
    private suspend fun entregarSubs(context: Context) {
        val prefs = Preferencias(context)
        val lista = prefs.subsDoPomodoro.first()
        if (lista.isEmpty()) return

        val agora = System.currentTimeMillis()
        val banco = BancoLocal.obter(context)
        val avisaveis = ligado(context, Canal.POMODORO)
        val som = somEscolhido(context)
        var mudou = false

        val novos = lista.mapIndexed { indice, sub ->
            val nome = nomeDoSub(sub.nome, indice)

            if (sub.descansoAte > 0L) {
                if (agora < sub.descansoAte) return@mapIndexed sub
                mudou = true
                // Sem a condicao de "app fechado" que o fim do FOCO tem: o fim
                // do foco e anunciado por dentro com confete e palmas, e este
                // nao e anunciado por nada. Calar aqui com o app aberto era
                // deixar o descanso acabar em silencio absoluto.
                if (avisaveis) {
                    avisar(
                        context = context,
                        canal = Canal.POMODORO,
                        som = som,
                        id = idDoAviso("sub-" + sub.id),
                        titulo = "Fim do descanso 🍎 — $nome",
                        texto = "Se estiver pronta, começa outro foco. Se não, tudo bem também 💗",
                        rota = Destino.POMODORO.rota,
                    )
                }
                return@mapIndexed sub.copy(descansoAte = 0L)
            }

            if (sub.fimEm <= 0L || agora < sub.fimEm) return@mapIndexed sub
            if (sub.avisado == sub.fimEm) return@mapIndexed sub

            mudou = true

            // O credito entra na conta do perfil mesmo com o aviso desligado:
            // uma coisa e nao querer ser interrompida, outra e perder as horas
            // de foco do proprio historico.
            if (sub.creditado != sub.fimEm) {
                banco.pomodoros().gravar(
                    PomodoroEntity(terminadoEm = sub.fimEm, minutos = sub.minutos),
                )
            }

            // Com o app na frente quem comemora e a tela, com confete e palmas
            // (ver `Visibilidade`). Postar aqui tambem seria o mesmo fim
            // anunciado duas vezes, com dois sons por cima um do outro.
            if (avisaveis && !Visibilidade.appNaFrente) {
                avisar(
                    context = context,
                    canal = Canal.POMODORO,
                    som = som,
                    id = idDoAviso("sub-" + sub.id),
                    titulo = "Tempo, momo! 🍎 — $nome",
                    texto = if (sub.minutos == 1) {
                        "Um minuto de foco. Agora levanta, espreguiça e bebe uma água 💗"
                    } else {
                        "${sub.minutos} minutos de foco. Agora levanta, espreguiça e bebe uma água 💗"
                    },
                    rota = Destino.POMODORO.rota,
                )
            }

            // O descanso comeca sozinho, como no principal.
            sub.copy(
                avisado = sub.fimEm,
                creditado = sub.fimEm,
                descansoAte = agora + descansoDe(sub.minutos) * 60_000L,
            )
        }

        if (mudou) prefs.salvarSubs(novos)
    }

    /**
     * Um alarme so para os dez, marcado para o PROXIMO que vencer.
     *
     * E a mesma regra da agenda (ver `Alarmes`): quando ele toca, quem entrega
     * recalcula e remarca. Sem isto seriam ate vinte alarmes exatos -- foco e
     * descanso de cada sub -- para manter em dia a cada toque de botao.
     */
    private suspend fun remarcarSubs(context: Context) {
        val agora = System.currentTimeMillis()
        val proximo = Preferencias(context).subsDoPomodoro.first()
            .flatMap { listOf(it.descansoAte, it.fimEm) }
            .filter { it > agora }
            .minOrNull()

        if (proximo != null && ligado(context, Canal.POMODORO)) {
            marcarAlarme(context, Tipo.SUBPOMODORO, proximo)
        } else {
            desmarcarAlarme(context, Tipo.SUBPOMODORO)
        }
    }

    // ------------------------------------------------------------ escolhas

    /** Este assunto avisa? Ver `Preferencias.avisoLigado`. */
    private suspend fun ligado(context: Context, canal: Canal): Boolean =
        Preferencias(context).avisoLigado(canal.base).first()

    /** O toque escolhido. */
    private suspend fun somEscolhido(context: Context): Som =
        Som.porChave(Preferencias(context).somDoAviso.first())
}

/**
 * Hora local em milissegundos do relogio.
 *
 * O banco guarda hora LOCAL sem fuso (o servidor tambem), e os alarmes sao
 * marcados em milissegundos absolutos. A conversao usa o fuso do aparelho:
 * "14:00" quer dizer duas da tarde onde a pessoa esta.
 */
private fun emMilissegundos(quando: LocalDateTime): Long =
    quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** O caminho de volta: milissegundos do relogio em hora local do aparelho. */
private fun emHoraLocal(instante: Long): LocalDateTime =
    Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault()).toLocalDateTime()
