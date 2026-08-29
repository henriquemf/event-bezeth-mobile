@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco

/**
 * Escolher uma hora: pelo relogio, ou digitando.
 *
 * ## Por que os dois
 *
 * O relogio de ponteiro do Material aceita qualquer minuto -- sao 60 posicoes --
 * mas quem escolhe e o dedo, e num mostrador de 190 dp cada minuto ocupa 3 graus.
 * Acertar 12:22 no ponteiro e mira, nao escolha. Marcar hora redonda, ao
 * contrario, e mais rapido no relogio do que em dois campos de texto.
 *
 * Entao nenhum dos dois e "o certo": um serve para 14:00 e o outro para 12:22.
 * O botao troca, e a escolha vale enquanto o dialogo estiver aberto.
 *
 * Nao ha arredondamento em lugar nenhum: o que este componente devolver e o que
 * vai para o banco. A grade de 15 minutos existe so no ARRASTE da grade do
 * planner, onde ela e o que torna o gesto utilizavel.
 */
@Composable
fun EscolhaDeHora(estado: TimePickerState, modifier: Modifier = Modifier) {
    val cores = Doce
    var digitando by rememberSaveable { mutableStateOf(false) }

    val paleta = TimePickerDefaults.colors(
        selectorColor = cores.destaque,
        containerColor = cores.superficie,
        periodSelectorSelectedContainerColor = cores.destaque.copy(alpha = 0.2f),
        timeSelectorSelectedContainerColor = cores.destaque.copy(alpha = 0.2f),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        // `TimeInput` e `TimePicker` compartilham o mesmo `TimePickerState`:
        // trocar de um para o outro no meio da escolha nao perde o que ja
        // estava marcado.
        if (digitando) {
            TimeInput(state = estado, colors = paleta)
        } else {
            TimePicker(state = estado, colors = paleta)
        }

        BotaoPilula(
            texto = if (digitando) "Usar o relógio" else "Digitar o horário",
            aoTocar = { digitando = !digitando },
        )
    }
}
