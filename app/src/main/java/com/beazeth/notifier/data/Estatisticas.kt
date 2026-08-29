package com.beazeth.notifier.data

import android.content.Context
import com.beazeth.notifier.data.local.BancoLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * O que o app ja acumulou, para a tela de perfil.
 *
 * ## De onde vem
 *
 * Do Room, e so do Room -- as mesmas tabelas que desenham as telas. Nao ha
 * requisicao nenhuma aqui: as contas saem instantaneas, funcionam no aviao, e
 * uma tarefa riscada agora muda o numero antes de subir para o servidor.
 *
 * O que o Room tem e o que a sincronizacao trouxe, e ela traz o historico
 * inteiro da conta. So o foco (pomodoro) e diferente, porque nunca existiu no
 * servidor: esse conta a partir do dia em que a tabela nasceu, e a tela diz
 * isso em vez de fingir um total antigo.
 */
data class Estatisticas(
    val coposAoTodo: Int = 0,
    val diasBebendo: Int = 0,
    val recordeDeCopos: Int = 0,
    val diasNaMeta: Int = 0,
    val metaDeCopos: Int = 0,
    val mlPorCopo: Int = 0,

    val tarefasFeitas: Int = 0,
    val tarefasAoTodo: Int = 0,

    val postits: Int = 0,
    val eventos: Int = 0,
    val blocos: Int = 0,
    val minutosDaSemana: Int = 0,

    val pomodoros: Int = 0,
    val minutosFocados: Int = 0,
    /** Instante do primeiro pomodoro contado, ou `null` se nao houver nenhum. */
    val focandoDesde: Long? = null,
) {
    /** Litros bebidos, do total de copos e do tamanho do copo configurado. */
    val litros: Float get() = coposAoTodo * mlPorCopo / 1000f

    val temAlgumaCoisa: Boolean
        get() = coposAoTodo > 0 || tarefasAoTodo > 0 || postits > 0 ||
            eventos > 0 || blocos > 0 || pomodoros > 0
}

/**
 * As estatisticas, vivas: a tela fica aberta e os numeros acompanham.
 *
 * Os `combine` sao aninhados em grupos porque a versao com todos os fluxos de
 * uma vez so existe recebendo um `Array<*>` -- e ai cada campo voltaria como
 * `Any?` para ser convertido na mao, que e exatamente o tipo de codigo que
 * compila depois de trocar dois numeros de lugar.
 */
fun fluxoDeEstatisticas(context: Context): Flow<Estatisticas> {
    val banco = BancoLocal.obter(context.applicationContext)

    val agua = combine(
        banco.agua().coposPorDia(),
        banco.agua().observarConfig(),
    ) { dias, config ->
        val meta = config?.dailyGoal ?: 0
        Estatisticas(
            coposAoTodo = dias.sum(),
            diasBebendo = dias.count { it > 0 },
            recordeDeCopos = dias.maxOrNull() ?: 0,
            diasNaMeta = if (meta > 0) dias.count { it >= meta } else 0,
            metaDeCopos = meta,
            mlPorCopo = config?.glassMl ?: 0,
        )
    }

    val escrito = combine(
        banco.tarefas().quantasFeitas(),
        banco.tarefas().quantasAoTodo(),
        banco.notas().quantos(),
        banco.eventos().quantos(),
    ) { feitas, todas, notas, eventos ->
        listOf(feitas, todas, notas, eventos)
    }

    val tempo = combine(
        banco.blocos().quantos(),
        banco.blocos().minutosDaSemana(),
        banco.pomodoros().quantos(),
        banco.pomodoros().minutosFocados(),
        banco.pomodoros().primeiroEm(),
    ) { blocos, minutosSemana, pomodoros, minutosFoco, desde ->
        Estatisticas(
            blocos = blocos,
            minutosDaSemana = minutosSemana,
            pomodoros = pomodoros,
            minutosFocados = minutosFoco,
            focandoDesde = desde,
        )
    }

    return combine(agua, escrito, tempo) { a, e, t ->
        a.copy(
            tarefasFeitas = e[0],
            tarefasAoTodo = e[1],
            postits = e[2],
            eventos = e[3],
            blocos = t.blocos,
            minutosDaSemana = t.minutosDaSemana,
            pomodoros = t.pomodoros,
            minutosFocados = t.minutosFocados,
            focandoDesde = t.focandoDesde,
        )
    }
}
