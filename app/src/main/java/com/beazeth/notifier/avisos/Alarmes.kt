package com.beazeth.notifier.avisos

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Quem acorda o aparelho na hora marcada.
 *
 * ## Um alarme por assunto, sempre o PROXIMO
 *
 * Nao ha um alarme por evento da agenda. Ha **um** alarme de evento, marcado
 * para o proximo lembrete que vencer; quando ele toca, o proprio despertador
 * calcula o seguinte e remarca. Cem eventos no calendario continuam sendo um
 * alarme.
 *
 * A alternativa -- marcar tudo de uma vez -- esbarra em dois muros. O Android
 * limita alarmes exatos por app (500 desde o 14), e uma agenda com regra de
 * curso arma tres lembretes por evento. E cada sincronizacao teria de descobrir
 * quais dos alarmes ja marcados nao valem mais, porque um evento apagado no
 * site continuaria avisando no celular.
 *
 * ## Por que `AlarmManager` e nao `WorkManager`
 *
 * O `SyncWorker` usa WorkManager porque sincronizar "daqui a pouco" esta otimo.
 * Aviso tem hora. O WorkManager agrupa trabalho para poupar bateria e pode
 * atrasar minutos ou horas -- um pomodoro de 25 minutos que apita as 40 nao e
 * um pomodoro.
 */
enum class Tipo(val acao: String, val codigo: Int) {
    AGUA("com.beazeth.notifier.AVISO_AGUA", 1),
    POMODORO("com.beazeth.notifier.AVISO_POMODORO", 2),
    EVENTO("com.beazeth.notifier.AVISO_EVENTO", 3),
}

/**
 * Marca (ou remarca) o alarme de [tipo] para [quando], em milissegundos do
 * relogio de parede.
 *
 * `RTC_WAKEUP` -- relogio de parede, e acordando o aparelho. Os tres avisos
 * sao presos a uma HORA ("as 14:00", "daqui a 25 minutos"), e nao a um tempo de
 * atividade; e `WAKEUP` porque um lembrete que so chega quando alguem liga a
 * tela nao lembrou nada.
 *
 * Se o `PendingIntent` de um tipo ja existir, `FLAG_UPDATE_CURRENT` reaproveita
 * o mesmo -- e o `AlarmManager` trata isso como remarcacao, nao como um segundo
 * alarme. E o que faz "recalcular tudo" ser uma operacao segura de repetir.
 */
fun marcarAlarme(context: Context, tipo: Tipo, quando: Long) {
    val gerente = context.getSystemService(AlarmManager::class.java) ?: return
    val alvo = pendente(context, tipo)

    // `setExactAndAllowWhileIdle` e o unico que atravessa o Doze -- o modo de
    // economia em que o aparelho parado adia quase tudo. Sem o `AllowWhileIdle`
    // o lembrete de agua da tarde chegaria junto com o da noite, quando alguem
    // finalmente pegasse o aparelho.
    //
    // A degradacao existe porque a permissao de alarme exato e revogavel nos
    // ajustes do sistema, mesmo estando declarada. Revogada, `setExact*` LANCA
    // `SecurityException` e o app cairia -- num BroadcastReceiver, o que
    // derruba tudo e nao avisa ninguem. O inexato chega atrasado; cair nao
    // chega nunca.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gerente.canScheduleExactAlarms()) {
        gerente.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, alvo)
    } else {
        gerente.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, alvo)
    }
}

/** Desmarca o alarme de [tipo]. Nao ha nada a fazer se ele nao existia. */
fun desmarcarAlarme(context: Context, tipo: Tipo) {
    val gerente = context.getSystemService(AlarmManager::class.java) ?: return
    gerente.cancel(pendente(context, tipo))
}

/**
 * O aparelho ainda deixa marcar alarme exato?
 *
 * Serve a tela de perfil, que precisa poder dizer "os avisos podem chegar
 * atrasados" em vez de deixar a pessoa achar que estao quebrados.
 */
fun alarmeExatoLiberado(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val gerente = context.getSystemService(AlarmManager::class.java) ?: return false
    return gerente.canScheduleExactAlarms()
}

/**
 * O `PendingIntent` de um tipo -- sempre o mesmo, por causa do [Tipo.codigo].
 *
 * Extras nao entram na identidade de um `PendingIntent`; acao e codigo entram.
 * Por isso o que distingue os tres alarmes e a ACAO, e nao um extra: com a
 * mesma acao e o mesmo codigo, marcar o alarme de agua apagaria o do pomodoro.
 */
private fun pendente(context: Context, tipo: Tipo): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        tipo.codigo,
        Intent(context, Despertador::class.java).setAction(tipo.acao),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
