package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * Um ajuste de liga/desliga: o que ele faz em cima, o detalhe embaixo, a chave
 * na ponta.
 *
 * Saiu de tres copias -- os avisos do perfil, o lembrete de agua e o descanso
 * automatico do pomodoro --, e o que as tres repetiam era justamente o que
 * tem de ser igual: as cores da chave. Com a copia, trocar o tom do "ligado"
 * num lugar deixaria os outros dois no tom velho.
 */
@Composable
internal fun LinhaComChave(
    titulo: String,
    descricao: String,
    marcado: Boolean,
    aoMudar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = TipografiaBeazeth.titleMedium,
                color = cores.tinta,
            )
            Text(
                text = descricao,
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                color = cores.tintaSuave,
            )
        }
        Switch(
            checked = marcado,
            onCheckedChange = aoMudar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = cores.superficie,
                checkedTrackColor = cores.destaque,
                checkedBorderColor = cores.destaque,
                uncheckedThumbColor = cores.tintaSuave,
                uncheckedTrackColor = cores.fundoCampo,
                uncheckedBorderColor = cores.traco,
            ),
        )
    }
}
