package com.beazeth.notifier.avisos

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.beazeth.notifier.R

/**
 * A salva de palmas do fim do foco, e o interruptor de "o app esta na frente".
 *
 * ## Por que o app precisa saber se esta visivel
 *
 * O alarme do pomodoro toca esteja o app aberto ou fechado, e posta o aviso na
 * barra -- com som. Se a festa tambem tocasse, o fim de um pomodoro com o app
 * aberto sairia como dois sons ao mesmo tempo, um por cima do outro.
 *
 * Com este interruptor cada momento tem um dono: app fechado, quem avisa e a
 * barra de notificacao; app na frente, quem avisa e o confete com as palmas --
 * que e o que a pessoa esta olhando de qualquer forma.
 *
 * `@Volatile` porque quem escreve e a thread principal (a `MainActivity`) e
 * quem le e a do alarme.
 */
object Visibilidade {
    @Volatile
    var appNaFrente: Boolean = false
}

/**
 * Toca as palmas, agora.
 *
 * `RingtoneManager` e nao `MediaPlayer` pelo mesmo motivo da previa dos toques
 * em `EscolhaDeSom`: e o caminho curto para tocar um `res/raw` sem gerenciar
 * ciclo de vida de player nenhum -- ele se solta sozinho ao terminar.
 *
 * `USAGE_NOTIFICATION` de proposito: a festa sai no mesmo volume dos avisos do
 * app, e nao no de midia. Quem baixou o volume dos avisos porque esta numa
 * reuniao nao espera ser aplaudida a plenos pulmoes.
 */
internal fun tocarPalmas(context: Context) {
    val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.festa_palmas}")
    RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
        audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        play()
    }
}

/**
 * Quanto dura o descanso que comeca sozinho quando o foco acaba.
 *
 * Espelha `DESCANSOS` em `static/js/core/pomodoro.js`: os dois tem de dar o
 * mesmo numero, senao a mesma sessao descansa diferente no site e no celular.
 *
 * A regra e a classica -- cinco minutos para um pomodoro curto, e mais para
 * quem passou uma hora inteira concentrada. Nao ha ajuste na tela porque nao ha
 * o que ajustar: o descanso e proporcional ao foco, e escolher os dois seria
 * escolher duas vezes a mesma coisa.
 */
internal fun descansoDe(minutos: Int): Int = when {
    minutos <= 30 -> 5
    minutos <= 59 -> 10
    else -> 15
}
