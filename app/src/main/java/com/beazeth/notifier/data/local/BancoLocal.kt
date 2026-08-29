package com.beazeth.notifier.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * O banco do aparelho.
 *
 * O esquema e exportado em JSON (ver `ksp` no build), e e de la que sai o SQL
 * exato de cada migracao: o Room compara o banco aberto com o esquema esperado
 * e RECUSA abrir se faltar uma virgula. Nada de
 * `fallbackToDestructiveMigration`, que resolveria apagando -- e apagaria junto
 * o que ainda nao subiu para o servidor.
 */
@Database(
    entities = [
        NotaEntity::class,
        TarefaEntity::class,
        BlocoEntity::class,
        EventoEntity::class,
        TagEntity::class,
        AguaDiaEntity::class,
        ConfigAguaEntity::class,
        PendenciaEntity::class,
        PomodoroEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BancoLocal : RoomDatabase() {

    abstract fun notas(): NotaDao
    abstract fun tarefas(): TarefaDao
    abstract fun blocos(): BlocoDao
    abstract fun eventos(): EventoDao
    abstract fun tags(): TagDao
    abstract fun agua(): AguaDao
    abstract fun pendencias(): PendenciaDao
    abstract fun pomodoros(): PomodoroDao

    companion object {
        /**
         * A tabela do historico de pomodoro, que a versao 1 nao tinha.
         *
         * Acrescenta, e so. Nenhuma tabela existente e tocada, entao nao ha o
         * que dar errado com o que ja esta no aparelho -- inclusive o que
         * estiver na fila esperando rede.
         *
         * O SQL e copiado do esquema exportado (`app/schemas/.../2.json`) letra
         * por letra, crases inclusive: o Room valida a tabela criada aqui
         * contra a que ele esperava e lanca se houver diferenca. Escrever "de
         * cabeca" e como esta migracao quebra.
         */
        private val DE_1_PARA_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pomodoros` " +
                        "(`terminadoEm` INTEGER NOT NULL, `minutos` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`terminadoEm`))"
                )
            }
        }

        @Volatile
        private var instancia: BancoLocal? = null

        /**
         * Uma instancia por processo.
         *
         * O Room mantem um pool de conexoes por instancia; abrir duas sobre o
         * mesmo arquivo daria bloqueio de escrita entre elas. O `synchronized`
         * cobre a corrida entre a interface e o worker de sincronizacao, que
         * podem pedir o banco no mesmo instante ao abrir o app.
         */
        fun obter(context: Context): BancoLocal =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    BancoLocal::class.java,
                    "beazeth.db",
                ).addMigrations(DE_1_PARA_2).build().also { instancia = it }
            }

        /**
         * Chamado ao sair da conta: o proximo login nao pode ver os dados do
         * anterior.
         *
         * **`Dispatchers.IO` nao e zelo, e obrigatorio.** `clearAllTables` e uma
         * das poucas chamadas do Room que verificam em qual thread estao e
         * LANCAM se for a da interface. Quem chama isto e o `viewModelScope`,
         * que roda em `Main.immediate` -- entao, sem esta troca, tocar em "Sair"
         * derrubava o app com `IllegalStateException`, toda vez. Passou muito
         * tempo sem ninguem ver porque nos testes o caminho usado para zerar o
         * aparelho era o `pm clear` do adb, que nao passa por aqui.
         *
         * O resto do app nao precisa disto porque os DAOs sao `suspend`: o Room
         * gera o salto de thread sozinho. `clearAllTables` nao e de DAO nenhum.
         */
        suspend fun limpar(context: Context) = withContext(Dispatchers.IO) {
            obter(context).clearAllTables()
        }
    }
}
