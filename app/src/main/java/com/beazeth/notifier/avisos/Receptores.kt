package com.beazeth.notifier.avisos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Os dois pontos por onde o sistema fala com os avisos.
 *
 * Ficam no mesmo arquivo porque sao a mesma coisa vista de dois lados -- "algo
 * aconteceu la fora, recalcule" -- e compartilham [emSegundoPlano], que e onde
 * mora o cuidado que os dois precisam ter.
 */

/** O alarme de um dos tres tipos venceu. Ver [Alarmes]. */
class Despertador : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Acao desconhecida e ignorada em vez de virar um recalculo: o receptor
        // e `exported="false"`, mas um `Intent` sem acao chegando por engano
        // nao deve disparar trabalho.
        val tipo = Tipo.entries.firstOrNull { it.acao == intent.action } ?: return
        emSegundoPlano(this) { Lembretes.tocou(context.applicationContext, tipo) }
    }
}

/**
 * O aparelho religou, o app foi atualizado, ou o relogio mudou.
 *
 * Os quatro casos apagam os alarmes marcados, cada um a seu modo:
 *
 * - **Religar** limpa a tabela de alarmes do sistema inteira. Sem isto, quem
 *   reiniciasse o tablet a noite acordaria sem lembrete de agua nenhum -- e nao
 *   teria como desconfiar, porque o app abre igual.
 * - **Atualizar o app** (`MY_PACKAGE_REPLACED`) faz o mesmo. Instalar o `.apk`
 *   novo por cima e justamente o que acontece toda vez que sai uma versao.
 * - **Mudar o relogio ou o fuso** nao apaga nada, mas os alarmes sao em tempo
 *   absoluto (`RTC_WAKEUP`) enquanto a janela da agua e os horarios dos eventos
 *   sao em hora LOCAL. Depois de um voo, "as 08:00" passa a ser outro instante,
 *   e so recalculando para bater de novo.
 */
class AoLigar : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conhecida = intent.action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (!conhecida) return
        emSegundoPlano(this) { Lembretes.rearmar(context.applicationContext) }
    }
}

/**
 * Roda o trabalho fora da thread principal sem deixar o sistema matar o
 * processo no meio.
 *
 * `onReceive` roda na thread principal e o processo pode ser encerrado assim
 * que ela retorna -- um `launch` solto seria cancelado no meio da leitura do
 * banco, e o alarme nunca seria remarcado. `goAsync()` segura o processo vivo
 * ate `finish()`, com uns dez segundos de teto; ler o Room e o DataStore leva
 * milissegundos.
 *
 * O `runCatching` existe porque uma excecao aqui derruba o app **sem tela e sem
 * pista**: o processo morre em segundo plano e o unico sintoma e "os avisos
 * pararam". Este e o unico `Log` do app inteiro, e e por isso -- sem ele, um
 * lembrete que nao chega nao tem por onde ser investigado.
 */
private fun emSegundoPlano(receptor: BroadcastReceiver, trabalho: suspend () -> Unit) {
    val pendente = receptor.goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            runCatching { trabalho() }.onFailure {
                Log.e("Avisos", "Falha ao recalcular os lembretes", it)
            }
        } finally {
            pendente.finish()
        }
    }
}
