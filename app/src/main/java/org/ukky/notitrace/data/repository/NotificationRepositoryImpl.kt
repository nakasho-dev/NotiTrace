package org.ukky.notitrace.data.repository

import android.database.sqlite.SQLiteException
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.ukky.notitrace.data.db.dao.NotificationDao
import org.ukky.notitrace.data.db.dao.NotificationRawLogDao
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationRawLogEntity
import org.ukky.notitrace.data.db.entity.NotificationWithTag
import org.ukky.notitrace.data.db.entity.RawLogWithTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: NotificationDao,
    private val rawLogDao: NotificationRawLogDao,
) : NotificationRepository {

    override fun getAllListItems(): Flow<PagingData<NotificationListItemModel>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = dao::getAllListItemsPaged,
        ).flow

    override fun getListItemsByTag(tag: String): Flow<PagingData<NotificationListItemModel>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { dao.getListItemsByTagPaged(tag) },
        ).flow

    override fun searchListItems(query: String): Flow<PagingData<NotificationListItemModel>> = flow {
        val pattern = query.toLikePattern()
        val pagingSourceFactory =
            if (canRunFtsQuery(query)) {
                { dao.searchListItemsPaged(query, pattern) }
            } else {
                { dao.searchListItemsPartialPaged(pattern) }
            }
        emitAll(
            Pager(
                config = PagingConfig(
                    pageSize = PAGE_SIZE,
                    initialLoadSize = PAGE_SIZE,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = pagingSourceFactory,
            ).flow
        )
    }

    override fun getById(id: Long): Flow<NotificationEntity?> =
        dao.getById(id)

    override suspend fun save(entity: NotificationEntity) {
        val newId = dao.insert(entity)
        rawLogDao.insert(
            NotificationRawLogEntity(
                notificationId = newId,
                rawJson = entity.rawJson,
                receivedAt = entity.lastReceivedAt,
            )
        )
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() {
        rawLogDao.deleteAll()
        dao.deleteAll()
    }

    override suspend fun getAllForBackup(): List<NotificationEntity> =
        dao.getAllForBackup()

    override suspend fun getForExport(tag: String?): List<NotificationWithTag> =
        if (tag == null) dao.getAllWithTagList()
        else dao.getByTagList(tag)

    override suspend fun getForRawExport(tag: String?): List<RawLogWithTag> =
        if (tag == null) rawLogDao.getAllWithTagOrderByReceivedAt()
        else rawLogDao.getByTagOrderByReceivedAt(tag)

    override suspend fun getAllRawLogsForBackup(): List<NotificationRawLogEntity> =
        rawLogDao.getAllForBackup()

    override suspend fun cleanupOldRawLogs(cutoffMillis: Long): Int =
        rawLogDao.deleteOlderThan(cutoffMillis)

    override fun getDistinctPackageNames(): Flow<List<String>> =
        dao.getDistinctPackageNames()

    private fun String.toLikePattern(): String = buildString(length + 2) {
        append('%')
        for (ch in this@toLikePattern) {
            when (ch) {
                '\\', '%', '_' -> append('\\')
            }
            append(ch)
        }
        append('%')
    }

    private suspend fun canRunFtsQuery(query: String): Boolean = try {
        dao.countSearchFts(query)
        true
    } catch (_: SQLiteException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
