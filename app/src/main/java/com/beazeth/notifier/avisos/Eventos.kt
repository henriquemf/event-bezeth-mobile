package com.beazeth.notifier.avisos

import com.beazeth.notifier.data.local.EventoEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Quais avisos cada evento da agenda arma, e quando.
 *
 * ## As regras sao as do site, e nao regras novas
 *
 * Estao em `_build_reminders`, em `app/services/scheduler_service.py`: a tag do
 * evento diz se ele avisa so na hora (`dia`) ou tambem com antecedencia
 * (`curso`). A regra ja chega resolvida em [EventoEntity.reminderRule] -- a
 * sincronizacao a copia da tag junto com o rotulo e a cor.
 *
 * Inventar um cronograma aqui seria o mesmo evento avisando em horas
 * diferentes no e-mail e no celular, e ninguem saberia qual dos dois esta
 * certo.
 */

/**
 * Quanto antes do evento cai o aviso.
 *
 * [chave] e o nome que o servidor grava em `reminder_dispatches`. Nao e usada
 * para nada aqui -- o aparelho tem a propria marca d'agua -- mas manter os
 * mesmos nomes e o que deixa comparar os dois lados quando um aviso nao chega.
 */
internal enum class Antecedencia(
    val chave: String,
    val rotulo: String,
    val dias: Long,
    /** Ver [Lembretes] -- quanto atraso ainda vale a pena entregar. */
    val tolerancia: Duration,
) {
    /**
     * Na hora marcada. Uma hora de tolerancia: um aviso de "agora" que chega
     * de tarde, para um compromisso da manha, nao lembra nada -- so assusta.
     */
    AGORA("event_now", "agora", 0, Duration.ofHours(1)),

    /**
     * Os dois antecipados da regra `curso`. Doze horas de tolerancia porque
     * "faltam 15 dias" continua sendo verdade meio dia depois: perder o aviso
     * porque o aparelho passou a manha desligado seria pior do que recebe-lo
     * na hora do almoco.
     */
    QUINZE_DIAS("course_15_days", "em 15 dias", 15, Duration.ofHours(12)),
    SETE_DIAS("course_7_days", "em 7 dias", 7, Duration.ofHours(12)),
}

/** Um aviso concreto: qual evento, com que antecedencia, em que instante. */
internal data class Gatilho(
    val evento: EventoEntity,
    val antecedencia: Antecedencia,
    val quando: LocalDateTime,
) {
    /** Ver [idDoAviso]. */
    val id: Int get() = idDoAviso("evento-${evento.id}-${antecedencia.chave}")

    val titulo: String get() = evento.title.ifBlank { "Evento" }

    val texto: String
        get() {
            val rotulo = evento.tagLabel.ifBlank { "Evento" }
            val quandoEscrito = dataDoEvento(evento)?.format(FORMATO) ?: evento.eventDatetime
            val cabeca = "$rotulo ${antecedencia.rotulo} · $quandoEscrito"
            val descricao = evento.description.trim()
            return if (descricao.isEmpty()) cabeca else "$cabeca\n$descricao"
        }
}

/**
 * Todos os avisos que a agenda arma, do mais cedo para o mais tarde.
 *
 * Percorre a agenda inteira em vez de so o futuro porque quem chama precisa
 * enxergar o passado recente tambem: um aviso pode ter vencido com o aparelho
 * desligado, e a decisao de entregar ou nao e de [Lembretes], nao daqui.
 */
internal fun gatilhosDaAgenda(eventos: List<EventoEntity>): List<Gatilho> =
    eventos.flatMap { evento ->
        val quando = dataDoEvento(evento) ?: return@flatMap emptyList()
        val regras = if (evento.reminderRule == "curso") {
            Antecedencia.entries
        } else {
            listOf(Antecedencia.AGORA)
        }
        regras.map { Gatilho(evento, it, quando.minusDays(it.dias)) }
    }.sortedBy { it.quando }

/**
 * A data do evento, ou `null` se o texto do banco nao servir.
 *
 * Data sem hora vira 09:00, igual a `_parse_event_datetime` do servidor. O site
 * aceita evento de dia inteiro, e um aviso a meia-noite para o compromisso do
 * dia seguinte chegaria cedo demais para ser util.
 */
internal fun dataDoEvento(evento: EventoEntity): LocalDateTime? {
    val bruto = evento.eventDatetime.trim()
    val completo = if (bruto.length == 10 && !bruto.contains("T")) "${bruto}T09:00" else bruto
    return runCatching { LocalDateTime.parse(completo) }.getOrNull()
}

/** O mesmo formato do e-mail que o servidor manda. */
private val FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
