package com.beazeth.notifier.data

import android.content.Context
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.avisos.descansoDe
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.data.local.PendenciaEntity
import com.beazeth.notifier.sync.SyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Um temporizador como a conta o guarda -- o mesmo formato do site
 * (`app/db/pomodoros.py`, `core/pomodoro-conta.js`).
 *
 * [fimEm] e o instante em que a fase termina, se ela corre; [restanteMs], o que
 * falta, se esta pausada. [nome] e [posicao] so valem para os outros pomodoros;
 * [descansoAutomatico], so para o principal.
 */
@Serializable
data class PomodoroJson(
    val id: String = "",
    val fase: String = PomodoroNaConta.PARADO,
    val minutos: Int = 25,
    val fimEm: Long = 0L,
    val restanteMs: Long = 0L,
    val totalMs: Long = 0L,
    val nome: String = "",
    val posicao: Int = 0,
    val descansoAutomatico: Boolean = true,
)

/**
 * O pomodoro e da conta: o que se comeca no celular aparece no site, e o
 * contrario.
 *
 * Ate a 1.13 ele morava so no DataStore, e o que se criava no PC nunca chegava
 * aqui. O relogio continua aqui -- o alarme, a festa, o descanso --, e o que
 * passa pela conta e o ESTADO de cada temporizador depois de cada acao de quem
 * esta na tela: comecar, pausar, pular o descanso, zerar, trocar o tempo,
 * renomear, criar, remover.
 *
 * O que acontece sozinho nao sobe. O descanso que entra no fim do foco e
 * calculado igual dos dois lados, a partir do MESMO fim (ver
 * `Lembretes.entregarPomodoro`), entao nao ha o que mandar -- e mandar faria
 * cada aparelho sobrescrever o outro com a sua versao, segundos diferente.
 *
 * A subida vai pela fila, como toda escrita: sem rede, espera. A descida vem
 * pela sincronizacao (`Sincronizador.aplicar`), e nao toca no temporizador que
 * ainda tem escrita na fila -- a versao daqui e a mais nova.
 */
object PomodoroNaConta {
    const val ALVO = "pomodoro"
    const val PRINCIPAL = "principal"
    const val PARADO = "parado"
    const val FOCO = "foco"
    const val DESCANSO = "descanso"

    // ------------------------------------------------------------ subida

    suspend fun enviarPrincipal(context: Context) {
        val prefs = Preferencias(context)
        val corpo = principalNaConta(
            minutos = prefs.pomoMinutos.first(),
            fimEm = prefs.pomoFimEm.first(),
            restante = prefs.pomoRestante.first(),
            descansoAte = prefs.pomoDescansoAte.first(),
            automatico = prefs.descansoAutomatico.first(),
        )
        enfileirar(context, "PUT", PRINCIPAL, corpo)
    }

    /** O sub como esta agora -- ou a remocao, se ele ja nao existe. */
    suspend fun enviarSub(context: Context, id: String) {
        val lista = Preferencias(context).subsDoPomodoro.first()
        val posicao = lista.indexOfFirst { it.id == id }
        if (posicao == -1) {
            enfileirar(context, "DELETE", id, null)
        } else {
            enfileirar(context, "PUT", id, lista[posicao].naConta(posicao))
        }
    }

    /**
     * Uma escrita por temporizador na fila: o PUT leva o estado INTEIRO, entao
     * o mais novo torna os anteriores inuteis. Sem isto, renomear um sub
     * letra por letra enfileiraria um pedido por tecla.
     */
    private suspend fun enfileirar(context: Context, metodo: String, id: String, corpo: JsonObject?) {
        if (TokenStore(context).modoLocalAtivo()) return
        val pendencias = BancoLocal.obter(context).pendencias()
        val caminho = "/api/pomodoro/$id"
        pendencias.descartar(ALVO, caminho)
        pendencias.enfileirar(
            PendenciaEntity(
                metodo = metodo,
                caminho = caminho,
                corpo = corpo?.toString(),
                idProvisorio = null,
                entidade = ALVO,
                criadoEm = System.currentTimeMillis(),
            )
        )
        SyncWorker.agora(context)
    }

    // ------------------------------------------------------------ descida

    /**
     * Aplica o que mudou na conta. [devendo] diz se o temporizador tem escrita
     * na fila -- esses ficam como estao.
     */
    suspend fun aplicar(
        context: Context,
        linhas: List<PomodoroJson>,
        apagados: List<String>,
        devendo: (String) -> Boolean,
    ) {
        val prefs = Preferencias(context)
        val agora = System.currentTimeMillis()
        var mexeuNoPrincipal = false

        linhas.firstOrNull { it.id == PRINCIPAL }?.let { conta ->
            if (!devendo(PRINCIPAL) && conta.diferenteDoPrincipal(prefs, agora)) {
                aplicarPrincipal(prefs, conta, agora)
                mexeuNoPrincipal = true
            }
        }

        val subsDaConta = linhas.filter { it.id != PRINCIPAL }.sortedBy { it.posicao }
        val lista = prefs.subsDoPomodoro.first()
        var nova = lista.filterNot { it.id in apagados && !devendo(it.id) }
        var mudou = nova.size != lista.size
        val automatico = prefs.descansoAutomatico.first()

        for (conta in subsDaConta) {
            if (devendo(conta.id)) continue
            val indice = nova.indexOfFirst { it.id == conta.id }
            if (indice == -1) {
                if (nova.size >= MAXIMO_DE_SUBS) continue
                val novo = SubPomodoro(id = conta.id).comConta(conta, agora, automatico)
                // Na ordem da conta, ate onde a lista daqui deixa.
                val onde = conta.posicao.coerceIn(0, nova.size)
                nova = nova.take(onde) + novo + nova.drop(onde)
                mudou = true
            } else if (!mesmo(nova[indice].naConta(indice, agora), conta)) {
                nova = nova.mapIndexed { i, sub ->
                    if (i == indice) sub.comConta(conta, agora, automatico) else sub
                }
                mudou = true
            }
        }

        if (mudou) {
            prefs.salvarSubs(nova)
            Lembretes.subsMudaram(context)
        }
        if (mexeuNoPrincipal) Lembretes.pomodoroMudou(context)
    }

    /**
     * Primeira conversa deste aparelho sobre pomodoros: le a conta INTEIRA (o
     * `/api/sync` so traz o que mudou desde a ultima vez, e a tabela pode ter
     * nascido antes disso) e sobe o que este aparelho tem e a conta nao --
     * subs montados antes de existir sincronizacao nao se perdem.
     */
    suspend fun semear(context: Context, token: String): Boolean {
        val prefs = Preferencias(context)
        if (prefs.pomoSemeado.first()) return true
        val r = Api.pomodoros(token)
        if (r !is Api.Resultado.Ok) return false
        val conta = r.corpo.pomodoros
        val ids = conta.map { it.id }.toSet()

        if (PRINCIPAL !in ids) enviarPrincipal(context)
        prefs.subsDoPomodoro.first().filter { it.id !in ids }.forEach { enviarSub(context, it.id) }

        val naFila = BancoLocal.obter(context).pendencias().todas()
        aplicar(context, conta, emptyList()) { id ->
            naFila.any { it.entidade == ALVO && it.caminho.endsWith("/$id") }
        }
        prefs.marcarPomodoroSemeado()
        return true
    }

    private suspend fun aplicarPrincipal(prefs: Preferencias, conta: PomodoroJson, agora: Long) {
        val minutos = conta.minutos.coerceIn(1, 600)
        val total = conta.totalOuPadrao()
        prefs.definirDescansoAutomatico(conta.descansoAutomatico)
        when {
            conta.fase == FOCO && conta.fimEm > 0L -> {
                prefs.salvarPomodoro(minutos, conta.fimEm, (total / 1_000L).toInt())
                if (conta.fimEm <= agora) {
                    // Acabou no outro aparelho: quem viu ja anunciou. Aqui
                    // entra direto no descanso, se ele ainda corre.
                    val fimDoDescanso = conta.fimEm + descansoDe(minutos) * 60_000L
                    prefs.marcarDescansoAte(
                        if (conta.descansoAutomatico && fimDoDescanso > agora) fimDoDescanso else 0L,
                    )
                    prefs.marcarPomodoroAvisado(conta.fimEm)
                    prefs.marcarPomodoroFestejado(conta.fimEm)
                } else {
                    prefs.marcarDescansoAte(0L)
                }
            }

            conta.fase == FOCO && conta.restanteMs > 0L -> {
                prefs.marcarDescansoAte(0L)
                prefs.salvarPomodoro(minutos, 0L, ((conta.restanteMs + 999L) / 1_000L).toInt())
            }

            conta.fase == DESCANSO && conta.fimEm > 0L -> {
                // O foco acabou onde o descanso comecou.
                val fimDoFoco = conta.fimEm - total
                prefs.salvarPomodoro(minutos, fimDoFoco, minutos * 60)
                prefs.marcarDescansoAte(conta.fimEm)
                prefs.marcarPomodoroAvisado(fimDoFoco)
                prefs.marcarPomodoroFestejado(fimDoFoco)
            }

            // Parado -- e o descanso PAUSADO do site, que este app nao tem:
            // aqui pausar o descanso e pular, e ele termina.
            else -> {
                prefs.marcarDescansoAte(0L)
                prefs.salvarPomodoro(minutos, 0L, minutos * 60)
            }
        }
    }

    private suspend fun PomodoroJson.diferenteDoPrincipal(prefs: Preferencias, agora: Long): Boolean {
        val daqui = principalNaConta(
            minutos = prefs.pomoMinutos.first(),
            fimEm = prefs.pomoFimEm.first(),
            restante = prefs.pomoRestante.first(),
            descansoAte = prefs.pomoDescansoAte.first(),
            automatico = prefs.descansoAutomatico.first(),
            agora = agora,
        )
        return !mesmo(daqui, this) || daqui["descansoAutomatico"].toString() != descansoAutomatico.toString()
    }

    // ------------------------------------------------------------ forma

    internal fun principalNaConta(
        minutos: Int,
        fimEm: Long,
        restante: Int,
        descansoAte: Long,
        automatico: Boolean,
        agora: Long = System.currentTimeMillis(),
    ): JsonObject = buildJsonObject {
        escreverFase(minutos, fimEm, restante, descansoAte, agora)
        put("minutos", minutos)
        put("descansoAutomatico", automatico)
    }

    internal fun SubPomodoro.naConta(posicao: Int, agora: Long = System.currentTimeMillis()): JsonObject =
        buildJsonObject {
            escreverFase(minutos, fimEm, restante, descansoAte, agora)
            put("minutos", minutos)
            put("nome", nome)
            put("posicao", posicao)
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.escreverFase(
        minutos: Int,
        fimEm: Long,
        restante: Int,
        descansoAte: Long,
        agora: Long,
    ) {
        when {
            descansoAte > agora -> {
                put("fase", DESCANSO)
                put("fimEm", descansoAte)
                put("restanteMs", 0L)
                put("totalMs", descansoDe(minutos) * 60_000L)
            }
            fimEm > 0L -> {
                put("fase", FOCO)
                put("fimEm", fimEm)
                put("restanteMs", 0L)
                put("totalMs", if (restante > 0) restante * 1_000L else minutos * 60_000L)
            }
            restante in 1 until minutos * 60 -> {
                put("fase", FOCO)
                put("fimEm", 0L)
                put("restanteMs", restante * 1_000L)
                put("totalMs", minutos * 60_000L)
            }
            else -> {
                put("fase", PARADO)
                put("fimEm", 0L)
                put("restanteMs", 0L)
                put("totalMs", 0L)
            }
        }
    }

    private fun SubPomodoro.comConta(conta: PomodoroJson, agora: Long, automatico: Boolean): SubPomodoro {
        val minutos = conta.minutos.coerceIn(1, 600)
        val base = copy(nome = conta.nome.take(MAXIMO_DO_NOME_DO_SUB), minutos = minutos)
        val total = conta.totalOuPadrao()
        return when {
            conta.fase == FOCO && conta.fimEm > 0L -> {
                val corre = base.copy(fimEm = conta.fimEm, restante = (total / 1_000L).toInt(), descansoAte = 0L)
                if (conta.fimEm > agora) {
                    corre
                } else {
                    val fimDoDescanso = conta.fimEm + descansoDe(minutos) * 60_000L
                    corre.copy(
                        avisado = conta.fimEm,
                        festejado = conta.fimEm,
                        descansoAte = if (automatico && fimDoDescanso > agora) fimDoDescanso else 0L,
                    )
                }
            }

            conta.fase == FOCO && conta.restanteMs > 0L ->
                base.copy(fimEm = 0L, restante = ((conta.restanteMs + 999L) / 1_000L).toInt(), descansoAte = 0L)

            conta.fase == DESCANSO && conta.fimEm > 0L -> {
                val fimDoFoco = conta.fimEm - total
                base.copy(
                    fimEm = fimDoFoco,
                    restante = minutos * 60,
                    descansoAte = conta.fimEm,
                    avisado = fimDoFoco,
                    festejado = fimDoFoco,
                )
            }

            else -> base.copy(fimEm = 0L, restante = minutos * 60, descansoAte = 0L)
        }
    }

    private fun PomodoroJson.totalOuPadrao(): Long = when {
        totalMs > 0L -> totalMs
        fase == DESCANSO -> descansoDe(minutos) * 60_000L
        else -> minutos * 60_000L
    }

    /** O mesmo estado? A posicao fica de fora, como no site. */
    private fun mesmo(daqui: JsonObject, conta: PomodoroJson): Boolean {
        fun texto(chave: String) = daqui[chave]?.toString()?.trim('"')
        return texto("fase") == conta.fase &&
            texto("fimEm") == conta.fimEm.toString() &&
            texto("restanteMs") == conta.restanteMs.toString() &&
            texto("minutos") == conta.minutos.toString() &&
            (daqui["nome"] == null || texto("nome") == conta.nome)
    }
}
