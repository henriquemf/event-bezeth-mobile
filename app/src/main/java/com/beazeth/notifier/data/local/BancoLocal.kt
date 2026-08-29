package com.beazeth.notifier.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * O banco do aparelho.
 *
 * Nao ha migracao declarada ainda porque nao ha versao anterior instalada em
 * lugar nenhum. Na primeira mudanca de esquema depois do app estar no celular
 * de alguem, entra uma `Migration` de verdade -- e nao
 * `fallbackToDestructiveMigration`, que apagaria o que ainda nao subiu.
 *
 * Por isso o esquema e exportado em JSON (ver `ksp` no build): a migracao
 * futura vai precisar saber exatamente como a tabela era.
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
    ],
    version = 1,
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

    companion object {
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
                ).build().also { instancia = it }
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
