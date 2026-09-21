package com.beazeth.notifier.ui.telas.diario

import androidx.compose.ui.graphics.Color

/**
 * Os humores do diario, e a cor de cada um.
 *
 * As chaves espelham `MOODS`, em `app/db/diary.py`: sao elas que viajam no
 * `PUT /api/diary/<dia>` e voltam na sincronizacao. Mudar uma aqui sem mudar la
 * faz o servidor devolver vazio -- ele recusa slug que nao conhece.
 *
 * **A cor nao vem do servidor, e isso e deliberado.** O servidor diz quais
 * humores existem; a tinta e desenho, e desenho e de quem desenha. Sao os seis
 * tons de papel dos post-its do site (`pages/notes.css`), os mesmos que o site
 * usa nesta tela -- e como sao pasteis, funcionam por cima do fundo claro e do
 * escuro sem precisar de duas paletas.
 */
internal enum class Humor(val chave: String, val rotulo: String, val cor: Color) {
    FELIZ("feliz", "Feliz", Color(0xFFFF9EC4)),
    ANIMADA("animada", "Animada", Color(0xFFFFE066)),
    CALMA("calma", "Calma", Color(0xFF7FE0C0)),
    CANSADA("cansada", "Cansada", Color(0xFFC0A8FB)),
    TRISTE("triste", "Triste", Color(0xFF8FCDFF)),
    BRAVA("brava", "Brava", Color(0xFFFFB08A));

    companion object {
        /** `null` e um estado legitimo: anotacao sem humor escolhido. */
        fun porChave(chave: String?): Humor? = entries.firstOrNull { it.chave == chave }
    }
}
