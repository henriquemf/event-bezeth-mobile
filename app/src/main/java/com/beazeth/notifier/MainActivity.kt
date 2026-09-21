package com.beazeth.notifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.ui.CascaApp
import com.beazeth.notifier.ui.CriarContaScreen
import com.beazeth.notifier.ui.LoginScreen
import com.beazeth.notifier.ui.Sessao
import com.beazeth.notifier.ui.SessaoViewModel
import com.beazeth.notifier.avisos.EXTRA_DESTINO
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.avisos.Visibilidade
import com.beazeth.notifier.ui.componentes.FundoDoce
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.ui.theme.FONTE_PADRAO
import com.beazeth.notifier.ui.theme.PALETA_PADRAO
import com.beazeth.notifier.ui.theme.TemaBeazeth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * A tela que um aviso pediu ao ser tocado, ou `null`.
     *
     * Estado do Compose, e nao um campo comum: quem o consome e a arvore de
     * composicao, e um aviso tocado com o app JA ABERTO chega pelo
     * `onNewIntent` -- fora de qualquer recomposicao. Sem ser observavel, o
     * toque nao levaria a lugar nenhum nesse caso, que e justamente o mais
     * comum durante o dia.
     */
    private var rotaPedida by mutableStateOf<String?>(null)

    /**
     * O pedido de permissao para avisar.
     *
     * Registrado como campo porque `registerForActivityResult` precisa
     * acontecer antes de a activity ficar pronta -- chamado de dentro do
     * `onCreate` ele lanca. A resposta nao e tratada: negada, a tela de perfil
     * mostra o aviso de que os lembretes estao mudos e leva aos ajustes.
     */
    private val pedidoDeAviso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Desenha sob as barras do sistema. Junto do `safeDrawingPadding` nas
        // telas, é o que dá a sensação de tela cheia que o site não tinha.
        enableEdgeToEdge()
        esconderBarrasDoSistema()

        rotaPedida = intent?.getStringExtra(EXTRA_DESTINO)
        pedirPermissaoDeAvisar()

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
                        rotaPedida = rotaPedida,
                        aoAtenderOPedido = { rotaPedida = null },
                    )
                }
            }
        }
    }

    /**
     * O app ja estava aberto e alguem tocou num aviso.
     *
     * So chega aqui por causa do `launchMode="singleTop"` no manifesto; sem
     * ele, o toque criaria uma SEGUNDA copia da tela por cima da que ja estava
     * aberta, e o botao de voltar sairia dela para a primeira.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        rotaPedida = intent.getStringExtra(EXTRA_DESTINO)
    }

    /**
     * Recalcula os alarmes toda vez que o app volta a frente, e cria os canais.
     *
     * E a rede de seguranca dos casos em que o Android apaga os alarmes sem
     * mandar aviso nenhum -- parar o app a forca pelos ajustes e o principal.
     * `Lembretes.rearmar` recalcula tudo do zero, entao repetir nao custa nada
     * alem de duas leituras de banco.
     *
     * Os canais nascem aqui dentro (e nao no `onCreate`) porque criar um canal
     * hoje depende de LER o toque escolhido, que mora no DataStore -- disco,
     * portanto corrotina. Isto roda no primeiro instante depois do `onCreate`,
     * entao continua valendo o que importava: um canal que nunca foi criado nao
     * aparece nos ajustes do sistema, e quem quiser calar so a agua precisa
     * encontrar a chave la sem esperar o primeiro aviso chegar.
     */
    override fun onResume() {
        super.onResume()
        // Com o app na frente, quem anuncia o fim do foco e o confete com as
        // palmas, e nao a barra de notificacao -- os dois juntos seriam dois
        // sons por cima um do outro. Ver `Visibilidade`.
        Visibilidade.appNaFrente = true
        lifecycleScope.launch { Lembretes.rearmar(applicationContext) }
    }

    override fun onPause() {
        super.onPause()
        Visibilidade.appNaFrente = false
    }

    /**
     * Pede a permissao de avisar, uma vez, na abertura.
     *
     * Sem drama nem tela de explicacao antes: o app se chama "notifier" e a
     * pessoa acabou de abrir um app de lembretes -- a caixa do sistema, aqui, e
     * menos intrusiva do que uma tela explicando o obvio.
     *
     * Se a resposta for nao, o Android nao mostra a caixa de novo. Quem
     * recupera esse caso e a tela de perfil, que le o estado real e leva aos
     * ajustes do sistema.
     */
    private fun pedirPermissaoDeAvisar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val jaTem = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!jaTem) pedidoDeAviso.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    rotaPedida: String?,
    aoAtenderOPedido: () -> Unit,
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
        Sessao.Fora -> if (cadastrando) {
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
        is Sessao.Dentro -> CascaApp(
            nome = s.nome,
            email = s.email,
            escuro = escuro,
            aoAlternarEscuro = aoAlternarEscuro,
            aoSair = vm::sair,
            aoAtualizarConta = vm::contaMudou,
            rotaPedida = rotaPedida,
            aoAtenderOPedido = aoAtenderOPedido,
        )
        // A mesma casca, e nao uma versao reduzida: o modo local nao e um app
        // menor, e o mesmo app sem servidor. O que ele nao tem -- fila, sync,
        // nome de conta -- some por conta do proprio `local`.
        Sessao.Local -> CascaApp(
            nome = "",
            email = "",
            local = true,
            escuro = escuro,
            aoAlternarEscuro = aoAlternarEscuro,
            aoSair = vm::verTelaDeLogin,
            // Sem conta nao ha o que avisar: o nome local mora no aparelho e a
            // propria tela de perfil ja o observa de la.
            aoAtualizarConta = { _, _ -> },
            // Os avisos nao dependem de conta: o pomodoro e do aparelho, e um
            // evento criado sem login mora no Room como qualquer outro.
            rotaPedida = rotaPedida,
            aoAtenderOPedido = aoAtenderOPedido,
        )
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
