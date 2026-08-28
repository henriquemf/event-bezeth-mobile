package com.beazeth.notifier.ui.telas.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.ui.LocalModoLocal
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.telas.corDoHex
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * A agenda: a grade do mes e a lista de eventos.
 *
 * **As duas, e nao uma.** E o `calendar-layout` do site -- a grade a esquerda,
 * "Próximos eventos" na coluna ao lado. Aqui a divisao e a mesma quando ha
 * largura para ela (celular deitado, tablet) e vira uma coluna so quando nao
 * ha: a grade em cima, a lista embaixo, rolando junto.
 *
 * **Tocar num dia liga as duas.** Sem dia escolhido, a lista mostra tudo,
 * agrupado por dia, como antes. Com um dia escolhido, mostra so aquele -- e o
 * botao de criar ja nasce naquela data, que e o que o `dateClick` do site faz.
 * Tocar de novo no mesmo dia solta a selecao; sem isso, voltar a ver o mes
 * inteiro exigiria adivinhar onde fica o botao que desfaz.
 */
@Composable
fun CalendarioScreen(vm: CalendarioViewModel = viewModel()) {
    val eventos by vm.eventos.collectAsState()
    val tags by vm.tags.collectAsState()
    val hoje = remember { LocalDate.now() }

    // `YearMonth` e `LocalDate` nao entram num Bundle; o mes vira um inteiro e
    // o dia vira a propria ISO, que e o que ja se usa como chave do mapa.
    var mesEmNumero by rememberSaveable {
        mutableIntStateOf(hoje.year * 12 + hoje.monthValue - 1)
    }
    var diaEscolhido by rememberSaveable { mutableStateOf<String?>(null) }
    var emEdicao by remember { mutableStateOf<EventoEntity?>(null) }
    var criando by remember { mutableStateOf(false) }

    // ------------------------------------------------- arrastar para outro dia
    //
    // O evento nao tem duracao -- e um instante em `event_datetime` --, entao
    // nao ha o que "aumentar ou diminuir": largar num dia da grade so troca a
    // DATA e preserva a hora. E o `mode: "move"` do `drag.js` do planner do
    // site, sem os dois modos de redimensionar que ali fazem sentido.
    var arrastado by remember { mutableStateOf<EventoEntity?>(null) }
    var ponto by remember { mutableStateOf(Offset.Zero) }
    val areasDosDias = remember { mutableStateMapOf<String, Rect>() }

    // Qual dia esta sob o dedo AGORA. Funcao e nao valor de propriedade: quem
    // solta precisa da resposta no instante em que solta, e o gesto guarda a
    // funcao de soltar desde que comecou -- um valor calculado la atras estaria
    // sempre vazio. Foi exatamente esse o defeito: `aoSoltar` ficava preso na
    // versao criada antes do arrasto existir, e nenhum evento mudava de dia.
    fun alvoSob(evento: EventoEntity?, onde: Offset): LocalDate? {
        val hora = evento?.let { horaDe(it) } ?: return null
        return areasDosDias.entries
            .firstOrNull { it.value.contains(onde) }
            ?.key
            ?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
            // O servidor recusa evento no passado. Um dia que nao pode receber
            // nao acende, entao ninguem larga ali e fica sem entender por que
            // nada aconteceu.
            ?.takeIf { LocalDateTime.of(it, hora.toLocalTime()).isAfter(LocalDateTime.now()) }
    }

    val alvo: LocalDate? = alvoSob(arrastado, ponto)

    val soltar = {
        val evento = arrastado
        val destino = alvoSob(evento, ponto)
        arrastado = null
        val hora = evento?.let { horaDe(it) }
        if (evento != null && destino != null && hora != null && hora.toLocalDate() != destino) {
            vm.editar(
                evento,
                evento.title,
                evento.description,
                LocalDateTime.of(destino, hora.toLocalTime()).format(FORMATO_ENVIO),
                evento.tagType,
            )
        }
    }

    val mes = YearMonth.of(mesEmNumero / 12, mesEmNumero % 12 + 1)
    val selecionado = diaEscolhido?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // Agrupa por dia mantendo a ordem cronologica que o Room ja entregou.
    val porDia = eventos.groupBy { it.eventDatetime.take(10) }.toSortedMap()
    val visiveis = diaEscolhido?.let { dia ->
        porDia.filterKeys { it == dia }
    } ?: porDia

    val painel: @Composable () -> Unit = {
        CartaoDaTela {
            GradeDoMes(
                mes = mes,
                porDia = porDia,
                selecionado = selecionado,
                hoje = hoje,
                aoTrocarMes = { novo -> mesEmNumero = novo.year * 12 + novo.monthValue - 1 },
                aoEscolherDia = { data ->
                    val iso = data.toString()
                    diaEscolhido = if (diaEscolhido == iso) null else iso
                },
                diaAlvo = alvo,
                aoMedirDia = { data, limites -> areasDosDias[data.toString()] = limites },
            )

            if (selecionado != null) {
                Text(
                    text = DIAS_LONGOS[selecionado.dayOfWeek.value - 1] + ", " +
                        "${selecionado.dayOfMonth} de ${MESES[selecionado.monthValue - 1]}",
                    style = TipografiaBeazeth.bodyMedium,
                    color = Doce.tintaSuave,
                )
            }

            BotaoPrimario(
                texto = if (selecionado == null) {
                    "Novo evento"
                } else {
                    "Novo evento em %02d/%02d".format(
                        selecionado.dayOfMonth, selecionado.monthValue,
                    )
                },
                aoTocar = { criando = true },
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A partir daqui cabem duas colunas de largura util. Abaixo disso, a
        // grade ao lado da lista deixaria as duas estreitas demais para as
        // duas coisas -- pior que empilhar.
        val emDuasColunas = maxWidth >= 620.dp

        if (emDuasColunas) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = Espaco.e5, end = Espaco.e2,
                            top = Espaco.e2, bottom = Espaco.e5,
                        ),
                ) {
                    painel()
                }
                ListaDeEventos(
                    visiveis = visiveis,
                    hoje = hoje,
                    diaEscolhido = diaEscolhido,
                    arrastado = arrastado,
                    aoEditar = { emEdicao = it },
                    aoApagar = { vm.apagar(it) },
                    aoPegar = { evento, onde -> arrastado = evento; ponto = onde },
                    aoMover = { ponto += it },
                    aoSoltar = soltar,
                    aoDesistir = { arrastado = null },
                    modifier = Modifier.weight(1f),
                    recuoInicial = Espaco.e2,
                )
            }
        } else {
            ListaDeEventos(
                visiveis = visiveis,
                hoje = hoje,
                diaEscolhido = diaEscolhido,
                arrastado = arrastado,
                aoEditar = { emEdicao = it },
                aoApagar = { vm.apagar(it) },
                aoPegar = { evento, onde -> arrastado = evento; ponto = onde },
                aoMover = { ponto += it },
                aoSoltar = soltar,
                aoDesistir = { arrastado = null },
                modifier = Modifier.fillMaxSize(),
                recuoInicial = Espaco.e5,
                cabecalho = painel,
            )
        }
    }

    arrastado?.let { evento ->
        FantasmaDoEvento(evento = evento, ponto = ponto)
    }

    if (criando) {
        FormularioDeEvento(
            evento = null,
            dataInicial = selecionado,
            tags = tags,
            aoFechar = { criando = false },
            aoSalvar = { titulo, descricao, quando, tag ->
                vm.criar(titulo, descricao, quando, tag)
                criando = false
            },
        )
    }

    emEdicao?.let { alvo ->
        FormularioDeEvento(
            evento = alvo,
            dataInicial = null,
            tags = tags,
            aoFechar = { emEdicao = null },
            aoSalvar = { titulo, descricao, quando, tag ->
                vm.editar(alvo, titulo, descricao, quando, tag)
                emEdicao = null
            },
        )
    }
}

@Composable
private fun ListaDeEventos(
    visiveis: Map<String, List<EventoEntity>>,
    hoje: LocalDate,
    diaEscolhido: String?,
    arrastado: EventoEntity?,
    aoEditar: (EventoEntity) -> Unit,
    aoApagar: (EventoEntity) -> Unit,
    aoPegar: (EventoEntity, Offset) -> Unit,
    aoMover: (Offset) -> Unit,
    aoSoltar: () -> Unit,
    aoDesistir: () -> Unit,
    modifier: Modifier = Modifier,
    recuoInicial: Dp = Espaco.e5,
    cabecalho: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = recuoInicial, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
        ),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        if (cabecalho != null) {
            item { cabecalho() }
        }

        if (visiveis.isEmpty()) {
            item {
                CartaoDaTela {
                    Text(
                        text = if (diaEscolhido == null) {
                            "Nenhum evento por aqui."
                        } else {
                            "Nada marcado neste dia."
                        },
                        style = TipografiaBeazeth.bodyLarge,
                        color = Doce.tintaSuave,
                    )
                    Text(
                        text = if (diaEscolhido == null) {
                            "Crie um acima, ou espere a sincronização trazer os que você " +
                                "criou no site."
                        } else {
                            "Toque no dia de novo para ver o mês inteiro."
                        },
                        style = TipografiaBeazeth.bodyMedium,
                        color = Doce.tintaSuave,
                    )
                }
            }
        }

        for ((iso, doDia) in visiveis) {
            item(key = iso) {
                CartaoDaTela {
                    CabecalhoDoDia(iso = iso, hoje = hoje)
                    for (evento in doDia) {
                        LinhaDeEvento(
                            evento = evento,
                            sendoArrastado = evento.id == arrastado?.id,
                            aoTocar = { aoEditar(evento) },
                            aoApagar = { aoApagar(evento) },
                            aoPegar = aoPegar,
                            aoMover = aoMover,
                            aoSoltar = aoSoltar,
                            aoDesistir = aoDesistir,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDoDia(iso: String, hoje: LocalDate) {
    val data = runCatching { LocalDate.parse(iso) }.getOrNull()
    val ehHoje = data == hoje

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        Text(
            text = data?.let { "%02d".format(it.dayOfMonth) } ?: "—",
            style = TipografiaBeazeth.headlineMedium.copy(fontSize = 26.sp),
            color = if (ehHoje) Doce.destaqueEscuro else Doce.tinta,
        )
        Column {
            Text(
                text = data?.let { "${MESES_CURTOS[it.monthValue - 1]} ${it.year}" } ?: iso,
                style = TipografiaBeazeth.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Doce.tintaSuave,
            )
            Text(
                text = if (ehHoje) {
                    "Hoje"
                } else {
                    data?.let { DIAS_LONGOS[it.dayOfWeek.value - 1] }.orEmpty()
                },
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                color = if (ehHoje) Doce.destaqueEscuro else Doce.tintaSuave,
            )
        }
    }
}

@Composable
private fun LinhaDeEvento(
    evento: EventoEntity,
    sendoArrastado: Boolean,
    aoTocar: () -> Unit,
    aoApagar: () -> Unit,
    aoPegar: (EventoEntity, Offset) -> Unit,
    aoMover: (Offset) -> Unit,
    aoSoltar: () -> Unit,
    aoDesistir: () -> Unit,
) {
    val hora = runCatching {
        LocalDateTime.parse(evento.eventDatetime).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("--:--")
    val tato = LocalHapticFeedback.current

    // Onde esta linha esta NA JANELA. O dedo comeca aqui e termina noutro
    // pedaco da arvore (a grade), entao os dois lados precisam falar a mesma
    // lingua de coordenadas.
    var lugar by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { lugar = it }
            .clip(RoundedCornerShape(12.dp))
            .background(corDoHex(evento.tagColor).copy(alpha = if (sendoArrastado) 0.05f else 0.14f))
            .clickable(onClick = aoTocar)
            // Segurar e arrastar leva o evento para outro dia. Segurar, e nao
            // arrastar direto: a lista rola no mesmo eixo, e um arrasto simples
            // seria ambiguo entre "rolar" e "mover o evento".
            .pointerInput(evento.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        tato.performHapticFeedback(HapticFeedbackType.LongPress)
                        aoPegar(evento, lugar?.localToWindow(local) ?: Offset.Zero)
                    },
                    onDragEnd = { aoSoltar() },
                    onDragCancel = { aoDesistir() },
                ) { mudanca, deslocamento ->
                    mudanca.consume()
                    aoMover(deslocamento)
                }
            }
            .padding(Espaco.e2),
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = hora,
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 14.sp),
            color = Doce.tinta,
            modifier = Modifier.padding(top = 2.dp),
        )

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(corDoHex(evento.tagColor)),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = evento.title,
                style = TipografiaBeazeth.bodyLarge.copy(fontSize = 15.sp),
                color = Doce.tinta,
            )
            if (evento.description.isNotBlank()) {
                Text(
                    text = evento.description,
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp),
                    color = Doce.tintaSuave,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = evento.tagLabel,
                    style = TipografiaBeazeth.bodyMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Doce.tintaSuave,
                )
                // Ver o mesmo selo em `Postit`: sem conta ele nao diz nada.
                if (evento.id < 0 && !LocalModoLocal.current) {
                    Text(
                        text = "não enviado",
                        style = TipografiaBeazeth.bodyMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Doce.tintaSuave,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = aoApagar),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", fontSize = 18.sp, color = Doce.tintaSuave)
        }
    }
}

/** A hora do evento, ou nulo se o texto do servidor vier estranho. */
private fun horaDe(evento: EventoEntity): LocalDateTime? =
    runCatching { LocalDateTime.parse(evento.eventDatetime) }.getOrNull()

/**
 * A copia que segue o dedo durante o arrasto.
 *
 * `Popup` porque ela precisa sair de dentro da lista -- que rola e recorta -- e
 * passar por cima da grade do mes, que e justamente o destino. E o `Popup` ja
 * pensa em coordenadas de janela, as mesmas em que o dedo e as celulas foram
 * medidos: nao ha conversao nenhuma pelo caminho.
 */
@Composable
private fun FantasmaDoEvento(evento: EventoEntity, ponto: Offset) {
    val cor = corDoHex(evento.tagColor)
    Popup(
        popupPositionProvider = remember(ponto) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(
                    (ponto.x - popupContentSize.width / 2f).roundToInt(),
                    (ponto.y - popupContentSize.height / 2f).roundToInt(),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Doce.superficie)
                .border(1.dp, cor, RoundedCornerShape(12.dp))
                .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(cor),
            )
            Text(
                text = evento.title,
                style = TipografiaBeazeth.bodyLarge.copy(fontSize = 14.sp),
                color = Doce.tinta,
            )
        }
    }
}
