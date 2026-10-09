package com.beazeth.notifier.sync

import android.content.Context
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.data.Api
import com.beazeth.notifier.data.Sincronizador
import com.beazeth.notifier.data.TokenStore
import kotlinx.coroutines.delay

/**
 * O que muda no site aparece aqui em um ou dois segundos, com o app na frente.
 *
 * Ate a 1.12 o celular so puxava o que mudou no PC ao nascer o processo, depois
 * de uma escrita propria ou de hora em hora. Voltar ao app NAO puxava nada, e
 * como o Android costuma manter o processo vivo, um post-it criado no PC podia
 * levar ate uma hora para aparecer.
 *
 * ## Como
 *
 * A cada [OLHADA_MS] pergunta o contador de mudancas da conta
 * (`/api/sync/versao`): um numero que o banco sobe a cada escrita de qualquer
 * aparelho, e que custa ao servidor so a leitura da conta que ele ja faz para
 * conferir o token. Mudou -- ou e a primeira olhada desde que o app voltou para
 * a frente --, roda a sincronizacao inteira. Quem chama cancela isto quando o
 * app sai da frente: em segundo plano fica so a obra de hora em hora, que nao
 * gasta bateria.
 *
 * Servidor sem o contador (deploy antigo, ou a rota falhou): cai para a
 * sincronizacao inteira a cada [SEM_CONTADOR_MS].
 */
object AoVivo {
    private const val OLHADA_MS = 2_000L
    private const val SEM_CONTADOR_MS = 30_000L

    suspend fun acompanhar(context: Context) {
        val app = context.applicationContext
        val tokens = TokenStore(app)
        var visto: Long? = null
        var primeira = true
        var ultimaInteira = 0L

        while (true) {
            val token = tokens.tokenAtual() ?: return
            when (val r = Api.versao(token)) {
                is Api.Resultado.Ok -> {
                    val atual = r.corpo.versao
                    if (primeira || atual == null || atual != visto) {
                        // So da o numero por visto se a sincronizacao fechou:
                        // sem rede no meio, a proxima olhada tenta de novo.
                        if (sincronizar(app)) {
                            visto = atual
                            primeira = false
                        }
                        ultimaInteira = System.currentTimeMillis()
                    }
                }

                is Api.Resultado.Erro -> {
                    // Sessao caiu: quem cuida e a tela de entrar.
                    if (r.semSessao) return
                    val agora = System.currentTimeMillis()
                    if (r.codigo == 404 && agora - ultimaInteira >= SEM_CONTADOR_MS) {
                        sincronizar(app)
                        ultimaInteira = agora
                    }
                }
            }
            delay(OLHADA_MS)
        }
    }

    private suspend fun sincronizar(app: Context): Boolean {
        val fim = Sincronizador(app).rodar()
        // O mesmo que a obra do WorkManager faz: evento novo vindo do site
        // precisa do alarme dele armado agora, e nao na proxima abertura.
        if (fim is Sincronizador.Fim.Ok) Lembretes.rearmar(app)
        return fim is Sincronizador.Fim.Ok
    }
}
