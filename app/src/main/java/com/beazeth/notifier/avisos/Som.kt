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
 */
enum class Som(
    val chave: String,
    val nome: String,
    val descricao: String,
) {
    SINO("sino", "Sino", "Duas notas, como um sininho de vidro."),
    GOTA("gota", "Gotinha", "Curtíssimo e discreto. Quase ninguém ao redor percebe."),
    HARPA("harpa", "Harpa", "Três notas subindo. O mais alegre dos três."),

    /**
     * O toque de notificacao do proprio aparelho.
     *
     * Fica na lista porque e o que muita gente espera, e porque quem trocar o
     * som do sistema espera que o app acompanhe. E o mais alto dos cinco: o
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
        SINO -> R.raw.aviso_sino
        GOTA -> R.raw.aviso_gota
        HARPA -> R.raw.aviso_harpa
        // Os dois nao tem arquivo; `uri` os resolve antes de chegar aqui.
        SISTEMA, MUDO -> 0
    }

    companion object {
        val PADRAO = SINO

        /** O som guardado, ou o padrao se a chave nao for reconhecida. */
        fun porChave(chave: String?): Som =
            entries.firstOrNull { it.chave == chave } ?: PADRAO
    }
}
