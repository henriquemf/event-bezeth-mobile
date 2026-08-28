package com.beazeth.notifier.ui.telas.calendario

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.EventoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O estado da agenda.
 *
 * Criar, editar e apagar passam pelas rotas JSON `POST/PATCH/DELETE
 * /api/events`, acrescentadas ao servidor para isto -- a rota antiga (`POST
 * /events`) le formulario, responde 302 e comunica erro por `flash`, tres
 * coisas que um cliente JSON nao tem como consumir.
 */
class CalendarioViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    val eventos = repo.eventos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags = repo.tags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun criar(titulo: String, descricao: String, quando: String, tag: String) =
        viewModelScope.launch { repo.criarEvento(titulo, descricao, quando, tag) }

    fun editar(
        evento: EventoEntity,
        titulo: String,
        descricao: String,
        quando: String,
        tag: String,
    ) = viewModelScope.launch { repo.editarEvento(evento, titulo, descricao, quando, tag) }

    fun apagar(evento: EventoEntity) = viewModelScope.launch { repo.apagarEvento(evento.id) }
}
