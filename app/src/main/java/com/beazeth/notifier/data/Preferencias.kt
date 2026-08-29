package com.beazeth.notifier.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.beazeth.notifier.ui.theme.FONTE_PADRAO
import com.beazeth.notifier.ui.theme.PALETA_PADRAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * O que a pessoa escolheu, e o que o Pomodoro precisa lembrar.
 *
 * DataStore separado do da sessao de proposito: sair da conta apaga o token e o
 * banco local, mas nao deve apagar o tema. Quem escolheu Lavender Kiss nao
 * espera voltar ao rosa so por ter feito login de novo.
 *
 * O estado do Pomodoro mora aqui e nao no ViewModel por um motivo concreto: a
 * queixa era "troco de tela e a contagem para". Guardando o INSTANTE EM QUE
 * TERMINA -- e nao os segundos restantes -- o timer continua correto mesmo se o
 * app for fechado, o processo morto ou o aparelho reiniciado. O relogio do
 * sistema faz a conta; nao ha nada para manter vivo.
 */
private val Context.prefsAparencia by preferencesDataStore(name = "aparencia")

class Preferencias(private val context: Context) {

    private val chaveTema = stringPreferencesKey("tema")
    private val chaveFonte = stringPreferencesKey("fonte")
    private val chaveEscuro = booleanPreferencesKey("escuro")

    private val chavePomoMinutos = intPreferencesKey("pomo_minutos")
    private val chavePomoFimEm = longPreferencesKey("pomo_fim_em")
    private val chavePomoRestante = intPreferencesKey("pomo_restante")
    private val chavePomoCreditado = longPreferencesKey("pomo_creditado")

    val tema: Flow<String> = context.prefsAparencia.data.map { it[chaveTema] ?: PALETA_PADRAO }
    val fonte: Flow<String> = context.prefsAparencia.data.map { it[chaveFonte] ?: FONTE_PADRAO }
    val escuro: Flow<Boolean> = context.prefsAparencia.data.map { it[chaveEscuro] ?: false }

    suspend fun definirTema(chave: String) {
        context.prefsAparencia.edit { it[chaveTema] = chave }
    }

    suspend fun definirFonte(chave: String) {
        context.prefsAparencia.edit { it[chaveFonte] = chave }
    }

    suspend fun definirEscuro(ligado: Boolean) {
        context.prefsAparencia.edit { it[chaveEscuro] = ligado }
    }

    // ----------------------------------------------------------- pomodoro

    /** Duracao escolhida, em minutos. */
    val pomoMinutos: Flow<Int> = context.prefsAparencia.data.map { it[chavePomoMinutos] ?: 25 }

    /**
     * Quando a contagem termina, em milissegundos do relogio do sistema.
     *
     * Zero significa parado. E o instante, e nao os segundos que faltam, porque
     * o instante nao envelhece: reabrir o app cinco minutos depois da o numero
     * certo sem ninguem ter contado nada nesse meio-tempo.
     */
    val pomoFimEm: Flow<Long> = context.prefsAparencia.data.map { it[chavePomoFimEm] ?: 0L }

    /** Segundos que faltavam quando alguem pausou. So vale com `pomoFimEm` zerado. */
    val pomoRestante: Flow<Int> = context.prefsAparencia.data.map {
        it[chavePomoRestante] ?: ((it[chavePomoMinutos] ?: 25) * 60)
    }

    /**
     * O `pomoFimEm` do ultimo pomodoro que ja entrou na conta do perfil.
     *
     * Existe para o credito nao acontecer duas vezes. O momento em que um
     * pomodoro termina nao e um evento que alguem receba: o app pode estar
     * fechado, e quem descobre e a primeira tela que olhar para o relogio
     * depois -- podendo ser duas ao mesmo tempo (a tela do pomodoro e o widget
     * da lateral). Comparar com este carimbo faz o segundo a chegar nao ter o
     * que fazer.
     */
    val pomoCreditado: Flow<Long> =
        context.prefsAparencia.data.map { it[chavePomoCreditado] ?: 0L }

    suspend fun marcarPomodoroCreditado(fimEm: Long) {
        context.prefsAparencia.edit { it[chavePomoCreditado] = fimEm }
    }

    suspend fun salvarPomodoro(minutos: Int, fimEm: Long, restante: Int) {
        context.prefsAparencia.edit {
            it[chavePomoMinutos] = minutos
            it[chavePomoFimEm] = fimEm
            it[chavePomoRestante] = restante
        }
    }
}
