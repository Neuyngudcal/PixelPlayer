package com.theveloper.pixelplay.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.LyricsDao
import com.theveloper.pixelplay.data.database.LyricsEntity
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsModuleHandler @Inject constructor(
    private val lyricsDao: LyricsDao,
    @BackupGson private val gson: Gson,
    private val lyricsRepository: LyricsRepository? = null
) : BackupModuleHandler {

    override val section = BackupSection.LYRICS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(lyricsDao.getAll())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        lyricsDao.getAll().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String): Unit = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, LyricsEntity::class.java).type
        val lyrics: List<LyricsEntity> = gson.fromJson(payload, type)
        lyricsDao.replaceAll(lyrics)
        lyricsRepository?.clearCache()
    }

    override suspend fun rollback(snapshot: String): Unit = restore(snapshot)
}
