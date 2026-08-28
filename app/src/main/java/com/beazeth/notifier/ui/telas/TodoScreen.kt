package com.beazeth.notifier.ui.telas

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.TarefaEntity
import com.beazeth.notifier.ui.componentes.CampoDoce
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.janelaDeitada
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * A semana de tarefas, como a pagina `/todo` do site.
 *
 * A referencia visual la e agenda de papel: numero do dia grande a esquerda,
 * linha tracejada separando os dias, caixinhas a direita, e uma estrela quando
 * o dia inteiro esta concluido.
 *
 * **A semana e UM bloco, e nao sete.** A primeira versao dava um cartao por
 * dia, e o resultado eram sete caixas flutuando com sombra propria -- a semana
 * deixava de parecer uma pagina e virava uma pilha. No site e um `panel-card`
 * so, com os dias separados por `border-bottom: 1px dashed`, e e isso que esta
 * aqui: a folha e uma, os dias sao linhas dela.
 */

private val MESES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)
private val DIAS_CURTOS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

/** Um dia da semana ja montado para a tela. */
data class DiaDaSemana(
    val data: LocalDate,
    val iso: String,
    val numero: String,
    val curto: String,
    val hoje: Boolean,
    val fimDeSemana: Boolean,
    val itens: List<TarefaEntity>,
) {
    val feitas: Int get() = itens.count { it.done }

    /** A estrela so acende com o dia inteiro feito -- e nunca no dia vazio,
     *  senao todo dia sem tarefa nasceria premiado. */
    val estrelado: Boolean get() = itens.isNotEmpty() && feitas == itens.size
}

class TodoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    /** A segunda-feira da semana em exibicao. */
    private val _segunda = MutableStateFlow(segundaDe(LocalDate.now()))
    val segunda = _segunda

    @OptIn(ExperimentalCoroutinesApi::class)
    val itens = _segunda
        .flatMapLatest { inicio ->
            repo.tarefas(inicio.toString(), inicio.plusDays(6).toString())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun semanaAnterior() { _segunda.value = _segunda.value.minusWeeks(1) }
    fun proximaSemana() { _segunda.value = _segunda.value.plusWeeks(1) }
    fun voltarParaHoje() { _segunda.value = segundaDe(LocalDate.now()) }

    fun criar(dia: String, texto: String) = viewModelScope.launch {
        repo.criarTarefa(dia, texto)
    }

    fun alternar(item: TarefaEntity) = viewModelScope.launch { repo.alternarTarefa(item) }

    fun editar(item: TarefaEntity, texto: String) = viewModelScope.launch {
        repo.editarTarefa(item, texto)
    }

    fun apagar(item: TarefaEntity) = viewModelScope.launch { repo.apagarTarefa(item.id) }

    private fun segundaDe(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
}

@Composable
fun TodoScreen(vm: TodoViewModel = viewModel()) {
    val segunda by vm.segunda.collectAsState()
    val itens by vm.itens.collectAsState()

    val domingo = segunda.plusDays(6)
    val hoje = LocalDate.now()
    val porDia = itens.groupBy { it.day }

    val dias = (0L..6L).map { deslocamento ->
        val data = segunda.plusDays(deslocamento)
        val iso = data.toString()
        DiaDaSemana(
            data = data,
            iso = iso,
            numero = "%02d".format(data.dayOfMonth),
            curto = DIAS_CURTOS[deslocamento.toInt()],
            hoje = data == hoje,
            fimDeSemana = deslocamento >= 5,
            itens = porDia[iso].orEmpty().sortedWith(compareBy({ it.position }, { it.id })),
        )
    }

    val total = itens.size
    val feitas = itens.count { it.done }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Espaco.e5, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
        ),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        item {
            CabecalhoDaSemana(
                segunda = segunda,
                domingo = domingo,
                feitas = feitas,
                total = total,
                naSemanaDeHoje = segunda == hoje.minusDays((hoje.dayOfWeek.value - 1).toLong()),
                aoAnterior = vm::semanaAnterior,
                aoHoje = vm::voltarParaHoje,
                aoProxima = vm::proximaSemana,
            )
        }

        // A semana inteira num item so. Sete linhas nao pesam, e o ganho e a
        // folha unica -- que e o ponto.
        item {
            CartaoDaTela {
                dias.forEachIndexed { indice, dia ->
                    LinhaDoDia(
                        dia = dia,
                        aoCriar = { texto -> vm.criar(dia.iso, texto) },
                        aoAlternar = vm::alternar,
                        aoEditar = vm::editar,
                        aoApagar = vm::apagar,
                    )
                    // Tracejado entre os dias, e nao depois do ultimo: a ultima
                    // linha ja tem a borda do cartao logo abaixo.
                    if (indice < dias.lastIndex) {
                        DivisorTracejado()
                    }
                }
            }
        }
    }
}

/** O `border-bottom: 1px dashed` que separa os dias no site. */
@Composable
private fun DivisorTracejado() {
    val cor = Doce.traco.copy(alpha = Doce.traco.alpha * 0.8f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = cor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                    ),
                )
            },
    )
}

@Composable
private fun CabecalhoDaSemana(
    segunda: LocalDate,
    domingo: LocalDate,
    feitas: Int,
    total: Int,
    naSemanaDeHoje: Boolean,
    aoAnterior: () -> Unit,
    aoHoje: () -> Unit,
    aoProxima: () -> Unit,
) {
    // Semana que cruza o mes (ou o ano) precisa dizer os dois, senao o titulo
    // mente na metade dos dias que estao logo abaixo dele. Mesma regra do
    // `build_header` no servidor.
    val mes = if (segunda.monthValue == domingo.monthValue) {
        MESES[segunda.monthValue - 1]
    } else {
        "${MESES[segunda.monthValue - 1]} – ${MESES[domingo.monthValue - 1]}"
    }
    val ano = if (segunda.year == domingo.year) "${segunda.year}" else "${segunda.year}/${domingo.year}"
    val numeroDaSemana = segunda.get(WeekFields.ISO.weekOfWeekBasedYear())

    val titulo: @Composable () -> Unit = {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = mes,
                    style = TipografiaBeazeth.headlineMedium,
                    color = Doce.tinta,
                )
                Text(
                    text = " $ano",
                    style = TipografiaBeazeth.headlineMedium.copy(fontSize = 18.sp),
                    color = Doce.tintaSuave,
                )
            }
            Text(
                text = "SEMANA $numeroDaSemana · ${segunda.dayOfMonth} → ${domingo.dayOfMonth}",
                style = TipografiaBeazeth.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                ),
                color = Doce.tintaSuave,
            )
        }
    }

    // A folga com `weight` mora aqui dentro para empurrar o placar para a
    // direita nas duas montagens -- em pe ele fica na ponta da linha de baixo,
    // deitado na ponta da linha unica.
    val controles: @Composable RowScope.() -> Unit = {
        BotaoDaSemana("‹", aoAnterior)
        BotaoDaSemana("Hoje", aoHoje, destacado = naSemanaDeHoje, largo = true)
        BotaoDaSemana("›", aoProxima)
        Box(modifier = Modifier.weight(1f))
        Placar(feitas = feitas, total = total)
    }

    CartaoDaTela {
        // Deitado o cabecalho vira uma linha so. Empilhado ele tem tres, e
        // tres linhas de cabecalho num aparelho de 411 dp de altura sao meia
        // tela gasta antes da primeira tarefa -- que e o que se veio ver.
        if (janelaDeitada()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
            ) {
                titulo()
                controles()
            }
        } else {
            titulo()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
            ) {
                controles()
            }
        }
    }
}

@Composable
private fun BotaoDaSemana(
    texto: String,
    aoTocar: () -> Unit,
    destacado: Boolean = false,
    largo: Boolean = false,
) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .then(if (largo) Modifier else Modifier.width(38.dp))
            .clip(RoundedCornerShape(Canto.botao))
            .background(
                if (destacado) Doce.destaque.copy(alpha = 0.20f) else Doce.fundoCampo
            )
            .border(
                1.dp,
                if (destacado) Doce.destaque else Doce.traco,
                RoundedCornerShape(Canto.botao),
            )
            .clickable(onClick = aoTocar)
            .padding(horizontal = if (largo) Espaco.e3 else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = TipografiaBeazeth.titleMedium,
            color = Doce.tinta,
        )
    }
}

/** O `.todo-score`: numero grande, barra de progresso e rotulo. */
@Composable
private fun Placar(feitas: Int, total: Int) {
    val progresso by animateFloatAsState(
        targetValue = if (total > 0) feitas.toFloat() / total else 0f,
        label = "progresso",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Canto.caixa))
            .background(
                Brush.linearGradient(
                    listOf(
                        Doce.destaque.copy(alpha = 0.14f),
                        Doce.azulSuave.copy(alpha = 0.10f),
                    )
                )
            )
            .border(1.dp, Doce.destaque.copy(alpha = 0.30f), RoundedCornerShape(Canto.caixa))
            .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$feitas",
                style = TipografiaBeazeth.headlineMedium.copy(fontSize = 24.sp),
                color = Doce.tinta,
            )
            Text(
                text = "/$total",
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp),
                color = Doce.tintaSuave,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Doce.destaque.copy(alpha = 0.20f)),
            ) {
                // `graphicsLayer` com escala, e nao largura: a barra anda no
                // compositor, sem passar por layout -- igual ao `scaleX` do site.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .graphicsLayer {
                            scaleX = progresso
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Doce.destaque, Doce.destaqueClaro)
                            )
                        ),
                )
            }
            Text(
                text = if (feitas == 1) "feita" else "feitas",
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 10.sp),
                color = Doce.tintaSuave,
            )
        }
    }
}

@Composable
private fun LinhaDoDia(
    dia: DiaDaSemana,
    aoCriar: (String) -> Unit,
    aoAlternar: (TarefaEntity) -> Unit,
    aoEditar: (TarefaEntity, String) -> Unit,
    aoApagar: (TarefaEntity) -> Unit,
) {
    var novo by remember(dia.iso) { mutableStateOf("") }

    val cores = Doce

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Sabado e domingo ganham um fundo de leve, como o sombreado do fim
            // de semana numa agenda de papel.
            .then(
                if (dia.fimDeSemana) {
                    Modifier.background(cores.destaque.copy(alpha = 0.05f))
                } else {
                    Modifier
                }
            )
            .drawBehind {
                // A margem: a linha vertical que numa agenda separa a coluna do
                // dia do espaco de escrever. Uma linha so, e e ela que faz a
                // pagina parecer pautada em vez de uma lista qualquer.
                val x = 52.dp.toPx()
                drawLine(
                    color = cores.traco,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = Espaco.e2),
        horizontalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        // Coluna da data: numero, sigla embaixo, estrela quando o dia esta
        // inteiro concluido. Quadrada, e nao redonda: e uma casa de agenda de
        // papel, nao um avatar.
        Column(
            modifier = Modifier.width(46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Canto.marca))
                    .background(
                        if (dia.hoje) Doce.destaque.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .border(
                        width = if (dia.hoje) 2.dp else 0.dp,
                        color = if (dia.hoje) Doce.destaque else Color.Transparent,
                        shape = RoundedCornerShape(Canto.marca),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dia.numero,
                    style = TipografiaBeazeth.headlineMedium.copy(fontSize = 21.sp),
                    color = if (dia.hoje) Doce.destaqueEscuro else Doce.tinta,
                )
            }
            Text(
                text = dia.curto.uppercase(),
                style = TipografiaBeazeth.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                ),
                color = if (dia.fimDeSemana) Doce.destaqueEscuro else Doce.tintaSuave,
            )
            if (dia.estrelado) {
                Text(text = "★", fontSize = 13.sp, color = Doce.destaque)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Espaco.e1),
        ) {
            for (item in dia.itens) {
                ItemDaTarefa(
                    item = item,
                    aoAlternar = { aoAlternar(item) },
                    aoEditar = { aoEditar(item, it) },
                    aoApagar = { aoApagar(item) },
                )
            }

            CampoDoce(
                rotulo = "",
                valor = novo,
                aoMudar = { novo = it },
                opcoesDoTeclado = KeyboardOptions(imeAction = ImeAction.Done),
                acoesDoTeclado = KeyboardActions(
                    onDone = {
                        if (novo.isNotBlank()) {
                            aoCriar(novo)
                            novo = ""
                        }
                    }
                ),
                dica = "Nova tarefa…",
                sublinhado = true,
            )
        }
    }
}

@Composable
private fun ItemDaTarefa(
    item: TarefaEntity,
    aoAlternar: () -> Unit,
    aoEditar: (String) -> Unit,
    aoApagar: () -> Unit,
) {
    val cores = Doce
    val foco = LocalFocusManager.current

    // O texto e um campo, nao um rotulo -- como no site, onde cada tarefa e um
    // `<input class="todo-text">` que salva ao sair. Antes so dava para marcar
    // e apagar: corrigir um "compar pao" exigia apagar a linha e escrever de
    // novo.
    var texto by remember(item.id, item.content) { mutableStateOf(item.content) }
    var focado by remember(item.id) { mutableStateOf(false) }

    val salvar = {
        val limpo = texto.trim()
        if (limpo.isEmpty()) {
            // Campo vazio nao apaga a tarefa: quem quer apagar tem o x ao lado,
            // e apagar por distracao seria perder o texto sem aviso.
            texto = item.content
        } else if (limpo != item.content) {
            aoEditar(limpo)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Canto.caixa))
            .drawBehind {
                // A pauta sob a tarefa: e o que faz o texto parecer escrito na
                // linha, e nao empilhado numa lista.
                drawLine(
                    color = cores.traco.copy(alpha = cores.traco.alpha * 0.7f),
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        // A caixinha desenhada tem 19 dp -- as medidas do `.todo-box` --, mas
        // o alvo de toque tem 34: era a linha inteira que alternava, e ela
        // deixou de alternar quando o texto virou campo. Dezenove dp de alvo
        // no polegar seria trocar um problema por outro.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Canto.caixa))
                .clickable(onClick = aoAlternar),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(RoundedCornerShape(Canto.marca))
                    .background(if (item.done) Doce.destaque else Doce.fundoCampo)
                    .border(
                        2.dp,
                        if (item.done) Doce.destaque else Doce.tintaSuave.copy(alpha = 0.55f),
                        RoundedCornerShape(Canto.marca),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) {
                    Text(text = "✓", fontSize = 12.sp, color = Color.White)
                }
            }
        }

        BasicTextField(
            value = texto,
            onValueChange = { texto = it },
            singleLine = true,
            textStyle = TipografiaBeazeth.bodyLarge.copy(
                fontSize = 15.sp,
                color = if (item.done) Doce.tintaSuave else Doce.tinta,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(Doce.destaque),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                salvar()
                foco.clearFocus()
            }),
            modifier = Modifier
                .weight(1f)
                // Salva ao sair do campo, como o `blur` do site. Sem isto,
                // tocar noutra tarefa perderia a edicao da anterior sem avisar.
                .onFocusChanged {
                    if (focado && !it.isFocused) salvar()
                    focado = it.isFocused
                },
        )

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(Canto.marca))
                .clickable(onClick = aoApagar),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", fontSize = 18.sp, color = Doce.tintaSuave)
        }
    }
}
