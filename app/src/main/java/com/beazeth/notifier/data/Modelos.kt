package com.beazeth.notifier.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * O formato de `GET /api/sync`.
 *
 * **Cuidado com a caixa dos nomes.** O servidor nao e uniforme, e isso e
 * proposital la: notas e tarefas passam por conversores Python que renomeiam
 * para `camelCase`, enquanto eventos e tags saem direto do SELECT e chegam em
 * `snake_case`. Um `@SerialName` faltando aqui nao da erro nenhum -- com
 * `ignoreUnknownKeys` ligado, o campo simplesmente chega vazio, e o evento
 * apareceria sem titulo. Conferido contra `app/db/sync.py`.
 *
 * Todos os campos tem valor padrao de proposito: uma versao do app instalada
 * num celular tem de continuar funcionando quando o servidor ganhar um campo
 * novo, e nao quebrar quando um opcional vier ausente.
 */
@Serializable
data class RespostaSync(
    val ok: Boolean = false,
    /** O instante a guardar para a proxima chamada. Vem do relogio do BANCO. */
    val now: String? = null,
    val changed: Mudancas = Mudancas(),
    val deleted: List<Exclusao> = emptyList(),
    val message: String? = null,
)

@Serializable
data class Mudancas(
    val notes: List<NotaJson> = emptyList(),
    val todoItems: List<TarefaJson> = emptyList(),
    val plannerBlocks: List<BlocoJson> = emptyList(),
    val events: List<EventoJson> = emptyList(),
    val tags: List<TagJson> = emptyList(),
    val hydrationIntake: List<AguaJson> = emptyList(),
    val diaryEntries: List<DiarioJson> = emptyList(),
    /** `null` aqui significa "nao mudou", e nao "nao existe". */
    val hydrationSettings: ConfigAguaJson? = null,
)

/** Uma lapide: `entity` e o nome da TABELA no Postgres, nao o do JSON. */
@Serializable
data class Exclusao(
    val entity: String = "",
    val id: String = "",
)

@Serializable
data class NotaJson(
    val id: Long = 0,
    val content: String = "",
    val bucket: String = "geral",
    val x: Int = 24,
    val y: Int = 24,
    val width: Int = 200,
    val height: Int = 200,
    val color: String = "#ffd0ec",
    val z: Int = 0,
    val updatedAt: String = "",
)

@Serializable
data class TarefaJson(
    val id: Long = 0,
    val day: String = "",
    val content: String = "",
    val done: Boolean = false,
    val position: Int = 0,
)

@Serializable
data class BlocoJson(
    val id: Long = 0,
    val title: String = "",
    val notes: String = "",
    val dayOfWeek: Int = 0,
    val startMinute: Int = 0,
    val endMinute: Int = 60,
    val color: String = "#ff78b2",
    val isRoutine: Boolean = false,
)

/** Este vem do SELECT: tudo em `snake_case`. */
@Serializable
data class EventoJson(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    @SerialName("event_datetime") val eventDatetime: String = "",
    @SerialName("tag_type") val tagType: String = "",
    @SerialName("tag_label") val tagLabel: String = "Evento",
    @SerialName("tag_color") val tagColor: String = "#7ec8ff",
    @SerialName("reminder_rule") val reminderRule: String = "dia",
)

/** Idem: `dict(row)` direto do Postgres. */
@Serializable
data class TagJson(
    val slug: String = "",
    val label: String = "",
    val color: String = "#7ec8ff",
    @SerialName("reminder_rule") val reminderRule: String = "dia",
)

@Serializable
data class AguaJson(
    val day: String = "",
    val glasses: Int = 0,
)

/**
 * Um dia do diario, como o servidor manda.
 *
 * `day` e a identidade nos dois lados -- nao ha id. Ver `DiarioEntity`.
 */
@Serializable
data class DiarioJson(
    val day: String = "",
    val mood: String = "",
    val note: String = "",
)

@Serializable
data class ConfigAguaJson(
    val enabled: Boolean = false,
    @SerialName("daily_goal") val dailyGoal: Int = 8,
    @SerialName("glass_ml") val glassMl: Int = 250,
    @SerialName("interval_minutes") val intervalMinutes: Int = 60,
    @SerialName("start_time") val startTime: String = "08:00",
    @SerialName("end_time") val endTime: String = "22:00",
)

// --------------------------------------------------------------- respostas
// de escrita. So o que o app precisa ler de volta: o id que o servidor emitiu.

@Serializable
data class RespostaNota(
    val ok: Boolean = false,
    val note: NotaJson? = null,
    val message: String? = null,
)

@Serializable
data class RespostaTarefa(
    val ok: Boolean = false,
    val item: TarefaJson? = null,
    val message: String? = null,
)

@Serializable
data class RespostaBloco(
    val ok: Boolean = false,
    val block: BlocoJson? = null,
    val message: String? = null,
)

@Serializable
data class RespostaEvento(
    val ok: Boolean = false,
    val event: EventoJson? = null,
    val message: String? = null,
)

@Serializable
data class RespostaSimples(
    val ok: Boolean = false,
    val message: String? = null,
)

