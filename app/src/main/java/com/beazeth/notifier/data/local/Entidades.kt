package com.beazeth.notifier.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * As tabelas do aparelho.
 *
 * Espelham o que `GET /api/sync` entrega, campo por campo -- os nomes vem do
 * JSON e nao do Postgres (`dayOfWeek`, e nao `day_of_week`), porque e do JSON
 * que elas sao preenchidas. Traduzir duas vezes seria duas chances de errar.
 *
 * **Esta e a fonte de verdade da interface.** Nenhuma tela espera a rede: ela
 * le daqui e desenha. A sincronizacao escreve aqui por baixo, quando da. E o
 * que faz o app abrir instantaneo e continuar funcionando no elevador.
 *
 * Os ids sao os do servidor. Linhas criadas offline recebem id NEGATIVO, que o
 * servidor nunca emite -- e como o app distingue "isto ainda nao existe la" sem
 * uma coluna a mais. Ao subir, a linha provisoria e trocada pela definitiva.
 */

@Entity(tableName = "notas")
data class NotaEntity(
    @PrimaryKey val id: Long,
    val content: String,
    val bucket: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val color: String,
    val z: Int,
    val updatedAt: String,
)

@Entity(tableName = "tarefas")
data class TarefaEntity(
    @PrimaryKey val id: Long,
    /** Dia em ISO (`2026-08-27`); e por ele que a tela agrupa. */
    val day: String,
    val content: String,
    val done: Boolean,
    val position: Int,
)

@Entity(tableName = "blocos")
data class BlocoEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val notes: String,
    /** 0 = segunda, como no planner do site. */
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val color: String,
    val isRoutine: Boolean,
)

@Entity(tableName = "eventos")
data class EventoEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String,
    /** ISO local, como o servidor guarda. */
    val eventDatetime: String,
    val tagType: String,
    // A tag ja vem resolvida do servidor. Guardar o rotulo e a cor junto evita
    // que o evento apareca sem cor enquanto a tabela de tags nao chegou -- as
    // duas vem na mesma resposta, mas a ordem de gravacao nao e garantida.
    val tagLabel: String,
    val tagColor: String,
    val reminderRule: String,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val slug: String,
    val label: String,
    val color: String,
    val reminderRule: String,
)

@Entity(tableName = "agua_dias")
data class AguaDiaEntity(
    @PrimaryKey val day: String,
    val glasses: Int,
)

/**
 * A configuracao de agua: uma linha so, sempre com [ID_UNICO].
 *
 * O servidor tem uma por conta e nunca a apaga. A chave fixa deixa o `upsert`
 * resolver sozinho se e a primeira vez ou uma atualizacao.
 */
@Entity(tableName = "agua_config")
data class ConfigAguaEntity(
    @PrimaryKey val id: Int = ID_UNICO,
    val enabled: Boolean,
    val dailyGoal: Int,
    val glassMl: Int,
    val intervalMinutes: Int,
    val startTime: String,
    val endTime: String,
) {
    companion object {
        const val ID_UNICO = 1
    }
}

/**
 * A fila de escritas que ainda nao chegaram ao servidor.
 *
 * Sem ela, escrever offline seria mentir: a tela mostraria a tarefa criada e
 * ela sumiria na proxima sincronizacao, porque o servidor nunca soube dela.
 *
 * Guarda o pedido HTTP inteiro -- metodo, caminho e corpo -- em vez de campos
 * por tipo de entidade. Assim a fila drena com um laco so, e uma tela nova nao
 * precisa mexer aqui.
 */
@Entity(tableName = "pendencias")
data class PendenciaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metodo: String,
    val caminho: String,
    /** `null` para DELETE, que nao tem corpo. */
    val corpo: String?,
    /**
     * Id provisorio (negativo) que esta escrita criou, se criou algum.
     *
     * Quando o servidor responder com o id de verdade, a linha provisoria e
     * trocada por ele -- e qualquer pendencia seguinte que apontasse para o
     * provisorio tem o caminho reescrito.
     */
    val idProvisorio: Long?,
    /** Qual tabela local a resposta deve atualizar. */
    val entidade: String,
    val criadoEm: Long,
)
