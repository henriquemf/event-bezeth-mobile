package com.beazeth.notifier.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Avisa a interface quando um id provisorio vira definitivo.
 *
 * ## Por que precisa existir
 *
 * A linha provisoria nao e editada quando o servidor responde: ela e APAGADA e
 * uma linha nova, com o id de verdade, entra no lugar. Para o Room isso e uma
 * troca de identidade, e para o Compose e a morte de um item da lista -- o
 * cartao sai da composicao e leva junto tudo o que era estado dele.
 *
 * Na pratica: criar um post-it e comecar a escrever imediatamente. Um segundo
 * depois a criacao sobe, o id troca, e o cartao que estava aberto simplesmente
 * fecha sozinho no meio da frase. Quem esta escrevendo nao tem como saber que
 * "o post-it -1" e "o post-it 66" sao o mesmo papel; so o [Sincronizador] sabe.
 * Entao ele conta.
 *
 * ## Por que um objeto de processo
 *
 * A sincronizacao roda num worker do WorkManager, que vive no mesmo processo do
 * app mas fora de qualquer tela. Nao ha ViewModel comum entre os dois lados, e
 * inventar um so para isto seria carregar estado de sessao para transportar um
 * par de numeros. Um [MutableSharedFlow] sem replay e o canal certo: quem nao
 * estava ouvindo nao perdeu nada -- se nao ha ninguem editando, nao ha o que
 * corrigir.
 */
object Remapeamentos {

    /** `de` era provisorio (negativo); `para` e o id que o servidor emitiu. */
    data class Troca(val entidade: String, val de: Long, val para: Long)

    // `tryEmit` nao pode suspender (quem anuncia esta no meio de uma transacao
    // de rede), e sem espaco de sobra ele falharia calado. Dezesseis cobre com
    // folga uma drenagem inteira de fila offline.
    private val canal = MutableSharedFlow<Troca>(extraBufferCapacity = 16)

    val fluxo = canal.asSharedFlow()

    fun anunciar(entidade: String, de: Long, para: Long) {
        canal.tryEmit(Troca(entidade, de, para))
    }
}
