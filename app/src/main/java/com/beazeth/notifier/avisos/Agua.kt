package com.beazeth.notifier.avisos

import com.beazeth.notifier.data.local.AguaDiaEntity
import com.beazeth.notifier.data.local.ConfigAguaEntity
import java.time.LocalDateTime

/**
 * Quando cai o proximo lembrete de agua.
 *
 * A configuracao -- ligado, intervalo, janela -- e a MESMA que o site guarda e
 * a sincronizacao ja traz para o Room. Nada aqui e escolhido de novo: mudar o
 * intervalo no computador muda o do celular na proxima sincronizacao.
 *
 * ## Um intervalo depois do ultimo copo, como no servidor
 *
 * O servidor conta a partir de `last_sent_at`, e beber um copo empurra esse
 * carimbo (`/api/hydration/drink`): quem acabou de beber so e cobrada de novo
 * um intervalo inteiro depois. Aqui e a mesma conta sobre um marco guardado no
 * aparelho (`Preferencias.aguaMarcoEm`): o ultimo copo bebido OU o ultimo
 * lembrete entregue, o que for mais recente.
 *
 * A primeira versao usava uma grade presa a abertura da janela (08:00, 09:00,
 * 10:00...), para os horarios nao derivarem. Estava errada de um jeito que so
 * aparece usando: quem bebia as 09:55 recebia "hora de beber agua" as 10:00,
 * cinco minutos depois -- e parecia que o lembrete era disparado pelo copo. O
 * lembrete e para quem NAO bebeu; contar a partir do ultimo copo e o que faz
 * ele calar para quem bebeu.
 *
 * ## Vencido e "agora"
 *
 * [proximoCopo] pode devolver um instante que nao esta no futuro. E o caso do
 * marco de ontem, ou de nenhum marco: o lembrete esta vencido, e quem chama
 * entrega na hora e recomeca a contar de agora -- exatamente o que o agendador
 * do servidor faz quando `last_sent_at` e antigo ou nulo.
 */

/** Titulo e texto sao os do servidor, letra por letra: um app so, duas telas. */
const val TITULO_DA_AGUA = "MOMO BEBA ÁGUA 💗"
const val TEXTO_DA_AGUA = "Meu amorzinho, hora de BEBER ÁGUA <3"

/**
 * O proximo lembrete: um intervalo depois de [ultimoMarco], ou [agora] se isso
 * ja passou, sempre dentro da janela. `null` se nao ha o que marcar.
 *
 * `null` acontece com o lembrete desligado, sem configuracao nenhuma (quem usa
 * sem conta nunca recebeu uma) ou com hora malformada no banco. Nos tres casos
 * o certo e ficar quieto -- e e o mesmo criterio da barra lateral, que so
 * mostra o copo quando `enabled` e verdadeiro.
 *
 * Fora da janela, o devido e empurrado para a proxima abertura: um copo as
 * 21:30 com intervalo de uma hora e janela ate as 22:00 nao lembra as 22:30,
 * lembra as 08:00 de amanha.
 */
internal fun proximoCopo(
    config: ConfigAguaEntity?,
    ultimoMarco: LocalDateTime?,
    agora: LocalDateTime,
): LocalDateTime? {
    if (config == null) return null
    val janela = Janela.de(config) ?: return null
    val intervalo = config.intervalMinutes.coerceAtLeast(1).toLong()
    val devido = ultimoMarco?.plusMinutes(intervalo)?.takeIf { it.isAfter(agora) } ?: agora
    return if (janela.contem(devido)) devido else janela.proximaAbertura(devido)
}

/**
 * A proxima vez que a janela ABRE, estritamente depois de [apos].
 *
 * E para onde vai o alarme quando a meta do dia ja foi batida: nao ha mais o
 * que lembrar hoje, e acordar o aparelho a cada intervalo para descobrir isso
 * de novo seria gastar bateria a toa.
 */
internal fun proximaAbertura(config: ConfigAguaEntity?, apos: LocalDateTime): LocalDateTime? =
    Janela.de(config)?.proximaAbertura(apos)

/**
 * A meta do dia ja foi batida?
 *
 * **Aqui o app se afasta do site de proposito.** O servidor avisa a cada
 * intervalo ate a janela fechar, tenha a pessoa bebido oito copos ou nenhum.
 * Num navegador aberto isso passa; num celular no bolso, seis avisos depois da
 * meta cumprida sao exatamente o que faz alguem desligar os avisos do app -- e
 * ai o lembrete de amanha tambem morre.
 *
 * [dia] e a linha de HOJE no aparelho (`AguaDao.buscar` pela data local), ou
 * `null` se ninguem bebeu nada hoje ainda.
 */
internal fun metaJaBatida(dia: AguaDiaEntity?, config: ConfigAguaEntity?): Boolean {
    val meta = config?.dailyGoal ?: return false
    if (meta <= 0 || dia == null) return false
    return dia.glasses >= meta
}

/**
 * A janela do lembrete, em minutos do dia, com a regra do servidor para a que
 * cruza a meia-noite (22:30 -> 06:00).
 */
private class Janela(private val inicio: Int, private val fim: Int) {

    /**
     * O mesmo `_in_hydration_window` do servidor, inclusive no caso de inicio
     * igual a fim: la a condicao `agora >= inicio or agora < fim` e verdadeira
     * o tempo todo, ou seja, o dia inteiro. Divergir aqui faria o mesmo ajuste
     * avisar no computador e nao no celular.
     */
    fun contem(instante: LocalDateTime): Boolean {
        val m = instante.hour * 60 + instante.minute
        return if (inicio < fim) m >= inicio && m < fim else m >= inicio || m < fim
    }

    /** A abertura de hoje se ainda nao passou; senao, a de amanha. */
    fun proximaAbertura(apos: LocalDateTime): LocalDateTime {
        val hoje = apos.toLocalDate().atStartOfDay().plusMinutes(inicio.toLong())
        return if (hoje.isAfter(apos)) hoje else hoje.plusDays(1)
    }

    companion object {
        fun de(config: ConfigAguaEntity?): Janela? {
            if (config == null || !config.enabled) return null
            val inicio = emMinutos(config.startTime) ?: return null
            val fim = emMinutos(config.endTime) ?: return null
            return Janela(inicio, fim)
        }
    }
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
