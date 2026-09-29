package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * Esta acao apagaria alteracoes que ainda nao subiram para o servidor.
 *
 * Aparece nos dois lugares onde o app limpa o banco do aparelho: ao entrar com
 * OUTRA conta e ao sair da conta. O que ja subiu esta salvo no servidor; o que
 * esta na fila so existe aqui, e nao sai sem a pessoa decidir, sabendo.
 *
 * O botao em destaque e o que NAO apaga, e fechar o dialogo tambem nao apaga:
 * descartar exige tocar, de proposito, no botao que diz isso com todas as
 * letras. E o texto diz o caminho que nao perde nada.
 */
@Composable
fun DialogoDePendentes(
    alteracoes: Int,
    /** De quem sao as alteracoes, se se sabe. */
    email: String?,
    /** O que apaga: "Entrar com outra conta", "Sair da conta". */
    acao: String,
    /** Como nao perder nada, dito para quem esta na frente da tela. */
    caminhoSeguro: String,
    aoApagar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    val cores = Doce
    val quantas = if (alteracoes == 1) "1 alteração" else "$alteracoes alterações"
    val enviadas = if (alteracoes == 1) "foi enviada" else "foram enviadas"
    val essas = if (alteracoes == 1) "essa alteração" else "essas alterações"
    val existem = if (alteracoes == 1) "ela não existe" else "elas não existem"
    val deQuem = email?.let { " da conta $it" }.orEmpty()

    Dialog(onDismissRequest = aoVoltar) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cores.superficie)
                .padding(Espaco.e4),
            verticalArrangement = Arrangement.spacedBy(Espaco.e3),
        ) {
            Text("Alterações que ainda não subiram", style = TipografiaBeazeth.titleLarge, color = cores.tinta)
            Text(
                "Este aparelho tem $quantas$deQuem que ainda não $enviadas ao servidor. " +
                    "$acao apaga $essas deste aparelho, e $existem em nenhum outro lugar.",
                style = TipografiaBeazeth.bodyMedium,
                color = cores.tinta,
            )
            Text(caminhoSeguro, style = TipografiaBeazeth.bodyMedium, color = cores.tintaSuave)
            BotaoPilula(
                texto = "Voltar e não apagar nada",
                aoTocar = aoVoltar,
                destacado = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BotaoPilula(
                texto = "Apagar as alterações e continuar",
                aoTocar = aoApagar,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
