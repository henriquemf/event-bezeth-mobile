package com.beazeth.notifier.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * O acesso ao banco do aparelho.
 *
 * Toda leitura de tela devolve `Flow`: a interface se inscreve uma vez e o Room
 * a reavisa sozinho quando a linha muda -- venha a mudanca do dedo da pessoa ou
 * da sincronizacao chegando por baixo. Sem isso, cada tela precisaria saber
 * quando recarregar, e alguma esqueceria.
 *
 * `@Upsert` e nao `@Insert`+`@Update` porque a sincronizacao nao sabe (nem
 * precisa saber) se a linha que chegou e nova ou editada: nos dois casos o
 * certo e gravar por cima pelo id.
 */

@Dao
interface NotaDao {
    @Query("SELECT * FROM notas ORDER BY z ASC, id ASC")
    fun observar(): Flow<List<NotaEntity>>

    @Query("SELECT * FROM notas WHERE bucket = :bucket ORDER BY z ASC, id ASC")
    fun observarDoQuadro(bucket: String): Flow<List<NotaEntity>>

    @Upsert
    suspend fun gravar(notas: List<NotaEntity>)

    @Upsert
    suspend fun gravar(nota: NotaEntity)

    @Query("SELECT * FROM notas WHERE id = :id")
    suspend fun buscar(id: Long): NotaEntity?

    @Query("DELETE FROM notas WHERE id = :id")
    suspend fun apagar(id: Long)

    @Query("SELECT COALESCE(MIN(id), 0) FROM notas")
    suspend fun menorId(): Long

    /**
     * As linhas que so existem neste aparelho.
     *
     * Id negativo quer dizer "o servidor nunca ouviu falar disto". No uso com
     * conta e um estado de segundos, entre criar e sincronizar; no modo local e
     * o estado permanente de tudo. E esta consulta que permite adotar o que foi
     * escrito sem conta quando alguem finalmente entra numa.
     */
    @Query("SELECT * FROM notas WHERE id < 0 ORDER BY id DESC")
    suspend fun provisorios(): List<NotaEntity>
}

@Dao
interface TarefaDao {
    @Query("SELECT * FROM tarefas WHERE day BETWEEN :inicio AND :fim ORDER BY day ASC, position ASC, id ASC")
    fun observarIntervalo(inicio: String, fim: String): Flow<List<TarefaEntity>>

    @Upsert
    suspend fun gravar(itens: List<TarefaEntity>)

    @Upsert
    suspend fun gravar(item: TarefaEntity)

    @Query("SELECT * FROM tarefas WHERE id = :id")
    suspend fun buscar(id: Long): TarefaEntity?

    @Query("DELETE FROM tarefas WHERE id = :id")
    suspend fun apagar(id: Long)

    @Query("SELECT COUNT(*) FROM tarefas WHERE day = :dia")
    suspend fun quantasNoDia(dia: String): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM tarefas WHERE day = :dia")
    suspend fun ultimaPosicao(dia: String): Int

    @Query("SELECT COALESCE(MIN(id), 0) FROM tarefas")
    suspend fun menorId(): Long

    /** Ver [NotaDao.provisorios]. */
    @Query("SELECT * FROM tarefas WHERE id < 0 ORDER BY id DESC")
    suspend fun provisorios(): List<TarefaEntity>
}

@Dao
interface BlocoDao {
    @Query("SELECT * FROM blocos ORDER BY dayOfWeek ASC, startMinute ASC")
    fun observar(): Flow<List<BlocoEntity>>

    @Upsert
    suspend fun gravar(blocos: List<BlocoEntity>)

    @Upsert
    suspend fun gravar(bloco: BlocoEntity)

    @Query("DELETE FROM blocos WHERE id = :id")
    suspend fun apagar(id: Long)

    @Query("SELECT COALESCE(MIN(id), 0) FROM blocos")
    suspend fun menorId(): Long

    /** Ver [NotaDao.provisorios]. */
    @Query("SELECT * FROM blocos WHERE id < 0 ORDER BY id DESC")
    suspend fun provisorios(): List<BlocoEntity>
}

@Dao
interface EventoDao {
    @Query("SELECT * FROM eventos ORDER BY eventDatetime ASC")
    fun observar(): Flow<List<EventoEntity>>

    @Upsert
    suspend fun gravar(eventos: List<EventoEntity>)

    @Query("DELETE FROM eventos WHERE id = :id")
    suspend fun apagar(id: Long)

    @Query("SELECT COALESCE(MIN(id), 0) FROM eventos")
    suspend fun menorId(): Long

    /** Ver [NotaDao.provisorios]. */
    @Query("SELECT * FROM eventos WHERE id < 0 ORDER BY id DESC")
    suspend fun provisorios(): List<EventoEntity>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY label ASC")
    fun observar(): Flow<List<TagEntity>>

    @Upsert
    suspend fun gravar(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE slug = :slug")
    suspend fun buscar(slug: String): TagEntity?

    @Query("DELETE FROM tags WHERE slug = :slug")
    suspend fun apagar(slug: String)
}

@Dao
interface AguaDao {
    /**
     * O dia mais recente que o servidor mandou.
     *
     * A tela de agua usa ESTE, e nao a data do aparelho. O dia do consumo e o
     * dia LOCAL DO SERVIDOR -- esta escrito assim em `app/db/hydration.py`, e
     * de proposito, para bater com a janela dos lembretes. Um celular num fuso
     * a frente perguntaria por uma data que o servidor ainda nao tem e veria
     * zero copo depois de beber tres. Foi exatamente o que aconteceu no
     * emulador, que roda em GMT enquanto o servidor esta em GMT-3.
     */
    @Query("SELECT * FROM agua_dias ORDER BY day DESC LIMIT 1")
    fun observarDiaMaisRecente(): Flow<AguaDiaEntity?>

    @Upsert
    suspend fun gravar(dias: List<AguaDiaEntity>)

    @Upsert
    suspend fun gravar(dia: AguaDiaEntity)

    @Query("SELECT * FROM agua_dias WHERE day = :dia")
    suspend fun buscar(dia: String): AguaDiaEntity?

    @Query("SELECT * FROM agua_config WHERE id = 1")
    fun observarConfig(): Flow<ConfigAguaEntity?>

    @Upsert
    suspend fun gravarConfig(config: ConfigAguaEntity)
}

@Dao
interface PendenciaDao {
    @Query("SELECT * FROM pendencias ORDER BY criadoEm ASC, id ASC")
    suspend fun todas(): List<PendenciaEntity>

    /**
     * A proxima da fila -- uma so, lida do banco na hora.
     *
     * A drenagem tem de pedir UMA por vez, e nao percorrer a lista de [todas].
     * Uma lista e uma fotografia: [trocarId] reescreve os caminhos no banco,
     * mas os objetos que ja estavam na mao continuam com o caminho velho. Era
     * exatamente isso que fazia o texto de um post-it recem-criado subir para
     * `/api/notes/-1` -- 404, pendencia descartada, texto perdido.
     */
    @Query("SELECT * FROM pendencias ORDER BY criadoEm ASC, id ASC LIMIT 1")
    suspend fun primeira(): PendenciaEntity?

    @Query("SELECT COUNT(*) FROM pendencias")
    fun quantas(): Flow<Int>

    @Insert
    suspend fun enfileirar(p: PendenciaEntity): Long

    @Delete
    suspend fun remover(p: PendenciaEntity)

    /**
     * Reescreve as pendencias que ainda apontam para um id provisorio.
     *
     * Cenario real: criar uma tarefa offline e ja marca-la como feita. A
     * segunda pendencia fala de `/api/todo/-3`, que nao existe no servidor.
     * Quando a primeira sobe e devolve o id 812, esta linha troca o `-3` por
     * `812` em tudo que ficou na fila.
     *
     * `entidade` no filtro nao e zelo: cada tabela conta os provisorios por
     * conta propria, entao existe um post-it -1 E uma tarefa -1 ao mesmo tempo.
     * Sem o filtro, o id que o servidor deu ao post-it seria carimbado tambem
     * no caminho da tarefa, que passaria a apontar para uma linha alheia.
     */
    @Query(
        "UPDATE pendencias SET caminho = REPLACE(caminho, :provisorio, :definitivo) " +
            "WHERE entidade = :entidade AND caminho LIKE '%' || :provisorio"
    )
    suspend fun trocarId(entidade: String, provisorio: String, definitivo: String)
}
