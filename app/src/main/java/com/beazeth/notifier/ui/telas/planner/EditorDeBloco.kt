@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.beazeth.notifier.ui.telas.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beazeth.notifier.data.local.BlocoEntity
import com.beazeth.notifier.ui.componentes.AvisoDeErro
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CampoDoce
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.EscolhaDeHora
import com.beazeth.notifier.ui.componentes.SeletorEmCaixa
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * O formulario de bloco do planner -- criar e editar.
 *
 * Existia so no site, e o app ficava com metade do planner: dava para ver e
 * apagar, nao para marcar nada. As funcoes de escrever bloco ja estavam
 * prontas no repositorio e nao tinham quem as chamasse; foi assim que o
 * auditor de codigo morto encontrou o buraco.
 *
 * **A hora escolhida aqui vale como foi escolhida.** A grade de 15 minutos
 * existe para o ARRASTE, onde o dedo nao tem precisao de minuto; num relogio em
 * que se aponta o minuto exato, arredondar e desfazer o que a pessoa acabou de
 * fazer -- 12:20 virava 12:15 na frente dela. O servidor tambem parou de
 * arredondar (`parse_block_payload`), entao o que o formulario mostra e o que
 * fica gravado.
 *
 * **Os dias sao caixas que ligam e desligam, e nao uma escolha unica.** Aula de
 * segunda, quarta e sexta e o caso comum, e ate aqui so havia dois extremos: um
 * dia, ou "Rotina", que sao os sete. Cada dia marcado vira um bloco proprio --
 * o primeiro fica com o bloco que estava sendo editado, os outros nascem. Sao
 * linhas independentes depois disso: mover a de quarta nao mexe na de sexta, e
 * e assim que se quer, porque a aula de quarta e que costuma mudar de sala.
 *
 * **Fim antes do inicio nao e erro, e engano comum.** Quem escolhe 14:00-13:00
 * quase sempre queria 13:00-14:00 e mexeu no campo errado. O formulario avisa
 * em vez de gravar um bloco de duracao negativa que o servidor depois conserta
 * de um jeito que ninguem pediu.
 *
 * **O [rascunho] traz o que a grade ja sabe.** Tocar numa quinta-feira as 14:00
 * abre o formulario NAQUELE dia e hora -- o mesmo caminho que o `dateClick` do
 * calendario do site faz. Sem isso, quem tocou no lugar certo teria de dizer de
 * novo, em dois campos, onde tinha acabado de tocar.
 */
@Composable
internal fun EditorDeBloco(
    rascunho: BlocoEntity,
    novo: Boolean,
    aoFechar: () -> Unit,
    /** Um bloco por dia marcado -- ver a doc acima. Chegam em ordem: o
     *  primeiro carrega o id que estava sendo editado, os outros nascem. */
    aoSalvar: (List<BlocoEntity>) -> Unit,
    aoApagar: () -> Unit,
) {
    val cores = Doce

    var titulo by remember { mutableStateOf(rascunho.title) }
    var notas by remember { mutableStateOf(rascunho.notes) }
    var dias by remember { mutableStateOf(setOf(rascunho.dayOfWeek)) }
    var inicio by remember { mutableIntStateOf(rascunho.startMinute) }
    var fim by remember { mutableIntStateOf(rascunho.endMinute) }
    var cor by remember { mutableStateOf(rascunho.color) }
    var rotina by remember { mutableStateOf(rascunho.isRoutine) }
    var aviso by remember { mutableStateOf<String?>(null) }

    var escolhendo by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = aoFechar) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            item {
                CartaoDaTela(titulo = if (novo) "Novo bloco" else "Editar bloco") {
                    CampoDoce(
                        rotulo = "Título",
                        valor = titulo,
                        aoMudar = { titulo = it; aviso = null },
                    )
                    CampoDoce(
                        rotulo = "Notas",
                        valor = notas,
                        aoMudar = { notas = it },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                    ) {
                        SeletorEmCaixa(
                            rotulo = "Início",
                            valor = comoHora(inicio),
                            aoTocar = { escolhendo = "inicio" },
                            modifier = Modifier.weight(1f),
                        )
                        SeletorEmCaixa(
                            rotulo = "Fim",
                            valor = comoHora(fim),
                            aoTocar = { escolhendo = "fim" },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Bloco de rotina vale todos os dias -- e o que
                    // `parse_block_payload` faz ao ignorar `day_of_week`
                    // quando `is_routine`. Sem esconder as abas aqui, a tela
                    // pediria um dia que o servidor vai descartar.
                    if (!rotina) {
                        Text(
                            text = "Dias",
                            style = TipografiaBeazeth.labelLarge,
                            color = cores.tintaSuave,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (indice in DIAS_CURTOS.indices) {
                                EscolhaDeDia(
                                    rotulo = DIAS_CURTOS[indice],
                                    ativo = indice in dias,
                                    aoTocar = {
                                        dias = if (indice in dias) {
                                            dias - indice
                                        } else {
                                            dias + indice
                                        }
                                        aviso = null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (dias.size > 1) {
                            Text(
                                text = "Serão ${dias.size} blocos, um por dia.",
                                style = TipografiaBeazeth.bodyMedium,
                                color = cores.tintaSuave,
                            )
                        }
                    }

                    Text(
                        text = "Cor",
                        style = TipografiaBeazeth.labelLarge,
                        color = cores.tintaSuave,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.e2)) {
                        for (nome in CORES_DO_BLOCO) {
                            AmostraDoBloco(
                                tom = corDoPlanner(nome),
                                ativa = nome == cor,
                                aoTocar = { cor = nome },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Canto.caixa))
                            .clickable { rotina = !rotina }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .clip(RoundedCornerShape(Canto.marca))
                                .background(if (rotina) cores.destaque else cores.fundoCampo)
                                .border(
                                    2.dp,
                                    if (rotina) {
                                        cores.destaque
                                    } else {
                                        cores.tintaSuave.copy(alpha = 0.55f)
                                    },
                                    RoundedCornerShape(Canto.marca),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (rotina) Text(text = "✓", fontSize = 12.sp, color = Color.White)
                        }
                        Column {
                            Text(
                                text = "Rotina",
                                style = TipografiaBeazeth.bodyLarge,
                                color = cores.tinta,
                            )
                            Text(
                                text = "Aparece em todos os dias da semana.",
                                style = TipografiaBeazeth.bodyMedium,
                                color = cores.tintaSuave,
                            )
                        }
                    }

                    aviso?.let { AvisoDeErro(it) }

                    BotaoPrimario(
                        texto = "Salvar",
                        aoTocar = {
                            val limpo = titulo.trim()
                            // Sem arredondar: o relogio ja devolveu o minuto
                            // exato, e o que o campo mostra e o que vai.
                            val comeco = inicio.coerceIn(0, MINUTOS_DO_DIA - 1)
                            val termino = fim.coerceIn(0, MINUTOS_DO_DIA)
                            // Rotina ja e "todos os dias" numa linha so --
                            // `parse_block_payload` ignora o dia quando ela
                            // esta ligada. Espalha-la pelos sete daria sete
                            // blocos dizendo a mesma coisa.
                            val alvos = if (rotina) listOf(0) else dias.sorted()

                            when {
                                limpo.isEmpty() ->
                                    aviso = "Informe o título do bloco."

                                alvos.isEmpty() ->
                                    aviso = "Escolha pelo menos um dia."

                                termino <= comeco ->
                                    aviso = "O fim precisa vir depois do início."

                                else -> {
                                    aoSalvar(
                                        alvos.mapIndexed { posicao, diaAlvo ->
                                            rascunho.copy(
                                                // So o primeiro herda o id: os
                                                // outros sao blocos novos, e id
                                                // zero e como o repositorio
                                                // sabe disso.
                                                id = if (posicao == 0) rascunho.id else 0L,
                                                title = limpo,
                                                notes = notas.trim(),
                                                dayOfWeek = diaAlvo,
                                                startMinute = comeco,
                                                endMinute = termino,
                                                color = cor,
                                                isRoutine = rotina,
                                            )
                                        }
                                    )
                                    aoFechar()
                                }
                            }
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Cancelar",
                            style = TipografiaBeazeth.bodyMedium,
                            color = cores.tintaSuave,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = aoFechar)
                                .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
                        )
                        // "Remover" so existe em bloco que existe. No site o
                        // botao fica `hidden` na criacao pelo mesmo motivo.
                        if (!novo) {
                            Text(
                                text = "Remover",
                                style = TipografiaBeazeth.bodyMedium,
                                color = Color(0xFFB4485F),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable(onClick = aoApagar)
                                    .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
                            )
                        }
                    }
                }
            }
        }
    }

    val alvo = escolhendo
    if (alvo != null) {
        val minutos = if (alvo == "inicio") inicio else fim
        val estado = rememberTimePickerState(
            initialHour = minutos / 60 % 24,
            initialMinute = minutos % 60,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { escolhendo = null }) {
            CartaoDaTela(titulo = if (alvo == "inicio") "Início" else "Fim") {
                EscolhaDeHora(estado = estado)
                BotaoPrimario(
                    texto = "Pronto",
                    aoTocar = {
                        val escolhido = estado.hour * 60 + estado.minute
                        if (alvo == "inicio") {
                            inicio = escolhido
                            // Mover o inicio arrasta o fim junto, mantendo a
                            // duracao. Sem isto, adiantar um bloco de uma hora
                            // exigiria mexer nos dois campos -- e esquecer o
                            // segundo da um bloco de duracao negativa.
                            //
                            // Os 15 minutos aqui sao um PADRAO, nao uma regra:
                            // so aparecem quando o fim ficou para tras e alguem
                            // precisa de um valor qualquer no lugar dele.
                            if (fim <= escolhido) {
                                fim = (escolhido + GRADE).coerceAtMost(MINUTOS_DO_DIA)
                            }
                        } else {
                            fim = escolhido
                        }
                        aviso = null
                        escolhendo = null
                    },
                )
            }
        }
    }
}

@Composable
private fun EscolhaDeDia(
    rotulo: String,
    ativo: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Canto.caixa))
            .background(if (ativo) Doce.destaque.copy(alpha = 0.20f) else Doce.fundoCampo)
            .border(
                1.dp,
                if (ativo) Doce.destaque else Doce.traco,
                RoundedCornerShape(Canto.caixa),
            )
            .clickable(onClick = aoTocar)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rotulo,
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
            color = if (ativo) Doce.destaqueEscuro else Doce.tintaSuave,
        )
    }
}

@Composable
private fun AmostraDoBloco(tom: Color, ativa: Boolean, aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (ativa) 32.dp else 28.dp)
            .clip(CircleShape)
            .background(tom)
            .border(
                width = if (ativa) 2.dp else 1.dp,
                color = if (ativa) Doce.destaqueEscuro else Doce.traco,
                shape = CircleShape,
            )
            .clickable(onClick = aoTocar),
    )
}
