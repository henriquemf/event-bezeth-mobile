package com.beazeth.notifier.ui.telas

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.avisos.descansoDe
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.data.segundosAte
import com.beazeth.notifier.ui.componentes.Ampulheta
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.LinhaComChave
import com.beazeth.notifier.ui.componentes.anelDoMostrador
import com.beazeth.notifier.ui.componentes.janelaDeitada
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.data.local.PomodoroEntity
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.Paleta
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O Pomodoro.
 *
 * A unica tela que nao fala com o servidor: o estado dela e o relogio, e
 * relogio nao se sincroniza -- um timer rodando em dois aparelhos ao mesmo
 * tempo nao tem significado. No site e igual, tudo em JavaScript local.
 *
 * **A contagem sobrevive a troca de tela.** A primeira versao guardava os
 * segundos num `remember` da propria tela: sair da aba destruia o estado e o
 * timer voltava ao zero. Agora o que se guarda e o INSTANTE EM QUE TERMINA, no
 * DataStore. Nada precisa ficar vivo contando -- a conta e feita pelo relogio
 * do sistema toda vez que alguem olha. Funciona inclusive se o app for fechado
 * ou o processo morto.
 */

/** Espelha `PRESETS` em `app/blueprints/pomodoro.py`, na mesma ordem. */
private val PRONTOS = listOf(
    5 to "Respiro",
    15 to "Rápido",
    25 to "Clássico",
    30 to "Foco",
    45 to "Longo",
    60 to "Profundo",
)

private const val MIN_MINUTOS = 1
private const val MAX_MINUTOS = 600

/** O que a tela precisa saber. */
data class EstadoPomodoro(
    val minutos: Int = 25,
    val restante: Int = 25 * 60,
    val correndo: Boolean = false,
    /**
     * O instante em que a contagem termina, ou zero se estiver parada.
     *
     * Sai do DataStore junto com o resto e serve a quem CREDITA o pomodoro
     * terminado: e a identidade daquele pomodoro -- ver
     * `Preferencias.pomoCreditado`.
     */
    val fimEm: Long = 0L,
    /**
     * O que corre agora e o descanso, e nao o foco.
     *
     * Quando isto e verdadeiro, [minutos] ja e a duracao DO DESCANSO -- e o que
     * faz o anel e a ampulheta encherem na proporcao certa sem nenhuma tela
     * precisar saber que ha duas fases.
     */
    val emDescanso: Boolean = false,
    /**
     * O tempo de FOCO escolhido, sempre -- inclusive durante o descanso.
     *
     * Existe separado de [minutos] por um defeito que so apareceu rodando: no
     * descanso, [minutos] passa a ser a duracao do intervalo (5), e o seletor
     * de tempo, que lia dali, acendia "5 Respiro" como se a pessoa tivesse
     * trocado a propria escolha. O mostrador quer a fase que corre; o seletor
     * quer a escolha. Sao duas perguntas diferentes.
     */
    val escolhidos: Int = 25,
) {
    val total: Int get() = minutos * 60

    /** Fracao ja DECORRIDA, como o `--pomo-progress` do site: o anel enche e a
     *  areia desce conforme o tempo passa. */
    val progresso: Float get() = if (total > 0) 1f - (restante.toFloat() / total) else 0f
}

/**
 * O estado do pomodoro, derivado do DataStore mais um tique por segundo.
 *
 * **E funcao livre, e nao metodo de ViewModel, porque ha DOIS lugares olhando
 * para o mesmo relogio**: esta tela e o widget da barra lateral. A versao
 * anterior lia as preferencias uma vez no `init` e contava num campo proprio --
 * dois donos daquele campo seriam dois cronometros, e apertar "Começar" numa
 * ponta nao moveria a outra. Derivando tudo do DataStore, quem manda e o disco,
 * e as duas telas leem a mesma coisa.
 *
 * O tique le o RELOGIO a cada volta em vez de subtrair 1: se o sistema atrasar
 * o `delay` -- e ele atrasa, sob carga ou com a tela apagada -- subtrair
 * acumularia erro, e um pomodoro de 25 minutos terminaria em 26.
 */
internal fun estadoDoPomodoro(prefs: Preferencias): Flow<EstadoPomodoro> {
    val tique = tiqueDeUmSegundo()
    return combine(
        prefs.pomoMinutos,
        prefs.pomoFimEm,
        prefs.pomoRestante,
        prefs.pomoDescansoAte,
        tique,
    ) { minutos, fimEm, guardado, descansoAte, _ ->
        val faltaDoDescanso = if (descansoAte > 0L) segundosAte(descansoAte) else 0

        when {
            // O descanso manda enquanto corre. Vem primeiro porque, durante
            // ele, o `fimEm` do foco ja esta vencido e continua gravado -- e
            // mostrar "Tempo!" no meio do intervalo apressaria justamente quem
            // devia estar parada.
            faltaDoDescanso > 0 -> EstadoPomodoro(
                minutos = descansoDe(minutos),
                restante = faltaDoDescanso,
                correndo = true,
                fimEm = descansoAte,
                emDescanso = true,
                escolhidos = minutos,
            )

            fimEm > 0L -> {
                val restante = segundosAte(fimEm)
                EstadoPomodoro(
                    minutos, restante, correndo = restante > 0, fimEm = fimEm,
                    escolhidos = minutos,
                )
            }

            else -> EstadoPomodoro(minutos, guardado, correndo = false, escolhidos = minutos)
        }
    }
}

/**
 * Um pulso por segundo, para quem deriva estado do RELOGIO.
 *
 * Nada aqui conta nada: o pulso so diz "olhe de novo". Quem olha refaz a conta a
 * partir do relogio do sistema, e por isso um `delay` atrasado -- e ele atrasa,
 * sob carga ou com a tela apagada -- nao acumula erro.
 */
internal fun tiqueDeUmSegundo(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}

/**
 * O fim de foco do pomodoro principal que a casca ainda nao resolveu, ou zero.
 *
 * "Resolveu" quer dizer creditado, descanso comecado e decidido se havia festa
 * -- e o carimbo de tudo isso e `pomodoroFestejado`.
 *
 * **Nao serve olhar para "terminou e ainda nao esta em descanso"**, que era como
 * isto funcionava: o alarme vence no instante exato do fim e o pulso da tela
 * chega ate um segundo depois, entao quase sempre o alarme comecava o descanso
 * primeiro e a condicao nunca era verdadeira com o app aberto. O confete
 * simplesmente nao saia. Perguntar pelo carimbo nao tem corrida: quem chegar
 * primeiro faz, e quem chegar depois ve feito.
 */
internal fun fimDePomodoroPendente(prefs: Preferencias): Flow<Long> =
    combine(prefs.pomoFimEm, prefs.pomodoroFestejado, tiqueDeUmSegundo()) { fimEm, festejado, _ ->
        if (fimEm > 0L && System.currentTimeMillis() >= fimEm && festejado != fimEm) fimEm else 0L
    }.distinctUntilChanged()

/**
 * Anota um pomodoro que chegou ao fim, para a tela de perfil somar.
 *
 * **Ninguem avisa quando um pomodoro termina.** O contador e derivado do
 * relogio: o app pode estar fechado na hora, e quem descobre e a primeira tela
 * a olhar depois. Por isso o credito nao esta preso a um evento -- e uma
 * pergunta que se faz sempre que o estado passa por zero, inclusive dois dias
 * depois, ao reabrir o app.
 *
 * Chamada de tres lugares (a tela do pomodoro, a casca -- que esta em todas as
 * outras telas -- e as proprias acoes de Começar e Zerar, que apagariam o
 * instante antes de alguem olhar). Chamar demais nao custa: o carimbo em
 * `pomoCreditado` faz da segunda em diante um retorno imediato, e a chave
 * natural da tabela nao deixaria duplicar de qualquer forma.
 */
internal suspend fun creditarPomodoroTerminado(prefs: Preferencias, banco: BancoLocal) {
    val fimEm = prefs.pomoFimEm.first()
    if (fimEm <= 0L || System.currentTimeMillis() < fimEm) return
    if (prefs.pomoCreditado.first() == fimEm) return

    banco.pomodoros().gravar(
        PomodoroEntity(terminadoEm = fimEm, minutos = prefs.pomoMinutos.first()),
    )
    prefs.marcarPomodoroCreditado(fimEm)
}

/**
 * Começar/Pausar. Vale para a tela e para o widget da lateral.
 *
 * O [context] entra so para remarcar o alarme no fim: e ele que faz o "Tempo!"
 * aparecer na barra de notificacao com o app fechado. Sem esta chamada, um
 * pomodoro comecado e deixado de lado terminaria em silencio, e a pessoa so
 * descobriria ao reabrir o app -- que e exatamente o que um cronometro existe
 * para evitar.
 */
internal suspend fun alternarPomodoro(context: Context, prefs: Preferencias, banco: BancoLocal) {
    // Antes de mexer: se o que estava la ja tinha terminado, este toque e um
    // recomeço -- e o instante antigo, que identifica o pomodoro cumprido, esta
    // prestes a ser sobrescrito.
    creditarPomodoroTerminado(prefs, banco)

    val minutos = prefs.pomoMinutos.first()

    // Durante o descanso o botao e "Pular descanso", e nao "Pausar": encerra o
    // intervalo e para por ali. Comecar outro foco em seguida seria decidir
    // pela pessoa justamente no momento em que ela disse que nao queria o que
    // estava acontecendo.
    if (prefs.pomoDescansoAte.first() > System.currentTimeMillis()) {
        prefs.marcarDescansoAte(0L)
        prefs.salvarPomodoro(minutos, 0L, minutos * 60)
        Lembretes.pomodoroMudou(context)
        return
    }

    val fimEm = prefs.pomoFimEm.first()
    val restante = if (fimEm > 0L) segundosAte(fimEm) else prefs.pomoRestante.first()

    // Um descanso ja vencido, mas ainda gravado, ficaria na frente do foco
    // novo -- inclusive no alarme.
    prefs.marcarDescansoAte(0L)

    if (fimEm > 0L && restante > 0) {
        // Pausar: guarda os segundos que faltam e esquece o instante.
        prefs.salvarPomodoro(minutos, 0L, restante)
    } else {
        // Começar. Depois de "Tempo!" o instante antigo ainda esta gravado e ja
        // venceu; tratar isso como recomeço evita o toque perdido de quem
        // aperta "Começar" e ve o mesmo zero.
        val segundos = if (restante > 0) restante else minutos * 60
        prefs.salvarPomodoro(minutos, System.currentTimeMillis() + segundos * 1_000L, segundos)
    }
    Lembretes.pomodoroMudou(context)
}

/** Zerar, de volta ao tempo cheio. */
internal suspend fun zerarPomodoro(context: Context, prefs: Preferencias, banco: BancoLocal) {
    creditarPomodoroTerminado(prefs, banco)
    val minutos = prefs.pomoMinutos.first()
    prefs.marcarDescansoAte(0L)
    prefs.salvarPomodoro(minutos, 0L, minutos * 60)
    // Desmarca: sem isto o alarme do pomodoro que acabou de ser zerado
    // continuaria marcado e apitaria na hora em que ele TERIA terminado.
    Lembretes.pomodoroMudou(context)
}

class PomodoroViewModel(app: Application) : AndroidViewModel(app) {

    private val contexto = app.applicationContext
    private val prefs = Preferencias(app)
    private val banco = BancoLocal.obter(app)

    val estado = estadoDoPomodoro(prefs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoPomodoro())

    fun alternar() = viewModelScope.launch { alternarPomodoro(contexto, prefs, banco) }

    fun zerar() = viewModelScope.launch { zerarPomodoro(contexto, prefs, banco) }

    val descansoAutomatico = prefs.descansoAutomatico
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Nao remarca alarme nenhum: o alarme do fim do foco e o mesmo com ou sem
     *  descanso, e quem le a escolha e a entrega, no instante do fim. */
    fun definirDescansoAutomatico(ligado: Boolean) = viewModelScope.launch {
        prefs.definirDescansoAutomatico(ligado)
    }

    /** Chamado quando a contagem chega a zero com a tela aberta. */
    fun creditar() = viewModelScope.launch { creditarPomodoroTerminado(prefs, banco) }

    /** Trocar o tempo so vale com o timer parado; a tela nao oferece o
     *  contrario, mas o ViewModel nao confia nisso. */
    fun definirMinutos(minutos: Int) = viewModelScope.launch {
        if (estado.value.correndo) return@launch
        val limpo = minutos.coerceIn(MIN_MINUTOS, MAX_MINUTOS)
        prefs.salvarPomodoro(limpo, 0L, limpo * 60)
        // Trocar o tempo zera o instante de fim, entao o alarme que existia nao
        // corresponde mais a nada. Arrastar o slider com uma contagem PAUSADA e
        // o caminho que chega aqui com um alarme velho para desmarcar.
        Lembretes.pomodoroMudou(contexto)
    }
}

@Composable
fun PomodoroScreen(vm: PomodoroViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    val cores = Doce
    val deitado = janelaDeitada()

    // Chegou a zero com esta tela aberta: entra na conta do perfil na hora, sem
    // esperar o proximo toque. A chave inclui o `fimEm` para o efeito rodar de
    // novo no pomodoro seguinte, e nao uma vez so por visita.
    LaunchedEffect(estado.fimEm, estado.restante == 0) {
        if (estado.fimEm > 0L && estado.restante == 0) vm.creditar()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Espaco.e5, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
        ),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        item {
            CartaoDaTela {
                // Deitado, o mostrador vai para o lado e os controles ficam ao
                // lado dele. Empilhados, o anel de 224 dp mais o relogio mais
                // os dois botoes passam da altura da janela -- e "Começar" cai
                // abaixo da dobra, num cronometro em que esse e o unico botao
                // que importa.
                if (deitado) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e5),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mostrador(estado = estado, cores = cores, tamanho = 150.dp)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Espaco.e2),
                        ) {
                            ControlesDoTempo(estado = estado, cores = cores, vm = vm)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
                    ) {
                        Mostrador(estado = estado, cores = cores, tamanho = 224.dp)
                        ControlesDoTempo(estado = estado, cores = cores, vm = vm)
                    }
                }
            }
        }

        item {
            CartaoDaTela(titulo = "Escolher tempo") {
                // Dois por linha: seis pilulas numa fileira so em 360 dp dariam
                // 55 dp cada, abaixo do alvo de toque confortavel.
                PRONTOS.chunked(3).forEach { fileira ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                    ) {
                        for ((min, rotulo) in fileira) {
                            TempoPronto(
                                minutos = min,
                                rotulo = rotulo,
                                ativo = min == estado.escolhidos,
                                // Trocar o tempo com o timer rodando confundiria
                                // o que o anel esta mostrando. O site tambem tranca.
                                habilitado = !estado.correndo,
                                aoTocar = { vm.definirMinutos(min) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Personalizado",
                        style = TipografiaBeazeth.labelLarge,
                        color = cores.tintaSuave,
                    )
                    Text(
                        text = "${estado.escolhidos} min",
                        style = TipografiaBeazeth.titleMedium,
                        color = cores.tinta,
                    )
                }

                Slider(
                    value = estado.escolhidos.coerceAtMost(120).toFloat(),
                    onValueChange = { vm.definirMinutos(it.toInt()) },
                    valueRange = MIN_MINUTOS.toFloat()..120f,
                    enabled = !estado.correndo,
                    colors = SliderDefaults.colors(
                        thumbColor = cores.destaque,
                        activeTrackColor = cores.destaque,
                        inactiveTrackColor = cores.destaque.copy(alpha = 0.22f),
                    ),
                )

                Text(
                    text = if (estado.correndo) {
                        "Pause para trocar o tempo."
                    } else {
                        "De $MIN_MINUTOS a $MAX_MINUTOS minutos. O slider vai até 120."
                    },
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
            }
        }

        // Os outros pomodoros, ate dez. Sao daqui e so daqui: a casca mostra
        // apenas o principal. Ver `SubsDoPomodoro.kt`.
        item { SecaoDosSubs() }
    }
}

@Composable
private fun TempoPronto(
    minutos: Int,
    rotulo: String,
    ativo: Boolean,
    habilitado: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (ativo) Doce.destaque.copy(alpha = 0.18f) else Doce.fundoCampo)
            .border(
                1.dp,
                if (ativo) Doce.destaque else Doce.traco,
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = habilitado, onClick = aoTocar)
            .padding(vertical = Espaco.e2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$minutos",
            style = TipografiaBeazeth.headlineMedium.copy(fontSize = 20.sp),
            color = if (ativo) Doce.destaqueEscuro else Doce.tinta,
        )
        Text(
            text = rotulo,
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
            color = Doce.tintaSuave,
        )
    }
}

/** O anel e a ampulheta. Extraido porque a montagem em pe e a deitada usam o
 *  mesmo mostrador em tamanhos diferentes -- duas copias sairiam de sincronia
 *  na primeira mudanca. */
@Composable
private fun Mostrador(estado: EstadoPomodoro, cores: Paleta, tamanho: Dp) {
    Box(
        modifier = Modifier
            .size(tamanho)
            .drawBehind {
                anelDoMostrador(
                    progresso = estado.progresso,
                    corViva = cores.destaque,
                    corApagada = cores.destaque.copy(alpha = 0.15f),
                    espessura = 9.dp.toPx(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Ampulheta(
            progresso = estado.progresso,
            correndo = estado.correndo,
            largura = tamanho * 0.375f,
        )
    }
}

/** O relogio, o estado em palavras e os dois botoes. */
@Composable
private fun ControlesDoTempo(
    estado: EstadoPomodoro,
    cores: Paleta,
    vm: PomodoroViewModel,
) {
    Text(
        text = "%02d:%02d".format(estado.restante / 60, estado.restante % 60),
        style = TipografiaBeazeth.headlineMedium.copy(fontSize = 44.sp),
        color = cores.tinta,
    )

    Text(
        text = when {
            // O descanso primeiro: durante ele o cronometro corre, e "Focando…"
            // seria o oposto do que a tela devia dizer.
            estado.emDescanso -> "Descanso 💗"
            estado.restante <= 0 -> "Tempo!"
            estado.correndo -> "Focando…"
            estado.restante < estado.total -> "Pausado"
            else -> "Pronto para começar"
        },
        style = TipografiaBeazeth.bodyLarge,
        color = cores.tintaSuave,
    )

    BotaoPrimario(
        texto = when {
            estado.emDescanso -> "Pular descanso"
            estado.correndo -> "Pausar"
            else -> "Começar"
        },
        aoTocar = vm::alternar,
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = vm::zerar)
            .padding(horizontal = Espaco.e4, vertical = Espaco.e2),
    ) {
        Text(
            text = "Zerar",
            style = TipografiaBeazeth.titleMedium,
            color = cores.tintaSuave,
        )
    }

    // Vale para este e para os subs de baixo: sao o mesmo relogio. Fica aqui,
    // junto do botao, e nao no perfil, porque e uma escolha sobre ESTE ciclo --
    // quem desliga para uma prova precisa achar a chave onde esta o timer.
    val descansoAutomatico by vm.descansoAutomatico.collectAsState()
    LinhaComChave(
        titulo = "Descanso automático",
        descricao = if (descansoAutomatico) {
            "Quando o foco acaba, o descanso começa sozinho."
        } else {
            "O foco termina parado no fim, e o próximo passo é seu."
        },
        marcado = descansoAutomatico,
        aoMudar = vm::definirDescansoAutomatico,
        modifier = Modifier.padding(top = Espaco.e1),
    )
}
