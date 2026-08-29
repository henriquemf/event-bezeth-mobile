package com.beazeth.notifier.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.beazeth.notifier.sync.SyncWorker
import com.beazeth.notifier.ui.componentes.BarraInferior
import com.beazeth.notifier.ui.componentes.BarraLateral
import com.beazeth.notifier.ui.componentes.BarraSuperior
import com.beazeth.notifier.ui.componentes.Destino
import com.beazeth.notifier.ui.telas.AguaScreen
import com.beazeth.notifier.ui.telas.AparenciaScreen
import com.beazeth.notifier.ui.telas.PomodoroScreen
import com.beazeth.notifier.ui.telas.TodoScreen
import com.beazeth.notifier.ui.telas.calendario.CalendarioScreen
import com.beazeth.notifier.ui.telas.planner.PlannerScreen
import com.beazeth.notifier.ui.telas.postits.PostitsScreen
import kotlin.math.roundToInt

/**
 * A casca de quem ja entrou: topo, tela e navegacao.
 *
 * Nenhuma tela daqui espera a rede. Todas leem do Room, que a sincronizacao
 * preenche por baixo -- e por isso trocar de aba e instantaneo, em vez de uma
 * ida ao Oregon como era no site embrulhado.
 *
 * ## Duas navegacoes, uma por formato
 *
 * **Onde cabe, a lateral do site.** O app roda num tablet de 11 polegadas, e
 * ali ha largura para a mesma barra lateral da versao web -- com marca, conta,
 * menu, pomodoro e agua, exatamente como em `partials/sidebar.html`. As tres
 * barrinhas a escondem e a trazem de volta, para quem quiser a tela inteira.
 *
 * **Onde nao cabe, barra embaixo**, no alcance do polegar -- a `.bottom-nav` do
 * site, que existe la pelo mesmo motivo: abaixo de certa largura a lateral
 * deixa de ser lateral e vira um bloco em cima do conteudo.
 *
 * ## A barra de cima recolhe
 *
 * Rolar para baixo empurra a barra para fora; rolar para cima a traz de volta.
 * A conta e feita por um `NestedScrollConnection` daqui, entao vale para
 * qualquer tela que role la dentro -- nenhuma delas precisa saber que existe
 * uma barra. Trocar de aba devolve a barra, senao a tela nova nasceria com o
 * titulo escondido sem nada para rolar de volta.
 */
@Composable
fun CascaApp(
    nome: String,
    escuro: Boolean,
    aoAlternarEscuro: (Boolean) -> Unit,
    aoSair: () -> Unit,
    local: Boolean = false,
) {
    val contexto = LocalContext.current
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val atual = Destino.porRota(entrada?.destination?.route) ?: Destino.POSTITS
    var lateralAberta by rememberSaveable { mutableStateOf(true) }

    // Altura natural da barra, e quanto dela ja saiu de cena (0 = aberta,
    // -altura = escondida).
    var alturaDaBarra by remember { mutableIntStateOf(0) }
    var recolhida by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(local) {
        // Ao abrir: puxa o que mudou desde a ultima vez e deixa a rede de
        // seguranca de hora em hora armada. Nenhum dos dois bloqueia a tela.
        //
        // Sem conta nao ha o que puxar nem para onde mandar. Agendar mesmo assim
        // acordaria o aparelho de hora em hora para uma tentativa que so pode
        // falhar -- bateria gasta para nada, num app que a pessoa escolheu usar
        // justamente sem depender de nada.
        if (!local) {
            SyncWorker.agora(contexto)
            SyncWorker.periodico(contexto)
        }
    }

    LaunchedEffect(atual) { recolhida = 0f }

    val conexao = remember {
        object : NestedScrollConnection {
            // Nao consome nada: a lista rola normalmente, e a barra so
            // acompanha. Consumir aqui faria o primeiro dedo de rolagem nao
            // mover a lista, que e a sensacao de tela travada.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                recolhida = (recolhida + available.y).coerceIn(-alturaDaBarra.toFloat(), 0f)
                return Offset.Zero
            }
        }
    }

    CompositionLocalProvider(LocalModoLocal provides local) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(conexao),
    ) {
    // Abaixo disto a lateral tomaria mais de um terco da largura, e o conteudo
    // -- a grade da semana, a do mes -- ficaria mais apertado do que o menu que
    // leva ate ele. Lido aqui fora: dentro da `Column` o `maxWidth` que vale e
    // o do escopo dela, e nao o da janela.
    val lateralCabe = maxWidth >= LARGURA_MINIMA_PARA_LATERAL

    // A coluna externa existe para o rodape e a barra de baixo ficarem ABAIXO
    // de tudo, lateral inclusive -- a mesma ordem do `base.html`, em que os dois
    // sao irmaos da casca e nao filhos dela.
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            if (lateralCabe && lateralAberta) {
                BarraLateral(
                    nome = nome,
                    atual = atual,
                    escuro = escuro,
                    aoAlternarEscuro = aoAlternarEscuro,
                    aoTrocar = { nav.irPara(it) },
                    aoSair = aoSair,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
            BarraSuperior(
                titulo = atual.titulo,
                escuro = escuro,
                aoAlternarEscuro = aoAlternarEscuro,
                aoAbrirAparencia = { nav.irPara(Destino.APARENCIA) },
                // As tres barrinhas so existem onde ha lateral para esconder.
                // Com a barra de baixo na tela, um segundo menu para a mesma
                // coisa e como o app comeca a ficar confuso.
                aoAbrirMenu = if (lateralCabe) ({ lateralAberta = !lateralAberta }) else null,
                modifier = Modifier
                    // Encolhe a ALTURA MEDIDA junto com o deslocamento, e nao
                    // so desloca: deslocar sozinho deixaria uma faixa vazia do
                    // tamanho da barra entre o topo e o conteudo.
                    .layout { medivel, restricoes ->
                        val posto = medivel.measure(restricoes)
                        val altura = (posto.height + recolhida).roundToInt().coerceAtLeast(0)
                        layout(posto.width, altura) {
                            posto.place(0, recolhida.roundToInt())
                        }
                    }
                    .onSizeChanged { alturaDaBarra = it.height }
                    .statusBarsPadding(),
            )

            NavHost(
                navController = nav,
                startDestination = Destino.POSTITS.rota,
                modifier = Modifier.weight(1f),
            ) {
                composable(Destino.POSTITS.rota) { PostitsScreen() }
                composable(Destino.CALENDARIO.rota) { CalendarioScreen() }
                composable(Destino.PLANNER.rota) { PlannerScreen() }
                composable(Destino.TODO.rota) { TodoScreen() }
                composable(Destino.POMODORO.rota) { PomodoroScreen() }
                composable(Destino.AGUA.rota) { AguaScreen() }
                composable(Destino.APARENCIA.rota) {
                    AparenciaScreen(nome = nome, aoSair = aoSair)
                }
            }

            }
        }

        // A barra de baixo por ultimo, encostada na borda, porque e ela que a
        // pessoa toca. So existe onde nao coube a lateral.
        if (!lateralCabe) {
            BarraInferior(atual = atual, aoTrocar = { nav.irPara(it) })
        }
    }
    }
    }
}

/** A partir daqui a lateral de 260 dp e um quarto da tela ou menos. */
private val LARGURA_MINIMA_PARA_LATERAL = 720.dp

/**
 * Troca de aba sem empilhar historico.
 *
 * Sem `launchSingleTop` e o `popUpTo`, ir e voltar entre duas abas dez vezes
 * deixaria dez entradas na pilha, e o botao de voltar do Android viraria um
 * desfazer de navegacao -- que nao e o que ninguem espera de barra inferior.
 * `saveState`/`restoreState` preservam a rolagem de cada aba.
 */
private fun NavHostController.irPara(destino: Destino) {
    navigate(destino.rota) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
