package com.beazeth.notifier.radio

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Uma estacao da radio lo-fi.
 *
 * A MESMA lista de `app/services/radio.py`, no servidor do site -- o motivo de
 * cada uma estar aqui (HTTPS, nome da musica no stream, aceitar tocar fora do
 * site delas) esta escrito la. Mexeu numa, mexe na outra.
 *
 * Aqui o app nao pergunta ao servidor o que esta tocando: o ExoPlayer le o
 * metadado ICY de dentro do proprio stream. E por isso que a radio funciona
 * igual no modo sem conta.
 */
data class Estacao(
    val id: String,
    val nome: String,
    val descricao: String,
    val stream: String,
    val site: String,
    val fonte: String,
)

val ESTACOES = listOf(
    Estacao(
        id = "lofi",
        nome = "Lo-fi",
        descricao = "lo-fi hip hop para estudar e relaxar",
        stream = "https://stream.laut.fm/lofi",
        site = "https://laut.fm/lofi",
        fonte = "laut.fm",
    ),
    Estacao(
        id = "chillhop",
        nome = "Chillhop",
        descricao = "batidas chill, jazz e hip hop suave",
        stream = "https://ilm.stream12.radiohost.de/ilm_ilovechillhop_mp3-192",
        site = "https://www.ilovemusic.de/ilovechillhop/",
        fonte = "I Love Music",
    ),
    Estacao(
        id = "hotmix",
        nome = "Hotmix Lo-Fi",
        descricao = "lo-fi tranquilo, sem pressa",
        stream = "https://streaming.hotmixradio.com/hotmix-lofi-en-mp3",
        site = "https://www.hotmixradio.fr/",
        fonte = "Hotmix Radio",
    ),
    Estacao(
        id = "study",
        nome = "Study",
        descricao = "instrumental para foco e estudo",
        stream = "https://study-high.rautemusik.fm/",
        site = "https://www.rautemusik.fm/study/",
        fonte = "RauteMusik",
    ),
)

fun estacaoPorId(id: String?): Estacao = ESTACOES.firstOrNull { it.id == id } ?: ESTACOES.first()

/** O nome da musica, separado: `titulo` e `artista` (este pode vir vazio). */
data class Musica(val titulo: String, val artista: String)

/**
 * "Artista - Musica" vira os dois campos, como `_separar` do servidor.
 *
 * A Hotmix pendura codigos internos depois de `||`; o que vem depois da
 * primeira barra dupla nao e para gente ler.
 */
fun separarMusica(bruto: String?): Musica? {
    val texto = bruto.orEmpty().substringBefore("||").trim().replace(Regex("\\s+"), " ").take(160)
    if (texto.isEmpty()) return null
    val corte = texto.indexOf(" - ")
    if (corte < 0) return Musica(titulo = texto, artista = "")
    return Musica(titulo = texto.substring(corte + 3).trim(), artista = texto.substring(0, corte).trim())
}

/**
 * O item que o player toca, com o que a notificacao e a tela de bloqueio
 * mostram. Sem musica ainda, mostram a estacao.
 */
fun Estacao.comoItem(musica: Musica? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(stream)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(musica?.titulo ?: nome)
                .setArtist(musica?.artista?.ifEmpty { nome } ?: descricao)
                .setStation(nome)
                .setAlbumTitle("Rádio lo-fi · $nome")
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()
        )
        .build()
