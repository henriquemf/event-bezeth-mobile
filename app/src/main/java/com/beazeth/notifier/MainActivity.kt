package com.beazeth.notifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.ui.CascaApp
import com.beazeth.notifier.ui.CriarContaScreen
import com.beazeth.notifier.ui.LoginScreen
import com.beazeth.notifier.ui.Sessao
import com.beazeth.notifier.ui.SessaoViewModel
import com.beazeth.notifier.ui.componentes.FundoDoce
import com.beazeth.notifier.ui.componentes.RodapeDoAmor
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.ui.theme.FONTE_PADRAO
import com.beazeth.notifier.ui.theme.PALETA_PADRAO
import com.beazeth.notifier.ui.theme.TemaBeazeth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Desenha sob as barras do sistema. Junto do `safeDrawingPadding` nas
        // telas, é o que dá a sensação de tela cheia que o site não tinha.
        enableEdgeToEdge()
        esconderBarrasDoSistema()
        setContent {
            // A aparencia escolhida vem do DataStore. Enquanto a primeira
            // leitura nao chega, valem os padroes -- os mesmos com que o site
            // abre, entao nao ha piscada de tema errado.
            val prefs = remember { Preferencias(applicationContext) }
            val tema by prefs.tema.collectAsStateWithLifecycle(PALETA_PADRAO)
            val fonte by prefs.fonte.collectAsStateWithLifecycle(FONTE_PADRAO)
            val escuro by prefs.escuro.collectAsStateWithLifecycle(false)
            val escopo = rememberCoroutineScope()

            TemaBeazeth(tema = tema, fonte = fonte, escuro = escuro) {
                // O fundo mora aqui, e não em cada tela, pelo mesmo motivo que
                // no site ele mora no `body`: é um só, atrás de tudo. Um
                // `Surface` de cor chapada neste lugar apagaria o degradê e as
                // bolhas, que são metade da identidade visual.
                FundoDoce {
                    // O sol/lua da barra de cima escreve no mesmo DataStore que
                    // a tela de Aparencia le: os dois controles sao um estado
                    // so, e mexer num move o outro.
                    App(
                        escuro = escuro,
                        aoAlternarEscuro = { escopo.launch { prefs.definirEscuro(it) } },
                    )
                }
            }
        }
    }

    /**
     * Volta a esconder as barras depois de qualquer coisa que as traga de volta.
     *
     * O sistema as restaura sozinho em varias situacoes -- ao voltar do fundo,
     * ao fechar o teclado, ao sair de uma janela de permissao. Sem isto, a barra
     * de notificacao reaparece e FICA, e a tela cheia dura ate o primeiro
     * desvio.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) esconderBarrasDoSistema()
    }

    /**
     * Tela cheia: sem barra de notificacao e sem barra de navegacao, ate que se
     * puxe da borda.
     *
     * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` e o que faz a diferenca entre
     * "escondido" e "sumido": as barras continuam a um gesto de distancia, e
     * voltam a sumir sozinhas. Sem esse comportamento, ou elas nao voltam, ou
     * voltam e ficam.
     *
     * O que se ganha sao uns 90 dp de altura -- em pe e uma tarefa a mais na
     * lista; deitado, num aparelho que tem 411 dp de altura, e quase um quarto
     * da tela.
     */
    private fun esconderBarrasDoSistema() {
        val controle = WindowInsetsControllerCompat(window, window.decorView)
        controle.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controle.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun App(
    escuro: Boolean,
    aoAlternarEscuro: (Boolean) -> Unit,
    vm: SessaoViewModel = viewModel(),
) {
    val sessao by vm.sessao.collectAsStateWithLifecycle()
    val login by vm.login.collectAsStateWithLifecycle()
    var cadastrando by rememberSaveable { mutableStateOf(false) }

    when (val s = sessao) {
        // Não é uma tela: é o instante entre ler o token do disco e saber se
        // ele vale. Mostrar o login aqui faria quem já entrou ver a tela de
        // senha piscar a cada abertura.
        Sessao.Verificando -> Carregando()
        // Entrar e criar conta sao a mesma situacao (ninguem esta logado), e
        // nao dois destinos de navegacao. Um estado local basta -- um NavHost
        // aqui so acrescentaria pilha de historico para duas telas que se
        // alternam.
        // O "EU TE AMO MOMO" tambem aqui: no `base.html` ele fica FORA do bloco
        // da casca, entao as telas de entrar e criar conta o mostram igual.
        Sessao.Fora -> ComRodape {
            if (cadastrando) {
                CriarContaScreen(
                    estado = login,
                    aoCriar = vm::criarConta,
                    aoIrParaLogin = { cadastrando = false; vm.limparErro() },
                    aoUsarSemConta = vm::usarSemConta,
                )
            } else {
                LoginScreen(
                    estado = login,
                    aoEntrar = vm::entrar,
                    aoIrParaCadastro = { cadastrando = true; vm.limparErro() },
                    aoUsarSemConta = vm::usarSemConta,
                )
            }
        }
        is Sessao.Dentro -> CascaApp(
            nome = s.nome,
            escuro = escuro,
            aoAlternarEscuro = aoAlternarEscuro,
            aoSair = vm::sair,
        )
        // A mesma casca, e nao uma versao reduzida: o modo local nao e um app
        // menor, e o mesmo app sem servidor. O que ele nao tem -- fila, sync,
        // nome de conta -- some por conta do proprio `local`.
        Sessao.Local -> CascaApp(
            nome = "",
            local = true,
            escuro = escuro,
            aoAlternarEscuro = aoAlternarEscuro,
            aoSair = vm::verTelaDeLogin,
        )
    }
}

/** A tela por cima, a faixa embaixo. */
@Composable
private fun ComRodape(conteudo: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) { conteudo() }
        RodapeDoAmor()
    }
}

@Composable
private fun Carregando() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Doce.destaque)
    }
}
