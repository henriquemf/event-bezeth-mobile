package com.beazeth.notifier.data

import com.beazeth.notifier.avisos.descansoDe
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Os outros pomodoros -- ate dez, alem do principal.
 *
 * ## Por que sao uma lista guardada, e nao dez pares de chaves
 *
 * O principal tem uma chave para cada coisa no DataStore (`pomo_fim_em`,
 * `pomo_restante`...). Repetir isso dez vezes daria sessenta chaves com numero
 * no nome, e cada leitura seria um `combine` de sessenta fluxos. Aqui a lista
 * inteira e UM texto JSON numa chave so: um fluxo, uma gravacao, e a ordem dos
 * cartoes vem de graca.
 *
 * ## O que cada sub guarda e o mesmo que o principal guarda
 *
 * [fimEm] e o INSTANTE em que a contagem termina, e nao os segundos que faltam
 * -- a mesma escolha de `Preferencias.pomoFimEm`, e pelo mesmo motivo: o
 * instante nao envelhece, entao fechar o app nao desalinha nada. [restante] so
 * vale com [fimEm] zerado, e e o que sobrou quando alguem pausou.
 *
 * Os tres carimbos ([avisado], [creditado], [festejado]) existem porque ninguem
 * avisa quando um pomodoro termina: quem descobre e a primeira coisa que olhar
 * para o relogio depois -- o alarme, a tela, ou o app reabrindo dois dias
 * adiante. Sem eles, reabrir o app soltaria confete de novo, toda vez.
 *
 * [dobrado] e estado de tela e mesmo assim mora aqui: o registro inteiro ja e
 * do aparelho (nada disto sobe para o servidor), e uma segunda chave so para
 * lembrar qual cartao estava minimizado seria uma segunda lista para
 * desencontrar desta.
 */
@Serializable
data class SubPomodoro(
    val id: String,
    val nome: String = "",
    val minutos: Int = 25,
    val fimEm: Long = 0L,
    val restante: Int = minutos * 60,
    val descansoAte: Long = 0L,
    val avisado: Long = 0L,
    val creditado: Long = 0L,
    val festejado: Long = 0L,
    val dobrado: Boolean = false,
)

/** Espelha `MAX_SUBS` em `app/static/js/core/pomodoro.js`. */
const val MAXIMO_DE_SUBS = 10

/** Espelha `MAX_NOME` do mesmo arquivo. */
const val MAXIMO_DO_NOME_DO_SUB = 24

/**
 * Um sub visto AGORA: qual fase corre, quanto falta, e quanto ja andou.
 *
 * O irmao de `EstadoPomodoro`, e com a mesma divisao entre [minutos] (a duracao
 * da fase que corre, que no descanso e a do intervalo) e [escolhidos] (o tempo
 * de foco, que o campo de minutos mostra). Sao duas perguntas diferentes, e
 * confundi-las foi o defeito que fez o seletor do principal acender "5 Respiro"
 * no meio do descanso.
 */
data class EstadoDoSub(
    val id: String,
    val nome: String,
    val escolhidos: Int,
    val minutos: Int,
    val restante: Int,
    val correndo: Boolean,
    val emDescanso: Boolean,
    val fimEm: Long,
    val dobrado: Boolean,
) {
    val total: Int get() = minutos * 60

    /** Fracao ja DECORRIDA, como o `--pomo-progress` do site. */
    val progresso: Float get() = if (total > 0) 1f - (restante.toFloat() / total) else 0f
}

/** O relogio decide tudo: nada aqui depende de alguem ter contado. */
fun SubPomodoro.agora(): EstadoDoSub {
    val faltaDoDescanso = if (descansoAte > 0L) segundosAte(descansoAte) else 0

    // O descanso manda enquanto corre, como no principal: durante ele o `fimEm`
    // do foco ja esta vencido e continua gravado.
    if (faltaDoDescanso > 0) {
        return EstadoDoSub(
            id = id, nome = nome, escolhidos = minutos, minutos = descansoDe(minutos),
            restante = faltaDoDescanso, correndo = true, emDescanso = true,
            fimEm = descansoAte, dobrado = dobrado,
        )
    }

    if (fimEm > 0L) {
        val falta = segundosAte(fimEm)
        return EstadoDoSub(
            id = id, nome = nome, escolhidos = minutos, minutos = minutos,
            restante = falta, correndo = falta > 0, emDescanso = false,
            fimEm = fimEm, dobrado = dobrado,
        )
    }

    return EstadoDoSub(
        id = id, nome = nome, escolhidos = minutos, minutos = minutos,
        restante = restante, correndo = false, emDescanso = false,
        fimEm = 0L, dobrado = dobrado,
    )
}

/**
 * O nome que aparece no cartao e no aviso.
 *
 * Sem nome escolhido, a posicao serve: o principal e o primeiro, entao o
 * primeiro sub e o segundo pomodoro da tela. Numerar pela posicao em vez de
 * guardar "Pomodoro 3" no registro evita dois cartoes com o mesmo nome depois
 * de alguem remover um do meio.
 */
fun nomeDoSub(nome: String, indice: Int): String =
    nome.ifBlank { "Pomodoro ${indice + 2}" }

/** Quantos segundos faltam ate [instante], nunca negativo. */
internal fun segundosAte(instante: Long): Int =
    (((instante - System.currentTimeMillis()) + 999) / 1_000).coerceAtLeast(0).toInt()

/* ------------------------------------------------------------------ disco */

/* `ignoreUnknownKeys` para uma versao futura poder ler o que uma versao com
   mais campos gravou, em vez de perder a lista inteira. */
private val codec = Json { ignoreUnknownKeys = true }

/* O serializador explicito em vez do `encodeToString<T>` reificado: a versao da
   biblioteca aqui pede a extensao `kotlinx.serialization.encodeToString` para a
   forma curta funcionar, e dizer qual e o serializador e mais claro do que
   depender de um import solto. */
private val daLista = ListSerializer(SubPomodoro.serializer())

/**
 * Le a lista gravada. Texto quebrado vira lista vazia, e nao excecao: isto roda
 * dentro de um `map` de DataStore, e uma excecao ali derrubaria todo fluxo que
 * depende das preferencias -- inclusive o tema.
 */
internal fun lerSubs(texto: String?): List<SubPomodoro> {
    if (texto.isNullOrBlank()) return emptyList()
    return runCatching { codec.decodeFromString(daLista, texto) }
        .getOrDefault(emptyList())
}

internal fun escreverSubs(lista: List<SubPomodoro>): String =
    codec.encodeToString(daLista, lista.take(MAXIMO_DE_SUBS))
