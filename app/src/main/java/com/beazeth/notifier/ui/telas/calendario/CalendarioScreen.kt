package com.beazeth.notifier.ui.telas.calendario

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.theme.Espaco
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * A agenda: a grade do mes e a lista de eventos.
 *
 * **As duas, e nao uma.** E o `calendar-layout` do site -- a grade e, ao lado
 * ou embaixo dela, "Próximos eventos".
 *
 * **Quando cada uma das duas formas.** O site so poe a lista ao lado a partir
 * de 1400 px de janela: abaixo disso o mes ficaria com 566 px, e a barra de
 * controles do FullCalendar -- prev, next, hoje e os tres botoes de visao --
 * quebra em duas linhas nessa largura. A barra daqui tem tres botoes e um
 * titulo, entao esse limite nao se aplica, e o corte pode ser bem mais baixo:
 * [LARGURA_PARA_DUAS_COLUNAS], medida na area de conteudo.
 *
 * No tablet isso da duas colunas, e e o que se quer: o mes inteiro cabe na
 * tela com celulas de ~85x75 dp, e "Próximos eventos" fica sempre a vista, em
 * vez de morar abaixo da dobra esperando alguem rolar ate ele. Uma lista do
 * que vem por ai que so aparece quando se rola nao lembra ninguem de nada.
 *
 * O corte ja foi 620 dp, de quando a pergunta era so "cabem duas colunas?".
 * Cabiam -- com celulas de 44 dp, uma grade de brinquedo ao lado de um cartao
 * quase vazio. O que mudou nao foi o numero de colunas, foi o tamanho do mes.
 *
 * **Tocar num dia mira a grade, nao a lista.** O dia escolhido acende na grade
 * e o botao de criar ja nasce naquela data, que e o que o `dateClick` do site
 * faz. Tocar de novo no mesmo dia solta a selecao.
 *
 * A lista NAO acompanha: ela mostra sempre de hoje para a frente. Chegou a
 * filtrar pelo dia escolhido, e o resultado era que a unica coisa da tela que
 * responde "o que vem por ai" desaparecia justamente quando se tocava no
 * calendario para conferir uma data.
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

    // "Proximos eventos" comeca sempre em HOJE, e nao no dia escolhido na
    // grade. Escolher um dia mudava a lista para so aquele dia, e o efeito era
    // que a unica coisa que diz o que vem por ai sumia justamente quando se
    // tocava no calendario para conferir uma data. Agora escolher um dia so
    // mexe na grade e no botao de criar; a lista fica.
    //
    // Comparacao de texto e nao de data: a chave e ISO (`2026-08-28`), e nesse
    // formato a ordem alfabetica E a ordem cronologica.
    val hojeIso = hoje.toString()
    val visiveis = porDia.filterKeys { it >= hojeIso }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val emDuasColunas = maxWidth >= LARGURA_PARA_DUAS_COLUNAS
        val alturaDasSemanas = maxHeight - ENFEITES_DA_GRADE

        val grade: @Composable () -> Unit = {
            CartaoDaTela {
                GradeDoMes(
                    mes = mes,
                    porDia = porDia,
                    selecionado = selecionado,
                    hoje = hoje,
                    aoTrocarMes = { novo ->
                        mesEmNumero = novo.year * 12 + novo.monthValue - 1
                    },
                    aoEscolherDia = { data ->
                        val iso = data.toString()
                        diaEscolhido = if (diaEscolhido == iso) null else iso
                    },
                    aoTocarEvento = { emEdicao = it },
                    aoCriar = { criando = true },
                    diaAlvo = alvo,
                    alturaDasSemanas = alturaDasSemanas,
                    aoMedirDia = { data, limites -> areasDosDias[data.toString()] = limites },
                )
            }
        }

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
                    grade()
                }
                // Largura fixa, como o `340px` do site: o que sobra e todo do
                // mes, que e quem precisa de espaco para os dias nao espremerem.
                ListaDeEventos(
                    visiveis = visiveis,
                    hoje = hoje,
                    arrastado = arrastado,
                    aoEditar = { emEdicao = it },
                    aoApagar = { vm.apagar(it) },
                    aoPegar = { evento, onde -> arrastado = evento; ponto = onde },
                    aoMover = { ponto += it },
                    aoSoltar = soltar,
                    aoDesistir = { arrastado = null },
                    modifier = Modifier.width(LARGURA_DA_COLUNA_DE_EVENTOS),
                    recuoInicial = Espaco.e2,
                )
            }
        } else {
            ListaDeEventos(
                visiveis = visiveis,
                hoje = hoje,
                arrastado = arrastado,
                aoEditar = { emEdicao = it },
                aoApagar = { vm.apagar(it) },
                aoPegar = { evento, onde -> arrastado = evento; ponto = onde },
                aoMover = { ponto += it },
                aoSoltar = soltar,
                aoDesistir = { arrastado = null },
                modifier = Modifier.fillMaxSize(),
                recuoInicial = Espaco.e5,
                cabecalho = grade,
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

    emEdicao?.let { emFoco ->
        FormularioDeEvento(
            evento = emFoco,
            dataInicial = null,
            tags = tags,
            aoFechar = { emEdicao = null },
            aoSalvar = { titulo, descricao, quando, tag ->
                vm.editar(emFoco, titulo, descricao, quando, tag)
                emEdicao = null
            },
        )
    }
}

/** A hora do evento, ou nulo se o texto do servidor vier estranho. */
private fun horaDe(evento: EventoEntity): LocalDateTime? =
    runCatching { LocalDateTime.parse(evento.eventDatetime) }.getOrNull()

/**
 * A partir daqui a lista cabe ao lado do mes sem espremer nenhum dos dois.
 *
 * Medida na area de CONTEUDO, e nao na janela: e ela que a grade tem para si. O
 * corte equivalente do site e 1400 px de janela, dos quais a barra lateral come
 * uns 300.
 */
private val LARGURA_PARA_DUAS_COLUNAS = 900.dp

/** O `340px` da coluna de "Próximos eventos" do site. */
private val LARGURA_DA_COLUNA_DE_EVENTOS = 340.dp

/**
 * Tudo o que ocupa altura nesta tela menos as semanas: o recuo, o cartao, a
 * barra do mes e a linha com os nomes dos dias.
 *
 * Serve para a grade saber quanto sobra e caber o mes inteiro sem rolagem.
 * Estimativa de proposito -- medir de verdade custaria uma volta de composicao
 * so para descobrir a altura de dois cabecalhos, e errar por alguns dp so faz a
 * celula sair um fio mais alta ou mais baixa.
 */
private val ENFEITES_DA_GRADE = 176.dp
