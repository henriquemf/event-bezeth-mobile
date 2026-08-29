package com.beazeth.notifier.ui.telas.perfil

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beazeth.notifier.data.Api
import com.beazeth.notifier.data.Estatisticas
import com.beazeth.notifier.data.Perfil
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.TokenStore
import com.beazeth.notifier.data.fluxoDeEstatisticas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O que a tela de perfil esta fazendo agora.
 *
 * Um estado so para os tres formularios (nome, e-mail e senha), com [campo]
 * dizendo qual deles falou por ultimo. Tres estados separados dariam tres
 * mensagens na tela ao mesmo tempo -- "senha trocada" ainda verde enquanto o
 * e-mail falha logo abaixo.
 */
data class EstadoDoPerfil(
    val salvando: Boolean = false,
    val erro: String? = null,
    val recado: String? = null,
    val campo: Campo? = null,
) {
    enum class Campo { NOME, EMAIL, SENHA, FOTO }
}

class PerfilViewModel(app: Application) : AndroidViewModel(app) {

    private val perfil = Perfil(app)
    private val guardaToken = TokenStore(app)
    private val repo = Repositorio(app)

    private val _estado = MutableStateFlow(EstadoDoPerfil())
    val estado: StateFlow<EstadoDoPerfil> = _estado.asStateFlow()

    val estatisticas = fluxoDeEstatisticas(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estatisticas())

    val nomeLocal = perfil.nomeLocal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Se ha foto escolhida -- e o que decide mostrar "Remover foto". */
    val temFoto = perfil.versaoDaFoto.map { it != 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pendencias = repo.pendencias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val ultimaSync = guardaToken.ultimaSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---------------------------------------------------------------- a foto

    fun escolherFoto(origem: Uri) = viewModelScope.launch {
        _estado.value = EstadoDoPerfil(salvando = true, campo = EstadoDoPerfil.Campo.FOTO)
        val deu = perfil.salvarFoto(origem)
        _estado.value = if (deu) {
            EstadoDoPerfil(recado = "Foto trocada.", campo = EstadoDoPerfil.Campo.FOTO)
        } else {
            EstadoDoPerfil(
                erro = "Não consegui ler essa imagem. Tente outra.",
                campo = EstadoDoPerfil.Campo.FOTO,
            )
        }
    }

    fun removerFoto() = viewModelScope.launch {
        perfil.removerFoto()
        _estado.value = EstadoDoPerfil(
            recado = "Foto removida.",
            campo = EstadoDoPerfil.Campo.FOTO,
        )
    }

    // ---------------------------------------------------------------- o nome

    /**
     * Grava o nome de exibicao.
     *
     * Sem conta ele mora no aparelho; com conta ele e o `display_name` do
     * servidor, o mesmo que o site mostra. Dai [aoMudar], que devolve o nome
     * COMO O SERVIDOR GRAVOU -- ele corta em 40 caracteres, e a lateral tem de
     * mostrar o que existe de verdade, nao o que foi digitado.
     */
    fun salvarNome(nome: String, aoMudar: (String, String) -> Unit) = viewModelScope.launch {
        val limpo = nome.trim()
        if (limpo.isEmpty()) {
            _estado.value = EstadoDoPerfil(
                erro = "O nome não pode ficar vazio.",
                campo = EstadoDoPerfil.Campo.NOME,
            )
            return@launch
        }

        val token = guardaToken.tokenAtual()
        if (token == null) {
            perfil.definirNomeLocal(limpo)
            _estado.value = EstadoDoPerfil(
                recado = "Nome salvo.",
                campo = EstadoDoPerfil.Campo.NOME,
            )
            return@launch
        }

        enviar(EstadoDoPerfil.Campo.NOME, "Nome salvo.", aoMudar) {
            Api.atualizarConta(token, nome = limpo)
        }
    }

    // ------------------------------------------------------- e-mail e senha

    fun salvarEmail(
        email: String,
        senhaAtual: String,
        aoMudar: (String, String) -> Unit,
    ) = viewModelScope.launch {
        val token = guardaToken.tokenAtual() ?: return@launch
        if (senhaAtual.isBlank()) {
            _estado.value = EstadoDoPerfil(
                erro = "Digite a senha atual para trocar o e-mail.",
                campo = EstadoDoPerfil.Campo.EMAIL,
            )
            return@launch
        }

        enviar(EstadoDoPerfil.Campo.EMAIL, "E-mail trocado.", aoMudar) {
            Api.atualizarConta(token, email = email.trim(), senhaAtual = senhaAtual)
        }
    }

    fun salvarSenha(
        atual: String,
        nova: String,
        repetida: String,
        aoMudar: (String, String) -> Unit,
    ) = viewModelScope.launch {
        val token = guardaToken.tokenAtual() ?: return@launch

        // As duas unicas conferencias que o servidor NAO pode fazer por nos: ele
        // recebe uma senha nova so, e nao sabe que houve um campo de repetir.
        if (atual.isBlank()) {
            _estado.value = EstadoDoPerfil(
                erro = "Digite a senha atual.",
                campo = EstadoDoPerfil.Campo.SENHA,
            )
            return@launch
        }
        if (nova != repetida) {
            _estado.value = EstadoDoPerfil(
                erro = "As duas senhas novas não são iguais.",
                campo = EstadoDoPerfil.Campo.SENHA,
            )
            return@launch
        }

        enviar(EstadoDoPerfil.Campo.SENHA, "Senha trocada.", aoMudar) {
            Api.atualizarConta(token, senhaAtual = atual, senhaNova = nova)
        }
    }

    /**
     * O caminho comum das tres gravacoes que passam pelo servidor.
     *
     * A resposta do PATCH ja traz a conta como ficou, entao o que ela diz e o
     * que vale -- inclusive quando o servidor cortou o nome. Guardar no
     * [TokenStore] antes de avisar a casca mantem a ordem certa: se o app
     * morresse entre uma coisa e outra, o disco ja estaria certo.
     */
    private suspend fun enviar(
        campo: EstadoDoPerfil.Campo,
        recado: String,
        aoMudar: (String, String) -> Unit,
        chamada: suspend () -> Api.Resultado<Api.RespostaConta>,
    ) {
        _estado.value = EstadoDoPerfil(salvando = true, campo = campo)

        _estado.value = when (val r = chamada()) {
            is Api.Resultado.Ok -> {
                val conta = r.corpo.user
                if (conta == null) {
                    EstadoDoPerfil(erro = "Resposta incompleta do servidor.", campo = campo)
                } else {
                    guardaToken.guardarConta(conta.nome, conta.email)
                    aoMudar(conta.nome, conta.email)
                    EstadoDoPerfil(recado = recado, campo = campo)
                }
            }
            is Api.Resultado.Erro -> EstadoDoPerfil(erro = r.mensagem, campo = campo)
        }
    }

    /** Some com o recado ao mexer em qualquer campo: ele falava do estado
     *  anterior, e continuar na tela faria parecer que o novo ja foi salvo. */
    fun limparRecado() {
        if (_estado.value.recado != null || _estado.value.erro != null) {
            _estado.value = EstadoDoPerfil()
        }
    }
}
