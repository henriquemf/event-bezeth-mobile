package com.beazeth.notifier.avisos

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.beazeth.notifier.R

/**
 * Os toques que os avisos podem ter.
 *
 * ## Por que trocar de som obriga a trocar o canal de lugar
 *
 * O som de um canal e copiado UMA vez, quando ele nasce. Depois disso quem
 * manda e a pessoa, nos ajustes do sistema, e `setSound` num canal existente
 * nao faz nada -- e nem apagar e recriar resolve, porque o Android lembra dos
 * ajustes de um canal apagado e os restaura quando um canal com o MESMO id
 * reaparece.
 *
 * O jeito que funciona e o id carregar a escolha: `pomodoro_gota` e um canal
 * diferente de `pomodoro_sino`. Trocar o som cria o novo e apaga o velho, e e
 * por isso que [chave] entra no id do canal em [Canal.idCom].
 *
 * O preco: quem tiver ajustado esse canal a mao nos ajustes do sistema perde o
 * ajuste ao trocar o som pelo app. E aceitavel porque foi a propria pessoa que
 * acabou de pedir a troca -- ninguem troca o toque esperando manter o toque.
 *
 * ## Por que as chaves mudaram na 1.8.0
 *
 * Ate ali os toques eram senos gerados por conta, e o arquivo de cada um foi
 * trocado por uma gravacao. Trocar so o CONTEUDO do arquivo nao chegaria a quem
 * ja instalou: o canal guarda o endereco do recurso pelo id NUMERICO, que o
 * build pode renumerar, e o canal velho continuaria apontando para o que
 * estivesse naquele numero. Chave nova e canal novo, com o endereco certo.
 *
 * Quem tinha escolhido um dos antigos nao volta ao padrao: [porChave] traduz a
 * chave velha para o toque novo mais parecido (ver [ANTIGAS]).
 *
 * ## De onde vem cada toque
 *
 * Todos sao gravacoes CC0 (dominio publico) do Freesound, tratadas uma vez:
 * silencio da frente cortado, grave abaixo de ~120 Hz tirado (o alto-falante
 * do celular nao o reproduz e o devolve como zumbido), nada acima de 10 kHz e
 * volume nivelado entre eles. A origem de cada um esta no README.
 */
enum class Som(
    val chave: String,
    val nome: String,
    val descricao: String,
) {
    KALIMBA("kalimba", "Kalimba", "Uma nota redonda e macia, que se apaga devagar."),
    CAIXINHA("caixinha", "Caixinha de música", "Uma nota delicada de caixinha de música."),
    SININHO("sininho", "Sininho", "Curtinho e claro, como um sininho de mesa."),
    BOLHAS("bolhas", "Bolhinhas", "Bolhas de água subindo. Combina com o lembrete de beber."),
    HARPA("harpinha", "Harpa", "Uma frase de harpa. O mais longo e o mais sonhador."),

    /**
     * O toque de notificacao do proprio aparelho.
     *
     * Fica na lista porque e o que muita gente espera, e porque quem trocar o
     * som do sistema espera que o app acompanhe. E o mais alto de todos: o
     * padrao do Android e desenhado para chamar.
     */
    SISTEMA("sistema", "Toque do aparelho", "O mesmo som das outras notificações."),

    /** Sem som nenhum. A vibracao de cada canal continua valendo. */
    MUDO("mudo", "Sem som", "Só aparece na tela, sem tocar nada.");

    /**
     * Onde este som mora, ou `null` para [MUDO].
     *
     * `Uri.parse("android.resource://<pacote>/<id numerico>")` e o formato que
     * o `NotificationChannel` aceita para um recurso do proprio app.
     */
    fun uri(context: Context): Uri? = when (this) {
        MUDO -> null
        SISTEMA -> Settings.System.DEFAULT_NOTIFICATION_URI
        else -> Uri.parse("android.resource://${context.packageName}/${recurso()}")
    }

    private fun recurso(): Int = when (this) {
        KALIMBA -> R.raw.aviso_kalimba
        CAIXINHA -> R.raw.aviso_caixinha
        SININHO -> R.raw.aviso_sininho
        BOLHAS -> R.raw.aviso_bolhas
        HARPA -> R.raw.aviso_harpinha
        // Os dois nao tem arquivo; `uri` os resolve antes de chegar aqui.
        SISTEMA, MUDO -> 0
    }

    companion object {
        val PADRAO = KALIMBA

        /** Os toques de antes da 1.8.0, no toque novo mais parecido com cada um. */
        private val ANTIGAS = mapOf("sino" to SININHO, "gota" to BOLHAS, "harpa" to HARPA)

        /** O som guardado, ou o padrao se a chave nao for reconhecida. */
        fun porChave(chave: String?): Som =
            entries.firstOrNull { it.chave == chave } ?: ANTIGAS[chave] ?: PADRAO
    }
}
