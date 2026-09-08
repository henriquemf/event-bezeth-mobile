package com.beazeth.notifier.avisos

import com.beazeth.notifier.data.local.AguaDiaEntity
import com.beazeth.notifier.data.local.ConfigAguaEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Quando cai o proximo lembrete de agua.
 *
 * A configuracao -- ligado, intervalo, janela -- e a MESMA que o site guarda e
 * a sincronizacao ja traz para o Room. Nada aqui e escolhido de novo: mudar o
 * intervalo no computador muda o do celular na proxima sincronizacao.
 *
 * ## Uma grade, e nao "a cada X desde o ultimo"
 *
 * O servidor conta a partir de `last_sent_at`: mandou agora, o proximo e daqui
 * a X. Funciona la, onde um processo fica girando. Aqui seria pior: cada
 * reinicio do aparelho, cada atraso do Doze e cada troca de fuso empurrariam
 * todos os avisos seguintes, e a janela de 08:00-22:00 acabaria virando
 * 08:07-22:07 depois de uma semana.
 *
 * Entao os horarios sao uma GRADE presa a abertura da janela: 08:00, 09:00,
 * 10:00... Nao ha deriva, e "o proximo" e a mesma conta antes e depois de
 * desligar o aparelho.
 */

/** Titulo e texto sao os do servidor, letra por letra: um app so, duas telas. */
const val TITULO_DA_AGUA = "MOMO BEBA ÁGUA 💗"
const val TEXTO_DA_AGUA = "Meu amorzinho, hora de BEBER ÁGUA <3"

private const val MINUTOS_DO_DIA = 1440

/**
 * O proximo horario da grade depois de [agora], ou `null` se nao ha o que
 * marcar.
 *
 * `null` acontece com o lembrete desligado, sem configuracao nenhuma (quem usa
 * sem conta nunca recebeu uma) ou com hora malformada no banco. Nos tres casos
 * o certo e ficar quieto -- e e o mesmo criterio da barra lateral, que so
 * mostra o copo quando `enabled` e verdadeiro.
 */
internal fun proximoCopo(config: ConfigAguaEntity?, agora: LocalDateTime): LocalDateTime? {
    if (config == null || !config.enabled) return null

    val inicio = emMinutos(config.startTime) ?: return null
    val fim = emMinutos(config.endTime) ?: return null
    val intervalo = config.intervalMinutes.coerceAtLeast(1).toLong()
    val duracao = duracaoDaJanela(inicio, fim)

    // Ontem, hoje e amanha, nesta ordem. Ontem entra porque uma janela que
    // cruza a meia-noite (22:30 -> 06:00) ABRE no dia anterior: as 02:00 de
    // hoje, quem esta correndo e a janela de ontem, e a abertura de hoje ainda
    // esta vinte horas a frente. Amanha entra porque a de hoje pode ter
    // fechado.
    for (deslocamento in -1L..1L) {
        val abertura = LocalDate.from(agora)
            .plusDays(deslocamento)
            .atStartOfDay()
            .plusMinutes(inicio.toLong())

        val decorridos = Duration.between(abertura, agora).toMinutes()
        // Estritamente DEPOIS de agora: cair exatamente em cima de um horario
        // da grade tem de devolver o seguinte, senao o despertador remarcaria
        // para o instante que acabou de tocar e entraria em laco.
        val passo = if (decorridos < 0) 0L else (decorridos / intervalo + 1) * intervalo
        if (passo < duracao) return abertura.plusMinutes(passo)
    }
    return null
}

/**
 * A meta do dia ja foi batida?
 *
 * **Aqui o app se afasta do site de proposito.** O servidor avisa a cada
 * intervalo ate a janela fechar, tenha a pessoa bebido oito copos ou nenhum.
 * Num navegador aberto isso passa; num celular no bolso, seis avisos depois da
 * meta cumprida sao exatamente o que faz alguem desligar os avisos do app -- e
 * ai o lembrete de amanha tambem morre.
 *
 * A comparacao de datas nao e zelo excessivo: [AguaDiaEntity] e a linha do dia
 * corrente DO SERVIDOR, que pode estar velha se a sincronizacao nao roda ha um
 * tempo. Sem conferir a data, um aparelho offline desde ontem -- com a meta de
 * ontem cumprida -- ficaria calado o dia inteiro de hoje. Na duvida, avisa.
 */
internal fun metaJaBatida(dia: AguaDiaEntity?, config: ConfigAguaEntity?, hoje: LocalDate): Boolean {
    val meta = config?.dailyGoal ?: return false
    if (meta <= 0 || dia == null) return false
    return dia.day == hoje.toString() && dia.glasses >= meta
}

/** `"08:00"` em minutos desde a meia-noite, ou `null` se o texto nao servir. */
private fun emMinutos(hora: String?): Int? {
    val partes = hora?.split(":") ?: return null
    if (partes.size < 2) return null
    val h = partes[0].trim().toIntOrNull() ?: return null
    val m = partes[1].trim().toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/**
 * Quantos minutos a janela dura.
 *
 * Inicio igual ao fim cai no segundo ramo e da 1440 -- o dia inteiro. Nao e
 * acaso: e o que `_in_hydration_window` faz no servidor, onde a condicao
 * `agora >= inicio or agora < fim` e verdadeira o tempo todo quando os dois sao
 * iguais. Divergir aqui faria o mesmo ajuste avisar no computador e nao no
 * celular.
 */
private fun duracaoDaJanela(inicio: Int, fim: Int): Long =
    if (inicio < fim) (fim - inicio).toLong() else (MINUTOS_DO_DIA - inicio + fim).toLong()
