package com.beazeth.notifier.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beazeth.notifier.data.Api
import com.beazeth.notifier.data.Perfil
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.TokenStore
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** O que a tela de login precisa saber para se desenhar. */
data class EstadoLogin(
    val carregando: Boolean = false,
    val erro: String? = null,
    /** Pedido de confirmacao: entrar descartaria alteracoes de outra conta. */
    val descarte: Descarte? = null,
)

/**
 * Ha [alteracoes] escritas da conta [emailAnterior] que nao subiram ainda, e a
 * conta que esta entrando e outra.
 */
data class Descarte(val alteracoes: Int, val emailAnterior: String?)

/** Em qual das situações o app está ao abrir. */
sealed interface Sessao {
    /** Ainda lendo o token guardado. Dura milissegundos e evita o piscar da
     *  tela de login para quem já está logado. */
    data object Verificando : Sessao
    data object Fora : Sessao

    /**
     * Dentro de uma conta.
     *
     * Carrega nome e e-mail porque a casca precisa dos dois em toda tela: o
     * nome fica na lateral, e a tela de perfil mostra o e-mail. Vem do
     * armazenamento, nao da rede -- ver [TokenStore].
     */
    data class Dentro(val nome: String, val email: String) : Sessao

    /**
     * Sem conta: tudo mora neste aparelho e nada sai dele.
     *
     * Não é o mesmo que [Fora]. `Fora` é quem ainda não decidiu, e por isso vê a
     * tela de login; `Local` é quem decidiu que não quer conta, e vê o app
     * inteiro. As duas não têm token, e sem essa distinção o app trataria uma
     * escolha deliberada como um estado a ser corrigido — mandando de volta para
     * o login a cada abertura.
     */
    data object Local : Sessao
}

class SessaoViewModel(app: Application) : AndroidViewModel(app) {

    private val guardaToken = TokenStore(app)

    private val _sessao = MutableStateFlow<Sessao>(Sessao.Verificando)
    val sessao: StateFlow<Sessao> = _sessao.asStateFlow()

    private val _login = MutableStateFlow(EstadoLogin())
    val login: StateFlow<EstadoLogin> = _login.asStateFlow()

    /** O login que espera a pessoa decidir sobre o [Descarte]. Em memoria so. */
    private var entradaEmEspera: Pair<String, Api.Conta>? = null

    init {
        restaurar()
    }

    /**
     * Decide a primeira tela -- sem esperar a rede.
     *
     * **Quem tem token entra na hora.** A versão anterior segurava a tela num
     * `/api/me` antes de mostrar qualquer coisa, e o efeito prático era um
     * giro de quarenta segundos sobre o fundo vazio toda manhã: o plano
     * gratuito do Render desliga o container por inatividade, e a primeira
     * requisição do dia paga o tempo de ele subir. Um app cujo dado inteiro
     * está no Room, e que existe justamente para abrir sem internet, não tinha
     * o que esperar ali.
     *
     * A conferência continua, só que atrás: se o token venceu ou a conta
     * sumiu, a sessão cai assim que o servidor responder. Se a rede está fora,
     * nada muda -- e é o comportamento certo, porque recusar a entrada seria
     * esconder dado que já está no aparelho.
     */
    private fun restaurar() = viewModelScope.launch {
        val token = guardaToken.tokenAtual()
        if (token == null) {
            // Sem token ha duas situacoes bem diferentes, e so a marca do modo
            // local as separa: quem escolheu ficar sem conta abre direto no app.
            _sessao.value =
                if (guardaToken.modoLocalAtivo()) Sessao.Local else Sessao.Fora
            return@launch
        }

        _sessao.value = Sessao.Dentro(
            nome = guardaToken.nomeAtual().orEmpty(),
            email = guardaToken.emailAtual().orEmpty(),
        )

        when (val r = Api.quemSou(token)) {
            // Nome e e-mail podem ter mudado no site, ou em outro aparelho,
            // desde o ultimo login.
            is Api.Resultado.Ok -> r.corpo.user?.let { conta ->
                guardaToken.guardarConta(conta.nome, conta.email, conta.id)
                _sessao.value = Sessao.Dentro(conta.nome, conta.email)
            }

            // O token caiu -- venceu, ou a senha foi trocada noutro aparelho.
            // Os dados do aparelho FICAM, porque podem ter escrita na fila que
            // ainda nao subiu; quem decide o destino deles e o proximo login
            // (ver `concluirEntrada`). O worker para: sem token ele so bateria
            // num 401 de hora em hora.
            is Api.Resultado.Erro ->
                if (r.semSessao) {
                    SyncWorker.parar(getApplication())
                    guardaToken.sessaoCaiu()
                    _sessao.value = Sessao.Fora
                }
        }
    }

    fun entrar(email: String, senha: String) = viewModelScope.launch {
        _login.value = EstadoLogin(carregando = true)

        when (val r = Api.entrar(email, senha)) {
            is Api.Resultado.Ok -> {
                val token = r.corpo.token
                val conta = r.corpo.user
                if (token == null || conta == null) {
                    _login.value = EstadoLogin(erro = "Resposta incompleta do servidor.")
                } else {
                    concluirEntrada(token, conta)
                }
            }
            is Api.Resultado.Erro -> _login.value = EstadoLogin(erro = r.mensagem)
        }
    }

    fun criarConta(nome: String, email: String, senha: String) = viewModelScope.launch {
        _login.value = EstadoLogin(carregando = true)

        when (val r = Api.criarConta(nome.trim(), email.trim(), senha)) {
            is Api.Resultado.Ok -> {
                val token = r.corpo.token
                val conta = r.corpo.user
                if (token == null || conta == null) {
                    _login.value = EstadoLogin(erro = "Resposta incompleta do servidor.")
                } else {
                    concluirEntrada(token, conta)
                }
            }
            is Api.Resultado.Erro -> _login.value = EstadoLogin(erro = r.mensagem)
        }
    }

    /**
     * O fim do caminho de entrar e de criar conta -- os dois passam por aqui.
     *
     * **A ordem das duas primeiras linhas e o que faz o modo local nao ser uma
     * armadilha.** `guardar` apaga a marca do modo local; so DEPOIS disso a fila
     * de envio volta a aceitar escritas, e por isso a adocao vem em seguida e
     * nao antes -- `Repositorio.enfileirar` ignoraria tudo em silencio.
     *
     * **Dado de outra conta sai antes de a nova entrar.** Quando a sessao cai
     * (token vencido, senha trocada noutro lugar), o banco do aparelho fica,
     * com a fila de escritas dentro. Se quem entra agora e outra pessoa, ela
     * veria o diario e os post-its da anterior -- e a fila, drenada com o token
     * NOVO, gravaria as escritas da anterior na conta dela, para sempre. Se e a
     * mesma pessoa, nada sai: a fila sobe e nenhuma escrita offline se perde.
     *
     * **E nada sai sem perguntar.** O que ja subiu esta salvo no servidor, na
     * conta anterior, e apagar a copia do aparelho nao perde nada. O que ainda
     * esta na fila so existe aqui: com escrita pendente, a entrada para e a tela
     * pede confirmacao ([Descarte], desenhado por `DialogoDePendentes`) -- o
     * caminho que nao perde nada e entrar antes com a conta anterior, para a
     * fila subir.
     */
    private suspend fun concluirEntrada(token: String, conta: Api.Conta, descarteConfirmado: Boolean = false) {
        val dono = guardaToken.contaDosDados()
        val emailDono = guardaToken.emailDosDados()
        val deOutraConta = if (dono != null) {
            dono != conta.id
        } else {
            emailDono != null && !emailDono.equals(conta.email, ignoreCase = true)
        }
        if (deOutraConta && !descarteConfirmado) {
            val pendentes = BancoLocal.obter(getApplication()).pendencias().quantas().first()
            if (pendentes > 0) {
                entradaEmEspera = token to conta
                _login.value = EstadoLogin(descarte = Descarte(pendentes, emailDono))
                return
            }
        }
        if (deOutraConta) {
            SyncWorker.parar(getApplication())
            BancoLocal.limpar(getApplication())
            Perfil(getApplication()).removerFoto()
        }
        val vinhaDoModoLocal = guardaToken.modoLocalAtivo()
        guardaToken.guardar(token, conta.nome, conta.email, conta.id)
        if (vinhaDoModoLocal) {
            Repositorio(getApplication()).adotarDadosLocais()
        }
        _login.value = EstadoLogin()
        _sessao.value = Sessao.Dentro(conta.nome, conta.email)
    }

    /**
     * O perfil trocou nome ou e-mail: a casca inteira acompanha.
     *
     * A tela de perfil ja gravou no [TokenStore]; o que falta e o estado que a
     * lateral desenha. Sem isto, o nome novo so apareceria na proxima abertura
     * do app.
     */
    fun contaMudou(nome: String, email: String) {
        _sessao.value = Sessao.Dentro(nome, email)
    }

    /** A pessoa leu o aviso e decidiu descartar as alteracoes da outra conta. */
    fun confirmarDescarte() = viewModelScope.launch {
        val (token, conta) = entradaEmEspera ?: return@launch
        entradaEmEspera = null
        concluirEntrada(token, conta, descarteConfirmado = true)
    }

    /** Voltou atras: nada e apagado, e o token da conta nova e esquecido. */
    fun cancelarDescarte() {
        entradaEmEspera = null
        _login.value = EstadoLogin()
    }

    /** Limpa o erro ao trocar entre entrar e criar conta: a mensagem da tela
     *  anterior nao tem o que dizer sobre a nova. */
    fun limparErro() {
        _login.value = EstadoLogin()
    }

    /**
     * Usar o app sem conta: nada de servidor, nada de rede.
     *
     * Nao cria conta anonima nem token de convidado -- simplesmente marca a
     * escolha e abre o app. O Room ja era a fonte da verdade das telas; o que
     * muda e que a fila de envio deixa de existir (ver `Repositorio.enfileirar`)
     * e o worker nunca e agendado.
     */
    fun usarSemConta() = viewModelScope.launch {
        guardaToken.ligarModoLocal()
        _sessao.value = Sessao.Local
    }

    /**
     * Do modo local para a tela de login -- sem apagar nada.
     *
     * A marca do modo local FICA. Quem chegou aqui so quis ver a tela de entrar;
     * se desistir e fechar o app, tem de voltar ao lugar onde estavam suas
     * coisas, e nao a uma tela de login que ele nunca pediu. Quem seguir ate o
     * fim entra numa conta, e ai [TokenStore.guardar] apaga a marca.
     */
    fun verTelaDeLogin() {
        _sessao.value = Sessao.Fora
    }

    /**
     * Sair de verdade.
     *
     * Nao basta esquecer o token: o banco do aparelho continua cheio dos
     * post-its e tarefas da conta anterior, e o proximo login veria tudo aquilo
     * antes da primeira sincronizacao -- que so traria o que mudou depois.
     *
     * O worker tambem para: sem sessao, ele so gastaria bateria batendo num
     * servidor que vai responder 401.
     */
    fun sair() = viewModelScope.launch {
        SyncWorker.parar(getApplication())
        BancoLocal.limpar(getApplication())
        guardaToken.limpar()
        // A foto e o nome escolhido saem junto. Sao o rosto de quem estava
        // dentro: deixa-los faria a proxima conta abrir com a cara da anterior
        // na lateral.
        Perfil(getApplication()).removerFoto()
        _sessao.value = Sessao.Fora
    }
}
