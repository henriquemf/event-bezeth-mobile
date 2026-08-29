package com.beazeth.notifier.ui.telas.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.BlocoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O estado do planner. Faz o papel do `store.js` do site: toda escrita passa
 * por aqui e vai para o Room, e a tela nunca espera a rede.
 */
class PlannerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    val blocos = repo.blocos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun apagar(bloco: BlocoEntity) = viewModelScope.launch { repo.apagarBloco(bloco.id) }

    /** Cria ou atualiza, pelo id: zero e o bloco que ainda nao existe. */
    /**
     * Grava os blocos que o formulario montou -- um por dia marcado.
     *
     * **Em serie, dentro de UMA corrotina.** `criarBloco` le o menor id da
     * tabela para derivar o proximo provisorio negativo; duas criacoes em
     * paralelo leriam o mesmo menor id e a segunda gravaria por cima da
     * primeira. Um `launch` por bloco parecia inofensivo e perderia dois dos
     * tres de uma aula de segunda, quarta e sexta.
     */
    fun salvar(blocos: List<BlocoEntity>) = viewModelScope.launch {
        for (bloco in blocos) {
            if (bloco.id == 0L) repo.criarBloco(bloco) else repo.atualizarBloco(bloco)
        }
    }

    /**
     * Arrastar um bloco na grade: muda dia e horario, preserva a duracao.
     *
     * Passa pelo mesmo `atualizarBloco` do formulario, entao a escrita entra na
     * fila de pendencias como qualquer outra -- arrastar sem rede funciona e
     * sobe depois.
     */
    fun mover(bloco: BlocoEntity, dia: Int, inicio: Int) {
        val duracao = bloco.endMinute - bloco.startMinute
        val comeco = inicio.coerceIn(0, MINUTOS_DO_DIA - duracao)
        if (comeco == bloco.startMinute && dia == bloco.dayOfWeek) return
        salvar(
            listOf(
                bloco.copy(
                    dayOfWeek = if (bloco.isRoutine) bloco.dayOfWeek else dia,
                    startMinute = comeco,
                    endMinute = comeco + duracao,
                )
            )
        )
    }
}
