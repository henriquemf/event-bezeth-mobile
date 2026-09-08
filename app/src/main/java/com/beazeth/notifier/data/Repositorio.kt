package com.beazeth.notifier.data

import android.content.Context
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.data.local.AguaDiaEntity
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.data.local.BlocoEntity
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.data.local.NotaEntity
import com.beazeth.notifier.data.local.PendenciaEntity
import com.beazeth.notifier.data.local.TarefaEntity
import com.beazeth.notifier.sync.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * O que as telas usam. Leitura sai do banco do aparelho; escrita entra nele
 * primeiro e so depois vai para a fila.
 *
 * **Esta e a regra que faz o app parecer nativo.** Nenhum metodo aqui espera
 * resposta de servidor. Marcar uma tarefa como feita grava no Room, a tela
 * redesenha no mesmo quadro, e a subida acontece depois -- em segundos com
 * rede, em horas sem ela. Se a escrita esperasse a rede, cada toque teria a
 * latencia do Oregon, que foi exatamente a queixa que matou o TWA.
 *
 * O preco e a linha provisoria: id negativo ate o servidor emitir o de verdade.
 * Ver [Sincronizador.trocarProvisorioPeloDefinitivo].
 */
class Repositorio(private val context: Context) {

    private val banco = BancoLocal.obter(context)
    private val guardaToken = TokenStore(context)

    // ------------------------------------------------------------ leitura

    fun notasDoQuadro(bucket: String): Flow<List<NotaEntity>> =
        banco.notas().observarDoQuadro(bucket)

    fun tarefas(inicio: String, fim: String): Flow<List<TarefaEntity>> =
        banco.tarefas().observarIntervalo(inicio, fim)

    fun blocos(): Flow<List<BlocoEntity>> = banco.blocos().observar()

    fun eventos() = banco.eventos().observar()

    fun tags() = banco.tags().observar()

    /** O dia de agua que o SERVIDOR considera corrente. Ver o DAO. */
    fun aguaCorrente(): Flow<AguaDiaEntity?> = banco.agua().observarDiaMaisRecente()

    fun configDeAgua() = banco.agua().observarConfig()

    /** Quantas escritas ainda nao subiram. A casca mostra isto. */
    fun pendencias(): Flow<Int> = banco.pendencias().quantas()

    // ------------------------------------------------------------ post-its

    suspend fun criarNota(
        bucket: String,
        texto: String,
        cor: String,
        x: Int = 18,
        y: Int = 18,
    ): Long {
        val id = proximoIdProvisorio(banco.notas().menorId())
        val nota = NotaEntity(
            id = id, content = texto, bucket = bucket,
            x = x, y = y, width = LARGURA_PADRAO, height = ALTURA_PADRAO,
            color = cor, z = 0, updatedAt = "",
        )
        banco.notas().gravar(nota)

        enfileirar(
            metodo = "POST",
            caminho = "/api/notes",
            corpo = buildJsonObject {
                put("bucket", bucket)
                put("content", texto)
                put("color", cor)
                put("x", x)
                put("y", y)
                put("width", LARGURA_PADRAO)
                put("height", ALTURA_PADRAO)
            },
            idProvisorio = id,
            entidade = Sincronizador.ALVO_NOTAS,
        )
        return id
    }

    suspend fun editarNota(nota: NotaEntity, texto: String) {
        if (!notaViva(nota.id)) return
        banco.notas().gravar(nota.copy(content = texto))
        enfileirar(
            metodo = "PATCH",
            caminho = "/api/notes/${nota.id}",
            corpo = buildJsonObject { put("content", texto) },
            entidade = Sincronizador.ALVO_NOTAS,
        )
    }

    /**
     * Guarda o que esta sendo escrito, sem passar pela fila de rede.
     *
     * Enquanto o dedo esta no papel o texto so existe na memoria da tela, e a
     * tela e a coisa mais fragil do sistema: o cartao sai da composicao quando
     * o id provisorio vira definitivo, quando o Android recolhe a atividade,
     * quando se gira o aparelho. Uma linha no Room a cada pausa custa quase
     * nada e faz com que nada disso apague uma frase pela metade.
     *
     * A fila fica de fora de proposito. Subir a cada pausa da digitacao encheria
     * a fila de PATCHes que so se sobrescrevem; o envio continua acontecendo uma
     * vez, ao fechar a edicao.
     */
    suspend fun rascunharNota(nota: NotaEntity, texto: String) {
        if (!notaViva(nota.id)) return
        banco.notas().gravar(nota.copy(content = texto))
    }

    /**
     * A linha ainda existe?
     *
     * `gravar` e um upsert, entao escrever numa nota apagada nao da erro: ela
     * VOLTA. E ha varios instantes em que a tela segura uma nota que o banco ja
     * nao tem -- o post-it novo cujo id provisorio acabou de virar definitivo (a
     * linha antiga e apagada e outra entra no lugar), ou um post-it apagado no
     * site que a sincronizacao acabou de remover daqui.
     *
     * Sem esta pergunta, o cartao que estava aberto gravava por cima e o post-it
     * reaparecia como um fantasma -- duas folhas empilhadas na tela, e um PATCH
     * para um id que o servidor nao conhece. Escrever numa nota que nao existe
     * mais nao e escrita nenhuma: e para ser descartado em silencio, porque a
     * decisao de apagar veio de quem manda.
     */
    private suspend fun notaViva(id: Long): Boolean = banco.notas().buscar(id) != null

    suspend fun recolorirNota(nota: NotaEntity, cor: String) {
        if (!notaViva(nota.id)) return
        banco.notas().gravar(nota.copy(color = cor))
        enfileirar(
            metodo = "PATCH",
            caminho = "/api/notes/${nota.id}",
            corpo = buildJsonObject { put("color", cor) },
            entidade = Sincronizador.ALVO_NOTAS,
        )
    }

    /**
     * Move ou redimensiona um post-it.
     *
     * Os limites sao os mesmos que `NOTE_BOUNDS` valida no servidor. Barrar
     * aqui evita enfileirar uma escrita que voltaria corrigida -- e o post-it
     * pularia de lugar sozinho na proxima sincronizacao.
     */
    suspend fun moverNota(nota: NotaEntity, x: Int, y: Int, largura: Int, altura: Int) {
        if (!notaViva(nota.id)) return
        val novo = nota.copy(
            x = x.coerceIn(0, MAX_X),
            y = y.coerceIn(0, MAX_Y),
            width = largura.coerceIn(MIN_LARGURA, MAX_LARGURA),
            height = altura.coerceIn(MIN_ALTURA, MAX_ALTURA),
        )
        banco.notas().gravar(novo)
        enfileirar(
            metodo = "PATCH",
            caminho = "/api/notes/${nota.id}",
            corpo = buildJsonObject {
                put("x", novo.x)
                put("y", novo.y)
                put("width", novo.width)
                put("height", novo.height)
            },
            entidade = Sincronizador.ALVO_NOTAS,
        )
    }

    suspend fun apagarNota(id: Long) {
        banco.notas().apagar(id)
        // Linha que nunca subiu nao precisa de DELETE la: o servidor nao a
        // conhece. Basta tirar da fila o que ainda falava dela.
        if (id < 0) {
            removerPendenciasDe(id)
            return
        }
        enfileirar(
            metodo = "DELETE",
            caminho = "/api/notes/$id",
            corpo = null,
            entidade = Sincronizador.ALVO_NOTAS,
        )
    }

    // -------------------------------------------------------------- to-do

    /** `false` quando o dia ja bateu o limite do servidor. */
    suspend fun criarTarefa(dia: String, texto: String): Boolean {
        val limpo = texto.trim().take(MAX_TEXTO_TAREFA)
        if (limpo.isEmpty()) return false
        if (banco.tarefas().quantasNoDia(dia) >= MAX_TAREFAS_POR_DIA) return false

        val id = proximoIdProvisorio(banco.tarefas().menorId())
        banco.tarefas().gravar(
            TarefaEntity(
                id = id, day = dia, content = limpo, done = false,
                position = banco.tarefas().ultimaPosicao(dia) + 1,
            )
        )

        enfileirar(
            metodo = "POST",
            caminho = "/api/todo",
            corpo = buildJsonObject {
                put("day", dia)
                put("content", limpo)
            },
            idProvisorio = id,
            entidade = Sincronizador.ALVO_TAREFAS,
        )
        return true
    }

    suspend fun alternarTarefa(item: TarefaEntity) {
        val feito = !item.done
        banco.tarefas().gravar(item.copy(done = feito))
        enfileirar(
            metodo = "PATCH",
            caminho = "/api/todo/${item.id}",
            corpo = buildJsonObject { put("done", feito) },
            entidade = Sincronizador.ALVO_TAREFAS,
        )
    }

    suspend fun editarTarefa(item: TarefaEntity, texto: String) {
        val limpo = texto.trim().take(MAX_TEXTO_TAREFA)
        if (limpo.isEmpty()) return
        banco.tarefas().gravar(item.copy(content = limpo))
        enfileirar(
            metodo = "PATCH",
            caminho = "/api/todo/${item.id}",
            corpo = buildJsonObject { put("content", limpo) },
            entidade = Sincronizador.ALVO_TAREFAS,
        )
    }

    suspend fun apagarTarefa(id: Long) {
        banco.tarefas().apagar(id)
        if (id < 0) {
            removerPendenciasDe(id)
            return
        }
        enfileirar(
            metodo = "DELETE",
            caminho = "/api/todo/$id",
            corpo = null,
            entidade = Sincronizador.ALVO_TAREFAS,
        )
    }

    // --------------------------------------------------------------- agua

    /**
     * Um copo a mais ou a menos.
     *
     * O servidor aceita `delta` e faz a conta la; aqui a mesma conta acontece
     * antes, para o copo acender na hora. Os dois chegam ao mesmo numero
     * porque a operacao e relativa -- se fosse "grave 5 copos", duas telas
     * abertas se sobrescreveriam.
     */
    suspend fun beberAgua(dia: String, delta: Int) {
        val atual = banco.agua().buscar(dia)?.glasses ?: 0
        val novo = (atual + delta).coerceAtLeast(0)
        banco.agua().gravar(AguaDiaEntity(day = dia, glasses = novo))

        enfileirar(
            metodo = "POST",
            caminho = "/api/hydration/drink",
            corpo = buildJsonObject { put("delta", delta) },
            entidade = ALVO_AGUA,
        )
    }

    // ------------------------------------------------------------ eventos

    /**
     * Cria um evento na agenda.
     *
     * A tag chega como slug (`evento`, `prova`...). Quem valida se ela existe
     * NA CONTA e o servidor -- e ele troca pela padrao se nao existir. Repetir
     * essa checagem aqui criaria uma segunda verdade que envelheceria primeiro.
     */
    suspend fun criarEvento(
        titulo: String,
        descricao: String,
        quando: String,
        tag: String,
    ): Long {
        val id = proximoIdProvisorio(banco.eventos().menorId())
        banco.eventos().gravar(
            listOf(
                EventoEntity(
                    id = id, title = titulo, description = descricao,
                    eventDatetime = quando, tagType = tag,
                    // Enquanto a resposta nao chega, mostra o que a tabela de
                    // tags local sabe; a sincronizacao corrige em seguida.
                    tagLabel = banco.tags().buscar(tag)?.label ?: "Evento",
                    tagColor = banco.tags().buscar(tag)?.color ?: "#7ec8ff",
                    reminderRule = banco.tags().buscar(tag)?.reminderRule ?: "dia",
                )
            )
        )

        enfileirar(
            metodo = "POST",
            caminho = "/api/events",
            corpo = buildJsonObject {
                put("title", titulo)
                put("description", descricao)
                put("eventDatetime", quando)
                put("tagType", tag)
            },
            idProvisorio = id,
            entidade = Sincronizador.ALVO_EVENTOS,
        )
        avisosDaAgendaMudaram()
        return id
    }

    suspend fun editarEvento(
        evento: EventoEntity,
        titulo: String,
        descricao: String,
        quando: String,
        tag: String,
    ) {
        banco.eventos().gravar(
            listOf(
                evento.copy(
                    title = titulo, description = descricao,
                    eventDatetime = quando, tagType = tag,
                )
            )
        )
        enfileirar(
            metodo = "PATCH",
            caminho = "/api/events/${evento.id}",
            corpo = buildJsonObject {
                put("title", titulo)
                put("description", descricao)
                put("eventDatetime", quando)
                put("tagType", tag)
            },
            entidade = Sincronizador.ALVO_EVENTOS,
        )
        avisosDaAgendaMudaram()
    }

    suspend fun apagarEvento(id: Long) {
        banco.eventos().apagar(id)
        // Logo apos a escrita, e nao no fim: abaixo ha um `return` para o
        // evento que nunca chegou ao servidor, e ele tambem some da agenda.
        avisosDaAgendaMudaram()
        if (id < 0) {
            removerPendenciasDe(id)
            return
        }
        enfileirar(
            metodo = "DELETE",
            caminho = "/api/events/$id",
            corpo = null,
            entidade = Sincronizador.ALVO_EVENTOS,
        )
    }

    /**
     * Recalcula os alarmes da agenda depois de mexer nela.
     *
     * A sincronizacao ja faz isso ao terminar, mas ela pode nao terminar: quem
     * cria um evento no elevador escreve no Room, a subida fica na fila, e sem
     * esta chamada o lembrete so seria marcado quando a rede voltasse. Um
     * evento para daqui a dez minutos passaria batido.
     */
    private suspend fun avisosDaAgendaMudaram() = Lembretes.rearmar(context)

    // ------------------------------------------------------------ planner

    suspend fun criarBloco(bloco: BlocoEntity): Long {
        val id = proximoIdProvisorio(banco.blocos().menorId())
        banco.blocos().gravar(bloco.copy(id = id))
        enfileirar(
            metodo = "POST",
            caminho = "/api/planner/blocks",
            corpo = corpoDoBloco(bloco),
            idProvisorio = id,
            entidade = Sincronizador.ALVO_BLOCOS,
        )
        return id
    }

    suspend fun atualizarBloco(bloco: BlocoEntity) {
        banco.blocos().gravar(bloco)
        // PUT e nao PATCH: o servidor valida o bloco inteiro (`parse_block_payload`),
        // e um corpo parcial seria recusado por falta de titulo.
        enfileirar(
            metodo = "PUT",
            caminho = "/api/planner/blocks/${bloco.id}",
            corpo = corpoDoBloco(bloco),
            entidade = Sincronizador.ALVO_BLOCOS,
        )
    }

    suspend fun apagarBloco(id: Long) {
        banco.blocos().apagar(id)
        if (id < 0) {
            removerPendenciasDe(id)
            return
        }
        enfileirar(
            metodo = "DELETE",
            caminho = "/api/planner/blocks/$id",
            corpo = null,
            entidade = Sincronizador.ALVO_BLOCOS,
        )
    }

    private fun corpoDoBloco(b: BlocoEntity): JsonObject = buildJsonObject {
        put("title", b.title)
        put("notes", b.notes)
        put("dayOfWeek", b.dayOfWeek)
        put("startMinute", b.startMinute)
        put("endMinute", b.endMinute)
        put("color", b.color)
        put("isRoutine", b.isRoutine)
    }

    // ----------------------------------------------------------- modo local

    /**
     * Manda para a conta tudo o que foi escrito sem ela.
     *
     * Chamado no instante em que alguem que usava o app sem conta entra numa.
     * Sem isto, o modo local seria uma armadilha: quem escreveu por um mes e
     * depois criou uma conta veria o proprio conteudo ficar para tras, ou --
     * pior -- ser apagado pela primeira sincronizacao, que so traz o que o
     * servidor ja conhece.
     *
     * A conta e simples porque a maquinaria ja existia: enfileirar uma criacao
     * por linha provisoria e deixar o [Sincronizador] fazer o resto. Ele ja sabe
     * trocar o id negativo pelo do servidor e reescrever a fila -- e o mesmo
     * caminho de qualquer post-it criado offline, so que em lote.
     *
     * **Nao adota agua nem aparencia, e isso e deliberado.** Copos sao uma
     * CONTAGEM do dia, nao uma criacao: somar a contagem local por cima da que
     * a conta ja tem contaria o mesmo copo duas vezes. Tema e fonte ja moram no
     * aparelho de proposito, e nunca subiram para conta nenhuma.
     */
    suspend fun adotarDadosLocais() {
        for (nota in banco.notas().provisorios()) {
            enfileirar(
                metodo = "POST",
                caminho = "/api/notes",
                corpo = buildJsonObject {
                    put("bucket", nota.bucket)
                    put("content", nota.content)
                    put("color", nota.color)
                    put("x", nota.x)
                    put("y", nota.y)
                    put("width", nota.width)
                    put("height", nota.height)
                },
                idProvisorio = nota.id,
                entidade = Sincronizador.ALVO_NOTAS,
            )
        }

        for (item in banco.tarefas().provisorios()) {
            enfileirar(
                metodo = "POST",
                caminho = "/api/todo",
                corpo = buildJsonObject {
                    put("day", item.day)
                    put("content", item.content)
                },
                idProvisorio = item.id,
                entidade = Sincronizador.ALVO_TAREFAS,
            )
            // O "feito" nao cabe na criacao, entao vai como um segundo pedido.
            // Ele fala do id negativo de proposito: quando o POST acima subir, o
            // sincronizador reescreve este caminho com o id de verdade.
            if (item.done) {
                enfileirar(
                    metodo = "PATCH",
                    caminho = "/api/todo/${item.id}",
                    corpo = buildJsonObject { put("done", true) },
                    entidade = Sincronizador.ALVO_TAREFAS,
                )
            }
        }

        for (bloco in banco.blocos().provisorios()) {
            enfileirar(
                metodo = "POST",
                caminho = "/api/planner/blocks",
                corpo = corpoDoBloco(bloco),
                idProvisorio = bloco.id,
                entidade = Sincronizador.ALVO_BLOCOS,
            )
        }

        for (evento in banco.eventos().provisorios()) {
            enfileirar(
                metodo = "POST",
                caminho = "/api/events",
                corpo = buildJsonObject {
                    put("title", evento.title)
                    put("description", evento.description)
                    put("eventDatetime", evento.eventDatetime)
                    put("tagType", evento.tagType)
                },
                idProvisorio = evento.id,
                entidade = Sincronizador.ALVO_EVENTOS,
            )
        }
    }

    // ---------------------------------------------------------- bastidores

    private suspend fun enfileirar(
        metodo: String,
        caminho: String,
        corpo: JsonObject?,
        entidade: String,
        idProvisorio: Long? = null,
    ) {
        // No modo local nao ha fila, porque nao ha para onde subir. E a barreira
        // fica AQUI, num lugar so, e nao espalhada por cada escrita: e o unico
        // ponto por onde toda escrita passa, entao e o unico ponto em que
        // esquecer uma seria possivel. Sem isto, a fila cresceria para sempre
        // com pedidos que nunca vao sair, e o app avisaria "N escritas
        // pendentes" para uma pessoa que nunca pediu para enviar nada.
        if (guardaToken.modoLocalAtivo()) return

        banco.pendencias().enfileirar(
            PendenciaEntity(
                metodo = metodo,
                caminho = caminho,
                corpo = corpo?.toString(),
                idProvisorio = idProvisorio,
                entidade = entidade,
                criadoEm = System.currentTimeMillis(),
            )
        )
        // Pedido, e nao ordem: se nao houver rede, o WorkManager segura e tenta
        // quando houver. E por isso que a tela nao precisa saber se ha sinal.
        SyncWorker.agora(context)
    }

    /**
     * Tira da fila tudo que falava de uma linha que nunca existiu no servidor.
     *
     * Criar um post-it offline e apaga-lo antes de sincronizar deixaria um POST
     * na fila que criaria a nota de volta -- e sem nada depois para apaga-la.
     */
    private suspend fun removerPendenciasDe(idProvisorio: Long) {
        val alvo = idProvisorio.toString()
        banco.pendencias().todas()
            .filter { it.idProvisorio == idProvisorio || it.caminho.endsWith("/$alvo") }
            .forEach { banco.pendencias().remover(it) }
    }

    /**
     * O proximo id provisorio: sempre negativo, sempre livre.
     *
     * O servidor emite ids positivos (BIGSERIAL), entao o sinal sozinho ja
     * distingue "isto ainda nao existe la". `coerceAtMost(0)` cobre a tabela
     * vazia, em que o menor id e 0 e o proximo tem de ser -1.
     */
    private fun proximoIdProvisorio(menor: Long): Long = menor.coerceAtMost(0L) - 1L

    companion object {
        /** Espelham `MAX_CONTENT` e `MAX_ITEMS_PER_DAY` de `app/db/todo.py`.
         *  Barrar aqui evita enfileirar uma escrita que o servidor recusaria --
         *  e que sumiria da tela na drenagem, sem explicacao. */
        const val MAX_TEXTO_TAREFA = 500
        const val MAX_TAREFAS_POR_DIA = 60

        const val ALVO_AGUA = "agua"

        /** Espelham `DEFAULT_SIZE` e `NOTE_BOUNDS` do quadro do site
         *  (`js/pages/notes/constants.js` e `app/db/notes.py`). */
        const val LARGURA_PADRAO = 232
        const val ALTURA_PADRAO = 216
        const val MIN_LARGURA = 150
        const val MAX_LARGURA = 560
        const val MIN_ALTURA = 120
        const val MAX_ALTURA = 560
        const val MAX_X = 4000
        const val MAX_Y = 6000

        /** O `GAP` do quadro: a folga entre post-its ao organizar. */
        const val FOLGA = 18
    }
}
