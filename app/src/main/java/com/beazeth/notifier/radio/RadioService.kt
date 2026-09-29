package com.beazeth.notifier.radio

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.beazeth.notifier.MainActivity

/**
 * A radio lo-fi: o player, e a sessao de midia que o mostra ao sistema.
 *
 * Servico, e nao um player dentro da tela, porque radio e coisa que se ouve
 * com a tela apagada e o app no fundo. A `MediaSessionService` da Media3 cuida
 * do resto sozinha: poe o servico em primeiro plano enquanto toca, desenha a
 * notificacao de midia com o nome da musica, atende a tela de bloqueio e o
 * botao do fone.
 *
 * O ExoPlayer ja traz o que um app de radio precisa ter e ninguem lembra:
 * - **foco de audio**: numa ligacao a radio para; num aviso do sistema ela
 *   abaixa e volta sozinha;
 * - **fone desconectado pausa** (`setHandleAudioBecomingNoisy`) -- sem isso a
 *   radio sai de repente pelo alto-falante no meio do onibus;
 * - **trava de rede** (`WAKE_MODE_NETWORK`): com a tela apagada o Wi-Fi pode
 *   dormir, e o stream morreria no meio da musica.
 */
class RadioService : MediaSessionService() {

    private var sessao: MediaSession? = null
    private val principal = Handler(Looper.getMainLooper())
    private var tentativas = 0

    /** Pausa longa fecha o stream. Ver [PAUSA_ATE_FECHAR_MS]. */
    private val fecharStream = Runnable {
        sessao?.player?.let { if (!it.playWhenReady) it.stop() }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(Ouvinte(player))

        // Tocar na notificacao abre o app onde ele estava.
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sessao = MediaSession.Builder(this, player)
            .setSessionActivity(abrirApp)
            .setCallback(Porteiro())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sessao

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        sessao?.run {
            player.release()
            release()
        }
        sessao = null
        super.onDestroy()
    }

    /**
     * Quem pode mandar na radio.
     *
     * O servico nao e exportado, mas a sessao conversa tambem pelo token de
     * midia do sistema -- e o padrao da Media3 e aceitar todo controlador.
     * Aqui entram o proprio app, a notificacao de midia e quem o sistema diz
     * ser confiavel (tela de bloqueio, Bluetooth, carro). Outro app qualquer
     * nao liga, desliga nem troca a estacao de ninguem.
     */
    private inner class Porteiro : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val aceito = controller.packageName == packageName ||
                controller.isTrusted ||
                session.isMediaNotificationController(controller) ||
                session.isAutoCompanionController(controller) ||
                session.isAutomotiveController(controller)
            return if (aceito) {
                super.onConnect(session, controller)
            } else {
                MediaSession.ConnectionResult.reject()
            }
        }
    }

    private inner class Ouvinte(private val player: ExoPlayer) : Player.Listener {

        /**
         * O nome da musica chegou de dentro do stream.
         *
         * Vai para o item com `replaceMediaItem`, e nao so para a tela: e o
         * item que a notificacao e a tela de bloqueio leem. Trocar so os
         * metadados de um item com o MESMO endereco nao recarrega o stream --
         * o som nao corta a cada musica nova.
         */
        override fun onMetadata(metadata: Metadata) {
            val titulo = (0 until metadata.length())
                .map { metadata[it] }
                .filterIsInstance<IcyInfo>()
                .firstNotNullOfOrNull { it.title }
                ?: return
            val musica = separarMusica(titulo) ?: return
            val atual = player.currentMediaItem ?: return
            val estacao = estacaoPorId(atual.mediaId)
            if (atual.mediaMetadata.title?.toString() == musica.titulo &&
                atual.mediaMetadata.artist?.toString() == musica.artista.ifEmpty { estacao.nome }
            ) {
                return
            }
            player.replaceMediaItem(player.currentMediaItemIndex, estacao.comoItem(musica))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) tentativas = 0
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            principal.removeCallbacks(fecharStream)
            if (!playWhenReady) principal.postDelayed(fecharStream, PAUSA_ATE_FECHAR_MS)
        }

        /**
         * Sinal caiu, radio fora do ar: tenta de novo, esperando cada vez mais.
         * Depois de [MAX_TENTATIVAS] desiste -- a radio que nao volta em meio
         * minuto nao volta com mais insistencia, so gasta bateria.
         */
        override fun onPlayerError(error: PlaybackException) {
            if (tentativas >= MAX_TENTATIVAS) return
            tentativas += 1
            principal.postDelayed({
                if (player.playWhenReady || player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                    player.play()
                }
            }, 1000L shl (tentativas - 1))
        }
    }

    companion object {
        /**
         * Em pausa, o ExoPlayer segue com a conexao aberta e o buffer cheio.
         * Por meio minuto vale: voltar e instantaneo. Mais que isso e dado
         * movel gasto numa radio que ninguem esta ouvindo -- e o "de onde
         * parou" de uma radio ao vivo ja nao e o agora.
         */
        const val PAUSA_ATE_FECHAR_MS = 30_000L
        const val MAX_TENTATIVAS = 5
    }
}
