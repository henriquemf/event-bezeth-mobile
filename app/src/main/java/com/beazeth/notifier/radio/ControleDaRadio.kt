package com.beazeth.notifier.radio

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A estacao escolhida, o volume, e se a radio estava tocando.
 *
 * DataStore proprio, e nao o da aparencia: e outro assunto, e sair da conta
 * nao tem por que esquecer a estacao favorita.
 */
private val Context.prefsRadio by preferencesDataStore(name = "radio")

/** O que o player do canto desenha. */
data class EstadoDaRadio(
    val estacao: Estacao = ESTACOES.first(),
    val tocando: Boolean = false,
    val carregando: Boolean = false,
    val musica: Musica? = null,
    val volume: Float = 0.7f,
)

/**
 * A ponte entre a tela e o [RadioService].
 *
 * **Conecta so quando precisa.** Ligar-se ao servico o cria, e com ele um
 * ExoPlayer. Quem nunca toca na radio nao paga isso: a conexao nasce no
 * primeiro toque no player -- ou na abertura do app, se a radio estava
 * tocando quando ele foi fechado (e entao continua tocando no servico, e a
 * tela so precisa voltar a mostra-la).
 */
class ControleDaRadio(private val context: Context, private val escopo: CoroutineScope) {

    private val chaveEstacao = stringPreferencesKey("estacao")
    private val chaveVolume = floatPreferencesKey("volume")
    private val chaveAtiva = booleanPreferencesKey("ativa")

    private val _estado = MutableStateFlow(EstadoDaRadio())
    val estado: StateFlow<EstadoDaRadio> = _estado.asStateFlow()

    private var futuro: ListenableFuture<MediaController>? = null
    private var controle: MediaController? = null
    private val pendentes = mutableListOf<(MediaController) -> Unit>()

    /** O ultimo "ativa" gravado: `onEvents` dispara a cada buffer e a cada
     *  musica, e gravar em disco a cada um seria escrita a toa. */
    private var ativaGravada: Boolean? = null

    private val ouvinte = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            ler(player)
        }
    }

    init {
        escopo.launch {
            val prefs = context.prefsRadio.data.first()
            _estado.update {
                it.copy(
                    estacao = estacaoPorId(prefs[chaveEstacao]),
                    volume = (prefs[chaveVolume] ?: it.volume).coerceIn(0f, 1f),
                )
            }
            if (prefs[chaveAtiva] == true) conectar()
        }
    }

    fun alternar() = comControle { c ->
        val tocandoOuQuase = c.isPlaying ||
            (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)
        if (tocandoOuQuase) c.pause() else tocarEm(c, _estado.value.estacao)
    }

    fun escolher(estacao: Estacao) = comControle { c -> tocarEm(c, estacao, trocar = true) }

    fun vizinha(passo: Int) {
        val i = ESTACOES.indexOf(_estado.value.estacao)
        escolher(ESTACOES[(i + passo + ESTACOES.size) % ESTACOES.size])
    }

    fun definirVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _estado.update { it.copy(volume = v) }
        controle?.volume = v
        escopo.launch { context.prefsRadio.edit { it[chaveVolume] = v } }
    }

    /** A tela saiu: solta a conexao. A radio, se tocando, segue no servico. */
    fun soltar() {
        controle?.removeListener(ouvinte)
        futuro?.let { MediaController.releaseFuture(it) }
        futuro = null
        controle = null
        pendentes.clear()
    }

    // ------------------------------------------------------------ por dentro

    private fun comControle(acao: (MediaController) -> Unit) {
        val c = controle
        if (c != null) {
            acao(c)
        } else {
            pendentes += acao
            conectar()
        }
    }

    private fun conectar() {
        if (futuro != null) return
        val token = SessionToken(context, ComponentName(context, RadioService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        futuro = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run {
                futuro = null
                return@addListener
            }
            controle = c
            c.addListener(ouvinte)
            ler(c)
            pendentes.forEach { it(c) }
            pendentes.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Toca [estacao]. O stream so e trocado quando e outra estacao, ou quando o
     * player esta parado -- voltar de uma pausa curta continua do buffer, sem
     * reconectar (ver `RadioService.PAUSA_ATE_FECHAR_MS`).
     */
    private fun tocarEm(c: MediaController, estacao: Estacao, trocar: Boolean = false) {
        val outra = c.currentMediaItem?.mediaId != estacao.id
        if (outra || c.playbackState == Player.STATE_IDLE) {
            if (outra || trocar || c.mediaItemCount == 0) c.setMediaItem(estacao.comoItem())
            c.prepare()
        }
        c.volume = _estado.value.volume
        c.play()
        _estado.update { it.copy(estacao = estacao, musica = if (outra) null else it.musica) }
        escopo.launch {
            context.prefsRadio.edit {
                it[chaveEstacao] = estacao.id
                it[chaveAtiva] = true
            }
        }
    }

    private fun ler(player: Player) {
        val estacao = player.currentMediaItem?.mediaId?.let(::estacaoPorId) ?: _estado.value.estacao
        val meta = player.mediaMetadata
        val titulo = meta.title?.toString()
        // Sem musica ainda, o item mostra a estacao (ver `comoItem`).
        val musica = if (titulo.isNullOrBlank() || titulo == estacao.nome) {
            null
        } else {
            Musica(titulo, meta.artist?.toString()?.takeIf { it != estacao.nome }.orEmpty())
        }
        val ativa = player.playWhenReady && player.playbackState != Player.STATE_IDLE
        _estado.update {
            it.copy(
                estacao = estacao,
                tocando = player.isPlaying,
                carregando = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
                musica = musica,
            )
        }
        if (ativa != ativaGravada) {
            ativaGravada = ativa
            escopo.launch { context.prefsRadio.edit { it[chaveAtiva] = ativa } }
        }
    }
}
