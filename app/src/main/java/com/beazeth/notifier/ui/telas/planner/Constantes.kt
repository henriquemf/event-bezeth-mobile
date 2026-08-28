package com.beazeth.notifier.ui.telas.planner

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.beazeth.notifier.ui.telas.corDoHex
import com.beazeth.notifier.ui.theme.Doce
import java.time.LocalDate

/**
 * Os numeros e nomes que o planner divide com o servidor e com o site.
 *
 * Mesmo motivo de `js/pages/planner/constants.js` existir la: sao os valores
 * que TEM de bater dos dois lados, e ficam onde da para conferir sem rolar por
 * trezentas linhas de interface.
 */

/** Espelha `DAY_LABELS`. Segunda e o indice 0, aqui e no servidor. */
internal val DIAS = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

/** Espelha `DAY_SHORT`. O cabecalho da coluna usa isto; o formulario usa o de cima. */
internal val DIAS_CURTOS = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")

/** Espelha `SNAP_MINUTES`, e o mesmo arredondamento de `parse_block_payload`. */
internal const val GRADE = 15

internal const val MINUTOS_DO_DIA = 24 * 60

/** As seis cores dos `planner-swatch` de `pages/planner.html`, na ordem. */
internal val CORES_DO_BLOCO = listOf("rose", "blue", "mint", "lavender", "peach", "sun")

/** `FULL_RANGE` e `WORK_RANGE`. O botao "Horário útil" alterna entre as duas. */
internal val FAIXA_COMPLETA = 0..MINUTOS_DO_DIA
internal val FAIXA_UTIL = 360..1380

/**
 * `ZOOM` do site, em dp de altura por hora.
 *
 * La e um `<input type="range">`; aqui sao dois botoes. Um controle deslizante
 * de 2 px de passo pede precisao que o dedo nao tem, e o unico uso real do zoom
 * e "quero ver mais horas" ou "quero ler os titulos" -- duas direcoes, dois
 * botoes.
 */
internal val ZOOM_MINIMO = 28.dp
internal val ZOOM_MAXIMO = 110.dp
internal val ZOOM_PADRAO = 52.dp
internal val ZOOM_PASSO = 12.dp

/** A regua de horas a esquerda, sempre visivel. */
internal val CALHA = 46.dp

/** Abaixo disto o titulo de um bloco nao cabe; e o piso do "adaptado". */
internal val COLUNA_MINIMA = 96.dp

/** Segunda = 0, como `todayIndex` do site (`(getDay() + 6) % 7`). */
internal fun diaDeHoje(): Int = LocalDate.now().dayOfWeek.value - 1

/** Encaixa na grade de 15 minutos e mantem dentro do dia. */
internal fun arredondar(minutos: Int): Int =
    ((minutos + GRADE / 2) / GRADE * GRADE).coerceIn(0, MINUTOS_DO_DIA)

internal fun comoHora(minutos: Int): String =
    "%02d:%02d".format(minutos / 60 % 24, minutos % 60)

/**
 * O planner guarda a cor por NOME (`rose`, `blue`...), e nao por hex como as
 * tags -- ver `payload.get("color") or "rose"` em `blueprints/planner.py`. Um
 * hex tambem e aceito, entao a conversao tenta o nome primeiro e cai no
 * interpretador de hex depois.
 */
@Composable
internal fun corDoPlanner(nome: String): Color = when (nome.lowercase()) {
    "rose" -> Doce.destaque
    "blue" -> Doce.azulSuave
    "mint" -> Color(0xFF7BD8A8)
    "peach" -> Color(0xFFFFB38A)
    "lavender" -> Color(0xFFB9A0FF)
    // `sun` esta entre os `planner-swatch` do site e faltava aqui: um bloco
    // amarelo criado no computador caia no interpretador de hex e chegava
    // cinza no celular.
    "sun" -> Color(0xFFFFD75E)
    else -> corDoHex(nome)
}
