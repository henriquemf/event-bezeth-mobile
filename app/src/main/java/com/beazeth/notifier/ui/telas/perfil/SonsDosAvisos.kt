package com.beazeth.notifier.ui.telas.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beazeth.notifier.avisos.Canal
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.avisos.OpcaoDeSom
import com.beazeth.notifier.avisos.Som
import com.beazeth.notifier.avisos.SomDaFesta
import com.beazeth.notifier.avisos.resolverSom
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.launch

/**
 * O som de cada aviso, um por um.
 *
 * ## Uma linha por aviso, fechada
 *
 * Sao cinco escolhas de seis opcoes cada. Tudo aberto seriam trinta linhas de
 * "Ouvir" empilhadas, e ninguem acharia nada. Fechada, cada linha diz o que
 * interessa de relance -- qual aviso e que som ele tem --, e tocar nela abre as
 * opcoes ali mesmo, com "Ouvir" em cada uma. Abrir uma fecha a outra: e uma
 * escolha de cada vez.
 *
 * ## Por que a festa esta aqui, se nao e um aviso
 *
 * Para quem esta escolhendo sons, o fim do foco com o app aberto e so mais um
 * som do app -- e o mais frequente de todos. Separa-lo em outra tela seria
 * esconder justamente a escolha que mais se nota.
 */
@Composable
internal fun SonsDosAvisos(prefs: Preferencias) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var aberta by remember { mutableStateOf<String?>(null) }
    val antigo by prefs.somDoAviso.collectAsStateWithLifecycle("")

    fun alternar(chave: String) {
        aberta = if (aberta == chave) null else chave
    }

    Column(verticalArrangement = Arrangement.spacedBy(Espaco.e2)) {
        for (canal in Canal.entries) {
            val proprio by prefs.somDoCanal(canal.base).collectAsStateWithLifecycle("")
            val som = resolverSom(proprio, antigo, canal)
            Momento(
                titulo = canal.titulo,
                descricao = canal.descricao,
                escolhida = som,
                opcoes = Som.entries,
                aberta = aberta == canal.base,
                aoAbrir = { alternar(canal.base) },
                aoEscolher = { novo ->
                    escopo.launch {
                        prefs.definirSomDoCanal(canal.base, novo.chave)
                        // Trocar o som TROCA O CANAL DE LUGAR (ver `Som`), entao
                        // o canal novo precisa nascer agora -- senao o proximo
                        // aviso sairia por um canal que ainda nao existe.
                        Lembretes.rearmar(contexto)
                    }
                },
            )
        }

        val festa by prefs.somDaFesta.collectAsStateWithLifecycle("")
        Momento(
            titulo = "Festa do fim do foco",
            descricao = "Com o app aberto, junto do confete.",
            escolhida = SomDaFesta.porChave(festa),
            opcoes = SomDaFesta.entries,
            aberta = aberta == CHAVE_DA_FESTA,
            aoAbrir = { alternar(CHAVE_DA_FESTA) },
            aoEscolher = { novo -> escopo.launch { prefs.definirSomDaFesta(novo.chave) } },
        )
    }
}

/** A festa nao e um [Canal], mas divide a mesma "qual esta aberta". */
private const val CHAVE_DA_FESTA = "festa"

@Composable
private fun Momento(
    titulo: String,
    descricao: String,
    escolhida: OpcaoDeSom,
    opcoes: List<OpcaoDeSom>,
    aberta: Boolean,
    aoAbrir: () -> Unit,
    aoEscolher: (OpcaoDeSom) -> Unit,
) {
    val cores = Doce
    val forma = RoundedCornerShape(Canto.caixa)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (aberta) cores.destaque.copy(alpha = 0.08f) else cores.fundoCampo)
            .border(1.dp, if (aberta) cores.destaque else cores.traco, forma),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (aberta) "Fechar" else "Escolher som", onClick = aoAbrir)
                .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = titulo, style = TipografiaBeazeth.titleMedium, color = cores.tinta)
                Text(
                    text = descricao,
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                    color = cores.tintaSuave,
                )
            }
            Text(
                text = escolhida.nome,
                style = TipografiaBeazeth.labelLarge,
                color = cores.destaqueEscuro,
            )
            // O mesmo "v" nas duas posicoes, girado: aberto aponta para cima,
            // que e para onde a lista se recolhe.
            Text(
                text = "⌄",
                style = TipografiaBeazeth.titleMedium,
                color = cores.tintaSuave,
                modifier = Modifier.rotate(if (aberta) 180f else 0f),
            )
        }

        if (aberta) {
            EscolhaDeSom(
                opcoes = opcoes,
                escolhida = escolhida,
                aoEscolher = aoEscolher,
                modifier = Modifier.padding(start = Espaco.e2, end = Espaco.e2, bottom = Espaco.e2),
            )
        }
    }
}
