package com.beazeth.notifier.data

import java.util.regex.Pattern

/**
 * O texto formatado dos post-its: a gramatica, a forma canonica e as marcas.
 *
 * E a mesma gramatica de `app/texto_rico.py` no servidor e de
 * `js/pages/notes/rich.js` no site, com os mesmos casos: seis marcas --
 * `<b> <i> <u> <s> <a href> <br>` --, leitura TOLERANTE (o que nao e marca e
 * texto, entao um post-it antigo de texto cru e lido igual a antes) e escrita
 * CANONICA (marcas na ordem a > b > i > u > s, vizinhos de mesmo estilo
 * juntos, quebra como `<br>`). Os tres escrevendo a mesma string e o que
 * impede a sincronizacao de achar mudanca onde nao houve: se o app escrevesse
 * `<i><b>` e o servidor devolvesse `<b><i>`, todo post-it formatado "mudaria"
 * a cada ida e volta.
 *
 * ## Por que marcas por intervalo, e nao trechos
 *
 * O site e o servidor pensam em trechos ("este pedaco e negrito e italico").
 * Aqui quem edita e um campo de texto do Compose, que so conhece o texto puro
 * e a posicao do cursor -- entao a formatacao vive AO LADO do texto, como
 * intervalos: "negrito de 18 a 21". A cada tecla, [ajustar] empurra os
 * intervalos para onde o texto foi. Na ida e na volta para HTML os dois
 * modelos se convertem sem perda.
 */

/** Espelha `MAX_TEXTO` em `app/texto_rico.py`: caracteres de TEXTO. */
const val MAXIMO_DO_TEXTO = 2000

enum class TipoDeMarca { NEGRITO, ITALICO, SUBLINHADO, RISCADO, LINK }

/** Uma formatacao sobre `[inicio, fim)` do texto. [link] so vale para [TipoDeMarca.LINK]. */
data class Marca(val inicio: Int, val fim: Int, val tipo: TipoDeMarca, val link: String? = null)

/** O estilo de um pedaco contiguo, depois de todas as marcas somadas. */
data class Estilo(
    val negrito: Boolean = false,
    val italico: Boolean = false,
    val sublinhado: Boolean = false,
    val riscado: Boolean = false,
    val link: String? = null,
)

data class Pedaco(val texto: String, val estilo: Estilo)

data class TextoRico(val texto: String, val marcas: List<Marca> = emptyList()) {

    /** O texto em pedacos de estilo uniforme -- o que se escreve e se pinta. */
    fun pedacos(): List<Pedaco> {
        if (texto.isEmpty()) return emptyList()
        val cortes = sortedSetOf(0, texto.length)
        for (m in marcas) {
            cortes.add(m.inicio.coerceIn(0, texto.length))
            cortes.add(m.fim.coerceIn(0, texto.length))
        }
        val lista = cortes.toList()
        val saida = mutableListOf<Pedaco>()
        for (k in 0 until lista.size - 1) {
            val a = lista[k]
            val b = lista[k + 1]
            if (a >= b) continue
            val ativas = marcas.filter { it.inicio <= a && it.fim >= b }
            val estilo = Estilo(
                negrito = ativas.any { it.tipo == TipoDeMarca.NEGRITO },
                italico = ativas.any { it.tipo == TipoDeMarca.ITALICO },
                sublinhado = ativas.any { it.tipo == TipoDeMarca.SUBLINHADO },
                riscado = ativas.any { it.tipo == TipoDeMarca.RISCADO },
                link = ativas.lastOrNull { it.tipo == TipoDeMarca.LINK }?.link,
            )
            val ultimo = saida.lastOrNull()
            if (ultimo != null && ultimo.estilo == estilo) {
                saida[saida.size - 1] = ultimo.copy(texto = ultimo.texto + texto.substring(a, b))
            } else {
                saida.add(Pedaco(texto.substring(a, b), estilo))
            }
        }
        return saida
    }

    /** A forma canonica, cortada no limite de texto. */
    fun paraHtml(): String {
        val sb = StringBuilder()
        var resta = MAXIMO_DO_TEXTO
        for (pedaco in pedacos()) {
            if (resta <= 0) break
            val texto = pedaco.texto.take(resta)
            resta -= pedaco.texto.length
            val e = pedaco.estilo
            val fecha = StringBuilder()
            if (e.link != null) {
                sb.append("<a href=\"").append(escapar(e.link).replace("\"", "&quot;")).append("\">")
                fecha.insert(0, "</a>")
            }
            for ((ativo, marca) in listOf(e.negrito to "b", e.italico to "i", e.sublinhado to "u", e.riscado to "s")) {
                if (ativo) {
                    sb.append('<').append(marca).append('>')
                    fecha.insert(0, "</$marca>")
                }
            }
            sb.append(escapar(texto).replace("\n", "<br>")).append(fecha)
        }
        return sb.toString()
    }

    /** O intervalo inteiro ja tem este estilo? E o que decide se o botao liga ou desliga. */
    fun tem(tipo: TipoDeMarca, inicio: Int, fim: Int): Boolean {
        if (inicio >= fim) return false
        var cursor = inicio
        for (m in marcas.filter { it.tipo == tipo }.sortedBy { it.inicio }) {
            if (m.inicio > cursor) break
            if (m.fim > cursor) cursor = m.fim
            if (cursor >= fim) return true
        }
        return false
    }

    /** O link que cobre a posicao, se houver. */
    fun linkEm(posicao: Int): String? =
        marcas.lastOrNull { it.tipo == TipoDeMarca.LINK && it.inicio <= posicao && it.fim > posicao }?.link

    /** Liga o estilo no intervalo -- ou desliga, se ele ja cobria tudo. */
    fun alternar(tipo: TipoDeMarca, inicio: Int, fim: Int): TextoRico {
        if (inicio >= fim) return this
        val semNoIntervalo = recortar(marcas, tipo, inicio, fim)
        return if (tem(tipo, inicio, fim)) {
            copy(marcas = semNoIntervalo)
        } else {
            copy(marcas = juntar(semNoIntervalo + Marca(inicio, fim, tipo)))
        }
    }

    /** Poe o link no intervalo, ou tira com `null`. So um link por pedaco de texto. */
    fun comLink(inicio: Int, fim: Int, link: String?): TextoRico {
        if (inicio >= fim) return this
        val sem = recortar(marcas, TipoDeMarca.LINK, inicio, fim)
        return copy(marcas = if (link == null) sem else juntar(sem + Marca(inicio, fim, TipoDeMarca.LINK, link)))
    }

    /**
     * O texto mudou de [texto] para [novo]: empurra cada marca para onde o
     * texto dela foi.
     *
     * Um campo de texto so conta o texto novo, e nao o que aconteceu -- entao
     * a mudanca e deduzida pelo que os dois tem em comum no comeco e no fim.
     * Escrever logo depois de um trecho em negrito continua em negrito, como em
     * todo editor; logo depois de um LINK, nao: continuar o link levaria o
     * resto da frase junto para o endereco.
     */
    fun ajustar(novo: String): TextoRico {
        if (novo == texto) return this
        val antigo = texto
        var p = 0
        val menor = minOf(antigo.length, novo.length)
        while (p < menor && antigo[p] == novo[p]) p++
        var s = 0
        while (s < menor - p && antigo[antigo.length - 1 - s] == novo[novo.length - 1 - s]) s++
        val removido = antigo.length - s - p
        val inserido = novo.length - s - p
        val fimDoRemovido = p + removido

        val ajustadas = marcas.mapNotNull { m ->
            val estende = m.tipo != TipoDeMarca.LINK
            val a = when {
                m.inicio < p -> m.inicio
                m.inicio >= fimDoRemovido -> m.inicio - removido + inserido
                else -> p + inserido
            }
            val b = when {
                m.fim < p -> m.fim
                m.fim == p -> if (estende) p + inserido else p
                m.fim >= fimDoRemovido -> m.fim - removido + inserido
                else -> if (estende) p + inserido else p
            }
            if (a < b) m.copy(inicio = a, fim = b) else null
        }
        return TextoRico(novo, juntar(ajustadas))
    }

    companion object {
        /** Texto sem formatacao nenhuma -- o que vem do campo de criar post-it. */
        fun dePuro(texto: String) = TextoRico(texto)
    }
}

// ------------------------------------------------------------------ leitura

private val MARCA = Pattern.compile("<(/?)(b|strong|i|em|u|s|strike|del)>", Pattern.CASE_INSENSITIVE)
private val QUEBRA = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE)
private val ABRE_LINK = Pattern.compile("<a\\s+href=\"([^\"]*)\"\\s*>", Pattern.CASE_INSENSITIVE)
private val FECHA_LINK = Pattern.compile("</a>", Pattern.CASE_INSENSITIVE)
private val ENTIDADE = Pattern.compile("&(amp|lt|gt|quot|#39|nbsp);")
private val ENTIDADES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "#39" to "'", "nbsp" to " ")
private val ESQUEMA_ACEITO = Regex("^(https?://|mailto:)", RegexOption.IGNORE_CASE)
private val SINONIMOS = mapOf(
    "b" to TipoDeMarca.NEGRITO, "strong" to TipoDeMarca.NEGRITO,
    "i" to TipoDeMarca.ITALICO, "em" to TipoDeMarca.ITALICO,
    "u" to TipoDeMarca.SUBLINHADO,
    "s" to TipoDeMarca.RISCADO, "strike" to TipoDeMarca.RISCADO, "del" to TipoDeMarca.RISCADO,
)

private fun desescapar(texto: String): String {
    val m = ENTIDADE.matcher(texto)
    val sb = StringBuffer()
    while (m.find()) m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(ENTIDADES.getValue(m.group(1)!!)))
    m.appendTail(sb)
    return sb.toString()
}

/** O endereco limpo, ou `null` se o esquema nao for http, https ou mailto. */
fun linkAceito(href: String?): String? {
    val limpo = desescapar(href ?: "").trim()
    return if (ESQUEMA_ACEITO.containsMatchIn(limpo)) limpo else null
}

private fun escapar(texto: String) = texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/**
 * Le o formato tolerante. Marca fechada sem ter sido aberta e ignorada, marca
 * aberta sem fechar vale ate o fim -- o que chega pela sincronizacao nao pode
 * derrubar o quadro.
 */
fun lerTextoRico(bruto: String): TextoRico {
    val fonte = bruto.replace("\r\n", "\n").replace('\r', '\n')
    val texto = StringBuilder()
    val abertas = mutableMapOf<TipoDeMarca, ArrayDeque<Int>>()
    // Link recusado entra como null: o `</a>` dele ainda precisa fechar a
    // marca certa, e nao a do link de fora.
    val links = ArrayDeque<String?>()
    val marcas = mutableListOf<Marca>()

    // Vale o link mais INTERNO que foi aceito, como no servidor. Em vez de um
    // intervalo por `<a>` -- que se sobreporiam, e ai qual vence? --, guarda-se
    // o link ativo e fecha-se o intervalo dele cada vez que ele troca.
    var linkAtivo: String? = null
    var inicioDoLink = 0
    fun reavaliarLink() {
        val agora = links.lastOrNull { it != null }
        if (agora == linkAtivo) return
        if (linkAtivo != null) marcas.add(Marca(inicioDoLink, texto.length, TipoDeMarca.LINK, linkAtivo))
        linkAtivo = agora
        inicioDoLink = texto.length
    }

    fun casa(p: Pattern, i: Int): java.util.regex.Matcher? =
        p.matcher(fonte).region(i, fonte.length).takeIf { it.lookingAt() }

    // Sem `?.let { ... continue }`: `continue` dentro de lambda e recurso
    // experimental no Kotlin 2.0, e o build recusa.
    var i = 0
    while (i < fonte.length) {
        val c = fonte[i]
        if (c == '<') {
            val marca = casa(MARCA, i)
            if (marca != null) {
                val tipo = SINONIMOS.getValue(marca.group(2)!!.lowercase())
                val pilha = abertas.getOrPut(tipo) { ArrayDeque() }
                if (marca.group(1)!!.isEmpty()) {
                    pilha.addLast(texto.length)
                } else if (pilha.isNotEmpty()) {
                    val inicio = pilha.removeLast()
                    // So a marca de FORA conta: `<b><b>x</b></b>` e um negrito so.
                    if (pilha.isEmpty()) marcas.add(Marca(inicio, texto.length, tipo))
                }
                i = marca.end()
                continue
            }
            val quebra = casa(QUEBRA, i)
            if (quebra != null) {
                texto.append('\n')
                i = quebra.end()
                continue
            }
            val abre = casa(ABRE_LINK, i)
            if (abre != null) {
                links.addLast(linkAceito(abre.group(1)))
                reavaliarLink()
                i = abre.end()
                continue
            }
            val fecha = casa(FECHA_LINK, i)
            if (fecha != null) {
                links.removeLastOrNull()
                reavaliarLink()
                i = fecha.end()
                continue
            }
        } else if (c == '&') {
            val entidade = casa(ENTIDADE, i)
            if (entidade != null) {
                texto.append(ENTIDADES.getValue(entidade.group(1)!!))
                i = entidade.end()
                continue
            }
        }
        texto.append(c)
        i++
    }
    // O que ficou aberto vale ate o fim.
    for ((tipo, pilha) in abertas) pilha.firstOrNull()?.let { marcas.add(Marca(it, texto.length, tipo)) }
    links.clear()
    reavaliarLink()

    return TextoRico(texto.toString(), juntar(marcas))
}

/** Tira intervalos vazios e funde os do mesmo tipo (e mesmo link) que se tocam. */
private fun juntar(marcas: List<Marca>): List<Marca> {
    val saida = mutableListOf<Marca>()
    for ((_, grupo) in marcas.filter { it.inicio < it.fim }.groupBy { it.tipo to it.link }) {
        var atual: Marca? = null
        for (m in grupo.sortedBy { it.inicio }) {
            atual = when {
                atual == null -> m
                m.inicio <= atual.fim -> atual.copy(fim = maxOf(atual.fim, m.fim))
                else -> { saida.add(atual); m }
            }
        }
        atual?.let { saida.add(it) }
    }
    return saida.sortedWith(compareBy({ it.inicio }, { it.tipo }))
}

/** As marcas de [tipo] sem o pedaco `[inicio, fim)` -- as de fora ficam em dois. */
private fun recortar(marcas: List<Marca>, tipo: TipoDeMarca, inicio: Int, fim: Int): List<Marca> =
    marcas.flatMap { m ->
        if (m.tipo != tipo || m.fim <= inicio || m.inicio >= fim) {
            listOf(m)
        } else {
            listOfNotNull(
                m.copy(fim = inicio).takeIf { it.inicio < it.fim },
                m.copy(inicio = fim).takeIf { it.inicio < it.fim },
            )
        }
    }
