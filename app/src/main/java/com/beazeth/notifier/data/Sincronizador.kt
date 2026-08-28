package com.beazeth.notifier.data

import android.content.Context
import com.beazeth.notifier.data.local.AguaDiaEntity
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.data.local.BlocoEntity
import com.beazeth.notifier.data.local.ConfigAguaEntity
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.data.local.NotaEntity
import com.beazeth.notifier.data.local.PendenciaEntity
import com.beazeth.notifier.data.local.TagEntity
import com.beazeth.notifier.data.local.TarefaEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A ponte entre o banco do aparelho e o servidor.
 *
 * A ordem das duas metades nao e arbitraria: **primeiro sobe o que esta na
 * fila, depois baixa o que mudou.** Ao contrario, uma tarefa criada offline
 * seria sobrescrita pela resposta do servidor -- que ainda nao sabe dela -- e
 * sumiria da tela antes de chegar a subir.
 *
 * Nada aqui bloqueia interface. Quem chama e o worker ou um puxao para
 * atualizar; as telas leem do Room e nao esperam por isto.
 */
class Sincronizador(private val context: Context) {

    private val banco = BancoLocal.obter(context)
    private val guardaToken = TokenStore(context)

    sealed interface Fim {
        data object Ok : Fim

        /** Rede fora ou servidor dormindo: vale tentar de novo depois. */
        data class Adiado(val motivo: String) : Fim

        /** Token vencido: nao adianta repetir, precisa de login. */
        data object SemSessao : Fim
    }

    /**
     * Sincroniza -- uma de cada vez, no processo inteiro.
     *
     * ## Por que a tranca
     *
     * Sao DUAS obras unicas no WorkManager, `sync-agora` e `sync-periodico`, e
     * "unica" vale por nome: nada impede as duas de rodarem no mesmo instante.
     * Quando isso acontece, as duas leem a mesma fila e mandam o mesmo POST
     * antes de qualquer uma apagar a pendencia -- e o servidor, que nao tem como
     * saber, cria duas linhas.
     *
     * Nao e hipotese: apareceu inteiro na primeira vez que alguem entrou numa
     * conta vindo do modo local. A adocao enfileira tudo de uma vez e a obra
     * periodica estreia junto, e o resultado foi cada post-it e cada tarefa
     * duplicados na conta nova.
     *
     * Um `Mutex` de processo basta porque os workers rodam todos no processo do
     * app -- nao ha `android:process` em lugar nenhum do manifesto. Se um dia
     * houver, a tranca tem de descer para o banco.
     *
     * **Espera, e nao desiste.** Quem chega no meio de uma drenagem tem alguma
     * escrita nova para mandar; desistir a deixaria parada ate o proximo
     * gatilho, que pode ser daqui a uma hora.
     */
    suspend fun rodar(): Fim = umaDeCadaVez.withLock {
        val token = guardaToken.tokenAtual() ?: return@withLock Fim.SemSessao

        val subida = drenarFila(token)
        if (subida !is Fim.Ok) return@withLock subida

        baixarMudancas(token)
    }

    // ------------------------------------------------------------- subida

    /**
     * Manda o que foi escrito offline, na ordem em que aconteceu.
     *
     * Para na primeira falha de rede em vez de pular para a proxima: a fila e
     * uma sequencia de fatos (criei, marquei como feita), e aplicar o segundo
     * sem o primeiro daria um 404 no servidor.
     *
     * **Pede uma por vez, e sempre ao banco.** A versao anterior percorria a
     * lista inteira de uma so vez, e nisso jogava fora metade do efeito de
     * [trocarProvisorioPeloDefinitivo]: a troca reescreve os caminhos NO BANCO,
     * mas as pendencias seguintes ja estavam na mao, com o caminho velho.
     * Criar um post-it e escrever nele antes da primeira subida mandava o texto
     * para `/api/notes/-1`; o servidor respondia 404, o texto era descartado
     * como "recusa" e a nota voltava vazia na descida seguinte. O laco sempre
     * avanca -- todo caminho ou remove a pendencia ou devolve.
     */
    private suspend fun drenarFila(token: String): Fim {
        while (true) {
            val p = banco.pendencias().primeira() ?: return Fim.Ok
            when (val r = Api.escrever(token, p.metodo, p.caminho, p.corpo)) {
                is Api.Resultado.Ok -> {
                    if (p.idProvisorio != null) {
                        trocarProvisorioPeloDefinitivo(p, r.corpo)
                    }
                    banco.pendencias().remover(p)
                }

                is Api.Resultado.Erro -> {
                    if (r.semSessao) return Fim.SemSessao
                    if (r.mensagem.startsWith(PREFIXO_REDE_FORA)) {
                        return Fim.Adiado(r.mensagem)
                    }
                    // Recusa do servidor: dia cheio, texto longo demais, linha
                    // que ja nao existe. Repetir nao muda a resposta, e manter
                    // na fila travaria tudo o que vem depois. Some a pendencia;
                    // se ela criava algo, some tambem a linha provisoria, que
                    // so existia apostando neste envio.
                    if (p.idProvisorio != null) {
                        apagarLocal(p.entidade, p.idProvisorio)
                    }
                    banco.pendencias().remover(p)
                }
            }
        }
    }

    /**
     * O servidor emitiu o id de verdade: troca a linha provisoria por ela.
     *
     * E reescreve a fila. Cenario real: criar uma tarefa offline e ja marca-la
     * como feita gera duas pendencias, e a segunda fala do id negativo. Sem
     * esta troca, ela subiria apontando para um id que nunca existiu la.
     */
    private suspend fun trocarProvisorioPeloDefinitivo(p: PendenciaEntity, corpo: String) {
        val provisorio = p.idProvisorio ?: return

        val definitivo: Long = when (p.entidade) {
            ALVO_NOTAS -> {
                val nota = decodificar(RespostaNota.serializer(), corpo)?.note ?: return
                // O que foi escrito DEPOIS de o POST entrar na fila mora so na
                // linha local -- o corpo que subiu e do momento da criacao, e o
                // PATCH com o texto ainda esta na fila, atras deste POST.
                // Gravar a resposta do servidor por cima apagaria o texto da
                // tela ate o PATCH subir; se a rede cair no meio dos dois, ele
                // some por tempo indeterminado. Do servidor interessa o id.
                val local = banco.notas().buscar(provisorio)
                banco.notas().apagar(provisorio)
                banco.notas().gravar(
                    local?.copy(id = nota.id, updatedAt = nota.updatedAt)
                        ?: nota.paraEntidade()
                )
                if (local != null && local.content != nota.content &&
                    !haEscritaNaFilaPara(ALVO_NOTAS, provisorio)
                ) {
                    acertarTextoDaNota(nota.id, local.content)
                }
                nota.id
            }

            ALVO_TAREFAS -> {
                val item = decodificar(RespostaTarefa.serializer(), corpo)?.item ?: return
                banco.tarefas().apagar(provisorio)
                banco.tarefas().gravar(item.paraEntidade())
                item.id
            }

            ALVO_BLOCOS -> {
                val bloco = decodificar(RespostaBloco.serializer(), corpo)?.block ?: return
                banco.blocos().apagar(provisorio)
                banco.blocos().gravar(bloco.paraEntidade())
                bloco.id
            }

            ALVO_EVENTOS -> {
                val evento = decodificar(RespostaEvento.serializer(), corpo)?.event ?: return
                banco.eventos().apagar(provisorio)
                banco.eventos().gravar(listOf(evento.paraEntidade()))
                evento.id
            }

            else -> return
        }

        banco.pendencias().trocarId(p.entidade, provisorio.toString(), definitivo.toString())
        Remapeamentos.anunciar(p.entidade, provisorio, definitivo)
    }

    /**
     * Ja ha alguma escrita na fila falando desta linha provisoria?
     *
     * Se ha, ela vai subir logo em seguida com o caminho ja corrigido, e o
     * acerto abaixo seria um segundo pedido dizendo a mesma coisa.
     */
    private suspend fun haEscritaNaFilaPara(entidade: String, provisorio: Long): Boolean =
        banco.pendencias().todas().any {
            it.entidade == entidade && it.caminho.endsWith("/$provisorio")
        }

    /**
     * Enfileira o texto que o aparelho tem e o servidor nao.
     *
     * A criacao sobe com o texto do momento em que foi pedida -- em geral,
     * vazio, porque o botao cria a folha em branco e so depois se escreve nela.
     * Se quando a resposta chega a linha local ja diz outra coisa, essa
     * diferenca e o que foi digitado nesse meio tempo, e nao ha garantia de que
     * exista pendencia para ela: o cartao pode ter fechado a edicao ja com o id
     * novo, achando que nao havia nada por mandar.
     *
     * Sem isto, o post-it fica certo no aparelho e vazio no site -- o pior tipo
     * de defeito, porque nada na tela indica que ha algo errado.
     */
    private suspend fun acertarTextoDaNota(id: Long, texto: String) {
        banco.pendencias().enfileirar(
            PendenciaEntity(
                metodo = "PATCH",
                caminho = "/api/notes/$id",
                corpo = buildJsonObject { put("content", texto) }.toString(),
                idProvisorio = null,
                entidade = ALVO_NOTAS,
                criadoEm = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun apagarLocal(entidade: String, id: Long) {
        when (entidade) {
            ALVO_NOTAS -> banco.notas().apagar(id)
            ALVO_TAREFAS -> banco.tarefas().apagar(id)
            ALVO_BLOCOS -> banco.blocos().apagar(id)
            ALVO_EVENTOS -> banco.eventos().apagar(id)
        }
    }

    private fun <T> decodificar(
        serializador: kotlinx.serialization.KSerializer<T>,
        texto: String,
    ): T? = runCatching { Api.json.decodeFromString(serializador, texto) }.getOrNull()

    // ------------------------------------------------------------- descida

    private suspend fun baixarMudancas(token: String): Fim {
        val desde = guardaToken.ultimaSyncAtual()

        return when (val r = Api.sincronizar(token, desde)) {
            is Api.Resultado.Erro ->
                if (r.semSessao) Fim.SemSessao else Fim.Adiado(r.mensagem)

            is Api.Resultado.Ok -> {
                aplicar(r.corpo)
                // So guarda o carimbo depois de gravar tudo. Se o processo
                // morrer no meio, a proxima chamada repete a mesma janela --
                // e repetir e inofensivo, porque grava por id. Guardar antes
                // perderia a janela inteira, e para sempre.
                r.corpo.now?.let { guardaToken.guardarUltimaSync(it) }
                Fim.Ok
            }
        }
    }

    private suspend fun aplicar(resposta: RespostaSync) {
        val m = resposta.changed

        if (m.notes.isNotEmpty()) banco.notas().gravar(m.notes.map { it.paraEntidade() })
        if (m.todoItems.isNotEmpty()) banco.tarefas().gravar(m.todoItems.map { it.paraEntidade() })
        if (m.plannerBlocks.isNotEmpty()) banco.blocos().gravar(m.plannerBlocks.map { it.paraEntidade() })
        if (m.events.isNotEmpty()) banco.eventos().gravar(m.events.map { it.paraEntidade() })
        if (m.tags.isNotEmpty()) banco.tags().gravar(m.tags.map { it.paraEntidade() })
        if (m.hydrationIntake.isNotEmpty()) banco.agua().gravar(m.hydrationIntake.map { it.paraEntidade() })
        m.hydrationSettings?.let { banco.agua().gravarConfig(it.paraEntidade()) }

        for (morto in resposta.deleted) {
            // `entity` e o nome da TABELA no Postgres, escrito pelo gatilho --
            // nao o nome do campo no JSON. Ver `registrar_exclusao` em
            // schema.py, que grava `TG_TABLE_NAME`.
            when (morto.entity) {
                "sticky_notes" -> morto.id.toLongOrNull()?.let { banco.notas().apagar(it) }
                "todo_items" -> morto.id.toLongOrNull()?.let { banco.tarefas().apagar(it) }
                "planner_blocks" -> morto.id.toLongOrNull()?.let { banco.blocos().apagar(it) }
                "events" -> morto.id.toLongOrNull()?.let { banco.eventos().apagar(it) }
                "event_tags" -> banco.tags().apagar(morto.id)
            }
        }
    }

    companion object {
        /** Ver [rodar]. Do processo, e nao da instancia: cada worker constroi o
         *  seu proprio [Sincronizador], entao uma tranca de instancia nao
         *  trancaria nada. */
        private val umaDeCadaVez = Mutex()

        const val ALVO_NOTAS = "notas"
        const val ALVO_TAREFAS = "tarefas"
        const val ALVO_BLOCOS = "blocos"
        const val ALVO_EVENTOS = "eventos"

        /**
         * O inicio da mensagem que [Api] usa para falha de rede.
         *
         * Comparar texto e fragil, mas a alternativa era vazar o tipo da
         * excecao ate aqui. Se esta mensagem mudar em Api.kt, a fila para de
         * distinguir rede fora de recusa do servidor -- e passa a descartar
         * escritas que so precisavam de outra tentativa.
         */
        const val PREFIXO_REDE_FORA = "Não foi possível falar"
    }
}

// -------------------------------------------------------- JSON -> entidade
//
// Funcoes de extensao, e nao construtores nas entidades: a entidade nao deve
// conhecer o formato da rede. Se um dia o JSON mudar, muda so aqui.

fun NotaJson.paraEntidade() = NotaEntity(
    id = id, content = content, bucket = bucket, x = x, y = y,
    width = width, height = height, color = color, z = z, updatedAt = updatedAt,
)

fun TarefaJson.paraEntidade() = TarefaEntity(
    id = id, day = day, content = content, done = done, position = position,
)

fun BlocoJson.paraEntidade() = BlocoEntity(
    id = id, title = title, notes = notes, dayOfWeek = dayOfWeek,
    startMinute = startMinute, endMinute = endMinute, color = color, isRoutine = isRoutine,
)

fun EventoJson.paraEntidade() = EventoEntity(
    id = id, title = title, description = description.orEmpty(),
    eventDatetime = eventDatetime, tagType = tagType,
    tagLabel = tagLabel, tagColor = tagColor, reminderRule = reminderRule,
)

fun TagJson.paraEntidade() = TagEntity(
    slug = slug, label = label, color = color, reminderRule = reminderRule,
)

fun AguaJson.paraEntidade() = AguaDiaEntity(day = day, glasses = glasses)

fun ConfigAguaJson.paraEntidade() = ConfigAguaEntity(
    enabled = enabled, dailyGoal = dailyGoal, glassMl = glassMl,
    intervalMinutes = intervalMinutes, startTime = startTime, endTime = endTime,
)
