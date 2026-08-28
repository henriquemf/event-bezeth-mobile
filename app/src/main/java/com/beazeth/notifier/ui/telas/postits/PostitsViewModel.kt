package com.beazeth.notifier.ui.telas.postits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.NotaEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O estado do quadro: qual aba esta aberta e o que ha nela.
 *
 * Faz o papel do `store.js` do site. Toda escrita passa por aqui e vai para o
 * Room; a tela nunca fala com o [Repositorio] direto, e nunca espera a rede.
 */
class PostitsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    private val _quadro = MutableStateFlow(QUADROS.first().first)
    val quadro = _quadro

    @OptIn(ExperimentalCoroutinesApi::class)
    val notas = _quadro
        .flatMapLatest { repo.notasDoQuadro(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun trocarQuadro(novo: String) { _quadro.value = novo }

    /**
     * Cria na primeira vaga livre do quadro -- o `findFreeSpot` do site.
     *
     * As duas tentativas anteriores erraram para lados opostos. Criar sempre em
     * (18, 18) escondia o post-it novo debaixo do que ja estava la. Criar
     * ABAIXO de tudo resolveu isso e criou outro: com meia duzia de papeis no
     * quadro, o novo nascia fora da area visivel, e a pessoa tocava em "Novo
     * post-it" e nao via nada acontecer.
     *
     * A resposta do site e a certa: varrer de cima para baixo procurando um
     * espaco onde o papel caiba sem encostar em ninguem. Como a varredura
     * comeca no topo, ele aparece perto do inicio -- e ainda tapa os buracos
     * deixados por quem foi arrastado para longe.
     */
    fun criar(texto: String, cor: String, larguraVisivel: Int) = viewModelScope.launch {
        val (x, y) = vagaLivre(notas.value, larguraVisivel)
        repo.criarNota(_quadro.value, texto, cor, x = x, y = y)
    }

    fun editar(nota: NotaEntity, texto: String) = viewModelScope.launch {
        repo.editarNota(nota, texto)
    }

    /** Grava o que esta sendo escrito sem enfileirar envio.
     *  Ver [Repositorio.rascunharNota]. */
    fun rascunhar(nota: NotaEntity, texto: String) = viewModelScope.launch {
        repo.rascunharNota(nota, texto)
    }

    fun recolorir(nota: NotaEntity, cor: String) = viewModelScope.launch {
        repo.recolorirNota(nota, cor)
    }

    /** Posicao ABSOLUTA, em dp -- nao deslocamento.
     *
     *  Era delta, e delta obrigava quem chamava a somar sobre `nota.x`: se a
     *  linha do Room ja tivesse mudado no meio do arrasto, a soma sairia de uma
     *  base velha. O cartao sabe exatamente onde se largou; que ele diga. */
    fun mover(nota: NotaEntity, x: Int, y: Int) = viewModelScope.launch {
        repo.moverNota(nota, x, y, nota.width, nota.height)
    }

    fun apagar(nota: NotaEntity) = viewModelScope.launch { repo.apagarNota(nota.id) }

    /**
     * O botao "Organizar": reenfileira em fileiras, respeitando a largura.
     *
     * Mesmo algoritmo do `tidy` do site -- ordena por posicao atual, quebra
     * linha quando nao cabe mais, e a altura da fileira e a do post-it mais
     * alto dela.
     */
    fun organizar(larguraDisponivel: Int) = viewModelScope.launch {
        val visiveis = notas.value.sortedWith(compareBy({ it.y }, { it.x }))
        if (visiveis.isEmpty()) return@launch

        val folga = Repositorio.FOLGA
        var x = folga
        var y = folga
        var alturaDaFileira = 0

        for (nota in visiveis) {
            if (x > folga && x + nota.width > larguraDisponivel - folga) {
                x = folga
                y += alturaDaFileira + folga
                alturaDaFileira = 0
            }
            repo.moverNota(nota, x, y, nota.width, nota.height)
            alturaDaFileira = maxOf(alturaDaFileira, nota.height)
            x += nota.width + folga
        }
    }
}

/**
 * O primeiro lugar onde um post-it novo cabe sem encostar em ninguem.
 *
 * Tradução de `findFreeSpot` (`js/pages/notes/board.js`), passo a passo: varre
 * de cima para baixo e da esquerda para a direita, de 26 em 26, e aceita a
 * primeira posicao em que o papel nao chega a menos de 10 dp de nenhum outro.
 *
 * O `2400` e o teto da varredura, e nao do quadro: abaixo disso a busca
 * desiste e empilha em cascata, deslocando um pouco a cada post-it. E o que
 * garante que a funcao sempre responde, mesmo com o quadro lotado -- e a
 * cascata ainda deixa o de cima visivel, que era o problema original.
 *
 * Fora da classe porque nao toca em estado nenhum: entra a lista e a largura,
 * sai um ponto. Assim da para conferir a conta sem instanciar um ViewModel.
 */
private fun vagaLivre(existentes: List<NotaEntity>, larguraVisivel: Int): Pair<Int, Int> {
    val folga = Repositorio.FOLGA
    val largura = Repositorio.LARGURA_PADRAO
    val altura = Repositorio.ALTURA_PADRAO
    val respiro = 10
    val passo = 26
    val limite = maxOf(larguraVisivel - folga, largura + folga)

    var y = folga
    while (y < TETO_DA_BUSCA) {
        var x = folga
        while (x + largura <= limite) {
            val cabe = existentes.all { outra ->
                x + largura + respiro <= outra.x ||
                    outra.x + outra.width + respiro <= x ||
                    y + altura + respiro <= outra.y ||
                    outra.y + outra.height + respiro <= y
            }
            if (cabe) return x to y
            x += passo
        }
        y += passo
    }

    val deslocamento = (existentes.size % 8) * 28
    return (folga + deslocamento) to (folga + deslocamento)
}

private const val TETO_DA_BUSCA = 2400
