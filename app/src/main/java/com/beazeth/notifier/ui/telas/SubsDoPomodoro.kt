package com.beazeth.notifier.ui.telas

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.data.EstadoDoSub
import com.beazeth.notifier.data.MAXIMO_DE_SUBS
import com.beazeth.notifier.data.MAXIMO_DO_NOME_DO_SUB
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.data.SubPomodoro
import com.beazeth.notifier.data.agora
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.data.local.PomodoroEntity
import com.beazeth.notifier.data.nomeDoSub
import com.beazeth.notifier.data.segundosAte
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CampoDoce
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Os outros pomodoros: ate dez, na mesma tela, contando igual ao principal.
 *
 * ## Por que eles nao viraram outra tela nem outro widget
 *
 * O principal e o que a casca mostra -- widget na lateral, nome no menu. Dez
 * widgets empilhados empurrariam o menu para fora da tela, e o que a casca
 * precisa dizer e uma coisa so: "tem outro contando". Isso cabe num numero em
 * cima do link do Pomodoro (ver [quantosSubsCorrendo]).
 *
 * ## O que e igual e o que e diferente do principal
 *
 * Igual: o instante do fim guardado em vez dos segundos restantes, o descanso
 * que comeca sozinho, o credito no perfil, as palmas e o confete. Diferente:
 * o estado dos dez mora numa lista so ([SubPomodoro]) e os dez dividem um
 * alarme -- o do proximo a vencer.
 *
 * Nao ha ampulheta no cartao, e isso e escolha: dez ampulhetas chacoalhando ao
 * mesmo tempo viram um circo, e o que o cartao precisa dizer -- quanto falta e
 * quanto ja andou -- o numero e a barrinha dizem melhor no tamanho que ele tem.
 */

private const val MINUTOS_NO_SLIDER = 120

/* ---------------------------------------------------------------- leitura */

/**
 * Os dez, vistos agora, com um tique por segundo.
 *
 * Funcao livre e nao metodo de ViewModel pelo mesmo motivo de
 * [estadoDoPomodoro]: ha mais de um lugar olhando para o mesmo relogio -- esta
 * tela e a casca, que precisa saber quando festejar e quantos estao correndo.
 * Derivando tudo do DataStore, quem manda e o disco.
 */
internal fun estadoDosSubs(prefs: Preferencias): Flow<List<EstadoDoSub>> =
    combine(prefs.subsDoPomodoro, tiqueDeUmSegundo()) { subs, _ -> subs.map { it.agora() } }

/**
 * O fim de foco de algum sub que a casca ainda nao resolveu, ou zero.
 *
 * Irmao de [fimDePomodoroPendente], e pela mesma razao -- ver o comentario de
 * la sobre a corrida entre o alarme e o pulso da tela.
 */
internal fun fimDeSubPendente(prefs: Preferencias): Flow<Long> =
    combine(prefs.subsDoPomodoro, tiqueDeUmSegundo()) { lista, _ ->
        val agora = System.currentTimeMillis()
        lista.firstOrNull { it.fimEm > 0L && agora >= it.fimEm && it.festejado != it.fimEm }
            ?.fimEm ?: 0L
    }.distinctUntilChanged()

/**
 * Quantos dos outros pomodoros estao contando agora.
 *
 * O `distinctUntilChanged` nao e enfeite: o fluxo de tras bate a cada segundo, e
 * sem ele as duas barras de navegacao seriam recompostas sessenta vezes por
 * minuto para escrever o mesmo numero.
 */
internal fun quantosSubsCorrendo(prefs: Preferencias): Flow<Int> =
    estadoDosSubs(prefs)
        .map { lista -> lista.count { it.correndo } }
        .distinctUntilChanged()

/* ------------------------------------------------------------------ acoes */

/** Troca um sub pelo resultado de [mudar], preservando a ordem. */
private suspend fun mexer(prefs: Preferencias, id: String, mudar: (SubPomodoro) -> SubPomodoro) {
    val lista = prefs.subsDoPomodoro.first()
    prefs.salvarSubs(lista.map { if (it.id == id) mudar(it) else it })
}

/**
 * Credita todo sub cujo foco ja venceu e ainda nao entrou na conta do perfil.
 *
 * Irmao de [creditarPomodoroTerminado], e existe pela mesma razao: ninguem
 * avisa quando um pomodoro termina, entao o credito nao pode estar preso a um
 * evento -- e uma pergunta que se faz sempre que o estado passa por zero,
 * inclusive dois dias depois, ao reabrir o app.
 */
internal suspend fun creditarSubsTerminados(prefs: Preferencias, banco: BancoLocal) {
    val agora = System.currentTimeMillis()
    val lista = prefs.subsDoPomodoro.first()
    var mudou = false

    val novos = lista.map { sub ->
        if (sub.fimEm <= 0L || agora < sub.fimEm || sub.creditado == sub.fimEm) {
            sub
        } else {
            banco.pomodoros().gravar(
                PomodoroEntity(terminadoEm = sub.fimEm, minutos = sub.minutos),
            )
            mudou = true
            sub.copy(creditado = sub.fimEm)
        }
    }

    if (mudou) prefs.salvarSubs(novos)
}

/** Nasce com o tempo escolhido no principal, e parado. */
internal suspend fun criarSub(context: Context, prefs: Preferencias) {
    val lista = prefs.subsDoPomodoro.first()
    if (lista.size >= MAXIMO_DE_SUBS) return
    val minutos = prefs.pomoMinutos.first()
    prefs.salvarSubs(lista + SubPomodoro(id = novoIdDeSub(), minutos = minutos))
    Lembretes.subsMudaram(context)
}

internal suspend fun removerSub(context: Context, prefs: Preferencias, id: String) {
    prefs.salvarSubs(prefs.subsDoPomodoro.first().filterNot { it.id == id })
    // Sem isto, o alarme marcado para o sub que acabou de sair continuaria de
    // pe e acordaria o aparelho para nao encontrar nada.
    Lembretes.subsMudaram(context)
}

/** Começar / Pausar / Pular descanso, conforme o que estiver acontecendo. */
internal suspend fun alternarSub(
    context: Context,
    prefs: Preferencias,
    banco: BancoLocal,
    id: String,
) {
    // Antes de mexer: se o que estava la ja tinha terminado, este toque e um
    // recomeço -- e o instante que identifica o pomodoro cumprido esta prestes
    // a ser sobrescrito.
    creditarSubsTerminados(prefs, banco)

    val agora = System.currentTimeMillis()
    mexer(prefs, id) { sub ->
        when {
            // Durante o descanso o botao e "Pular descanso": encerra o
            // intervalo e para por ali. Comecar outro foco em seguida seria
            // decidir pela pessoa justamente quando ela disse que nao queria o
            // que estava acontecendo.
            sub.descansoAte > agora ->
                sub.copy(descansoAte = 0L, fimEm = 0L, restante = sub.minutos * 60)

            else -> {
                val falta = if (sub.fimEm > 0L) segundosAte(sub.fimEm) else sub.restante
                if (sub.fimEm > 0L && falta > 0) {
                    // Pausar: guarda os segundos que faltam e esquece o instante.
                    sub.copy(fimEm = 0L, restante = falta, descansoAte = 0L)
                } else {
                    // Começar. Depois de "Tempo!" o instante antigo ainda esta
                    // gravado e ja venceu; tratar isso como recomeço evita o
                    // toque perdido de quem aperta e ve o mesmo zero.
                    val segundos = if (falta > 0) falta else sub.minutos * 60
                    sub.copy(
                        fimEm = agora + segundos * 1_000L,
                        restante = segundos,
                        descansoAte = 0L,
                    )
                }
            }
        }
    }
    Lembretes.subsMudaram(context)
}

internal suspend fun zerarSub(
    context: Context,
    prefs: Preferencias,
    banco: BancoLocal,
    id: String,
) {
    creditarSubsTerminados(prefs, banco)
    mexer(prefs, id) { it.copy(fimEm = 0L, descansoAte = 0L, restante = it.minutos * 60) }
    Lembretes.subsMudaram(context)
}

internal suspend fun definirMinutosDoSub(
    context: Context,
    prefs: Preferencias,
    id: String,
    minutos: Int,
) {
    mexer(prefs, id) { sub ->
        val limpo = minutos.coerceIn(1, MINUTOS_NO_SLIDER)
        sub.copy(minutos = limpo, fimEm = 0L, descansoAte = 0L, restante = limpo * 60)
    }
    // Trocar o tempo zera o instante do fim, entao o alarme que existia nao
    // corresponde mais a nada.
    Lembretes.subsMudaram(context)
}

internal suspend fun renomearSub(prefs: Preferencias, id: String, nome: String) {
    mexer(prefs, id) { it.copy(nome = nome.take(MAXIMO_DO_NOME_DO_SUB)) }
}

internal suspend fun dobrarSub(prefs: Preferencias, id: String, dobrado: Boolean) {
    mexer(prefs, id) { it.copy(dobrado = dobrado) }
}

/** Curto e sem sorte de repetir: o relogio da o comeco, o acaso da o resto. */
private fun novoIdDeSub(): String =
    System.currentTimeMillis().toString(36) + "-" + (100_000..999_999).random().toString(36)

class SubsViewModel(app: Application) : AndroidViewModel(app) {

    private val contexto = app.applicationContext
    private val prefs = Preferencias(app)
    private val banco = BancoLocal.obter(app)

    val estado = estadoDosSubs(prefs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun criar() = viewModelScope.launch { criarSub(contexto, prefs) }
    fun remover(id: String) = viewModelScope.launch { removerSub(contexto, prefs, id) }
    fun alternar(id: String) = viewModelScope.launch { alternarSub(contexto, prefs, banco, id) }
    fun zerar(id: String) = viewModelScope.launch { zerarSub(contexto, prefs, banco, id) }
    fun renomear(id: String, nome: String) = viewModelScope.launch { renomearSub(prefs, id, nome) }
    fun dobrar(id: String, dobrado: Boolean) =
        viewModelScope.launch { dobrarSub(prefs, id, dobrado) }

    fun definirMinutos(id: String, minutos: Int) =
        viewModelScope.launch { definirMinutosDoSub(contexto, prefs, id, minutos) }
}

/* ------------------------------------------------------------------- tela */

@Composable
internal fun SecaoDosSubs(vm: SubsViewModel = viewModel()) {
    val subs by vm.estado.collectAsState()
    val cores = Doce

    CartaoDaTela {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Espaco.e3),
            // Encostado no topo, e nao centrado: num celular estreito o titulo
            // quebra em duas linhas e a explicacao em quatro, e o + centrado
            // acabava flutuando no meio do paragrafo.
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Outros pomodoros",
                    style = TipografiaBeazeth.headlineMedium,
                    color = cores.tinta,
                )
                Text(
                    text = when {
                        subs.isEmpty() ->
                            "Nenhum por enquanto. O + cria um com o tempo escolhido aqui em cima."
                        subs.size >= MAXIMO_DE_SUBS ->
                            "São $MAXIMO_DE_SUBS, o máximo. Remova um para abrir espaço."
                        else ->
                            "Cada um conta o seu, com o mesmo descanso e as mesmas palmas no " +
                                "fim. Cabem $MAXIMO_DE_SUBS."
                    },
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
            }

            BotaoDeMais(
                habilitado = subs.size < MAXIMO_DE_SUBS,
                aoTocar = vm::criar,
            )
        }

        // Quantos cabem por linha sai da largura DISPONIVEL, e nao de um
        // breakpoint: o mesmo cartao serve o celular em pe (um), o celular
        // deitado (dois) e o tablet (tres), sem ninguem perguntar qual e qual.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val porLinha = ((maxWidth / 240.dp).toInt()).coerceIn(1, 3)

            Column(verticalArrangement = Arrangement.spacedBy(Espaco.e2)) {
                subs.chunked(porLinha).forEachIndexed { linha, fileira ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                    ) {
                        // A posicao vem da conta, e nao de um `indexOf`: dois
                        // cartoes recem-criados sao o MESMO valor para um data
                        // class, e o `indexOf` daria o primeiro para os dois.
                        fileira.forEachIndexed { coluna, estado ->
                            CartaoDeSub(
                                estado = estado,
                                indice = linha * porLinha + coluna,
                                vm = vm,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Sem isto, dois cartoes numa linha de tres esticariam
                        // para preencher o vazio e ficariam maiores que os de
                        // cima.
                        repeat(porLinha - fileira.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoDeSub(
    estado: EstadoDoSub,
    indice: Int,
    vm: SubsViewModel,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val aberto = !estado.dobrado

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Canto.caixa))
            .background(
                if (estado.correndo) {
                    cores.destaque.copy(alpha = 0.09f)
                } else {
                    cores.fundoCampo
                },
            )
            .border(
                1.dp,
                if (estado.correndo) cores.destaque.copy(alpha = 0.45f) else cores.traco,
                RoundedCornerShape(Canto.caixa),
            )
            .padding(Espaco.e2),
        verticalArrangement = Arrangement.spacedBy(Espaco.e1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
        ) {
            Text(
                text = nomeDoSub(estado.nome, indice),
                style = TipografiaBeazeth.titleMedium.copy(fontSize = 13.sp),
                color = cores.tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%02d:%02d".format(estado.restante / 60, estado.restante % 60),
                style = TipografiaBeazeth.headlineMedium.copy(fontSize = 19.sp),
                color = cores.tinta,
            )
            BotaoDesenhado(
                descricao = if (aberto) "Minimizar" else "Expandir",
                aoTocar = { vm.dobrar(estado.id, aberto) },
            ) {
                chevron(paraCima = aberto, cor = cores.tintaSuave)
            }
            BotaoDesenhado(descricao = "Remover", aoTocar = { vm.remover(estado.id) }) {
                xis(cor = cores.tintaSuave)
            }
        }

        // A barra continua visivel minimizado: e ela que faz o cartao fechado
        // ainda dizer quanto falta, sem ocupar linha nenhuma.
        Barra(progresso = estado.progresso, cores = cores)

        if (!aberto) return@Column

        Text(
            text = when {
                estado.emDescanso -> "Descanso 💗"
                estado.restante <= 0 && estado.fimEm > 0L -> "Tempo!"
                estado.correndo -> "Focando…"
                estado.restante < estado.total -> "Pausado"
                else -> "Pronto para começar"
            },
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
            color = cores.tintaSuave,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Tempo",
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                color = cores.tintaSuave,
            )
            Text(
                text = "${estado.escolhidos} min",
                style = TipografiaBeazeth.titleMedium.copy(fontSize = 13.sp),
                color = cores.tinta,
            )
        }

        Slider(
            value = estado.escolhidos.coerceAtMost(MINUTOS_NO_SLIDER).toFloat(),
            onValueChange = { vm.definirMinutos(estado.id, it.toInt()) },
            valueRange = 1f..MINUTOS_NO_SLIDER.toFloat(),
            // Trocar o tempo com a contagem correndo confundiria o que a barra
            // esta mostrando. O principal e o site tambem trancam.
            enabled = !estado.correndo,
            colors = SliderDefaults.colors(
                thumbColor = cores.destaque,
                activeTrackColor = cores.destaque,
                inactiveTrackColor = cores.destaque.copy(alpha = 0.22f),
            ),
        )

        BotaoPrimario(
            texto = when {
                estado.emDescanso -> "Pular descanso"
                estado.correndo -> "Pausar"
                else -> "Começar"
            },
            aoTocar = { vm.alternar(estado.id) },
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(999.dp))
                .clickable { vm.zerar(estado.id) }
                .padding(horizontal = Espaco.e3, vertical = Espaco.e1),
        ) {
            Text(
                text = "Zerar",
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                color = cores.tintaSuave,
            )
        }

        CampoDoce(
            rotulo = "Nome",
            valor = estado.nome,
            aoMudar = { vm.renomear(estado.id, it) },
            dica = nomeDoSub("", indice),
        )
    }
}

@Composable
private fun Barra(progresso: Float, cores: com.beazeth.notifier.ui.theme.Paleta) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(cores.destaque.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progresso.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(cores.destaque),
        )
    }
}

/**
 * O "+" que cria outro pomodoro.
 *
 * Desenhado, e nao a letra "+": e o mesmo motivo das tres barrinhas do menu --
 * o caractere muda de largura e de peso a cada uma das dez fontes do app, e um
 * botao redondo com um sinal descentralizado fica torto em metade delas.
 */
@Composable
private fun BotaoDeMais(habilitado: Boolean, aoTocar: () -> Unit) {
    val cores = Doce
    val opacidade = if (habilitado) 1f else 0.4f

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(cores.destaque.copy(alpha = opacidade))
            .clickable(enabled = habilitado, onClick = aoTocar)
            .drawBehind {
                val meio = size.width / 2f
                val braco = size.width * 0.22f
                val traco = 2.5.dp.toPx()
                drawLine(
                    color = cores.superficie.copy(alpha = opacidade),
                    start = Offset(meio - braco, meio),
                    end = Offset(meio + braco, meio),
                    strokeWidth = traco,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = cores.superficie.copy(alpha = opacidade),
                    start = Offset(meio, meio - braco),
                    end = Offset(meio, meio + braco),
                    strokeWidth = traco,
                    cap = StrokeCap.Round,
                )
            },
    )
}

/** Uma caixinha de toque de 28 dp com um desenho dentro. */
@Composable
private fun BotaoDesenhado(
    descricao: String,
    aoTocar: () -> Unit,
    desenho: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(Canto.caixa))
            .clickable(onClick = aoTocar)
            .drawBehind(desenho),
    )
}

/** A setinha do minimizar, apontando para onde o cartao vai. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.chevron(
    paraCima: Boolean,
    cor: androidx.compose.ui.graphics.Color,
) {
    val meio = size.width / 2f
    val braco = size.width * 0.19f
    val traco = 2.dp.toPx()
    val alto = meio - braco / 2f
    val baixo = meio + braco / 2f
    val ponta = if (paraCima) alto else baixo
    val base = if (paraCima) baixo else alto

    drawLine(cor, Offset(meio - braco, base), Offset(meio, ponta), traco, StrokeCap.Round)
    drawLine(cor, Offset(meio, ponta), Offset(meio + braco, base), traco, StrokeCap.Round)
}

/** O xis do remover. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.xis(
    cor: androidx.compose.ui.graphics.Color,
) {
    val meio = size.width / 2f
    val braco = size.width * 0.18f
    val traco = 2.dp.toPx()

    drawLine(
        cor, Offset(meio - braco, meio - braco), Offset(meio + braco, meio + braco),
        traco, StrokeCap.Round,
    )
    drawLine(
        cor, Offset(meio + braco, meio - braco), Offset(meio - braco, meio + braco),
        traco, StrokeCap.Round,
    )
}
