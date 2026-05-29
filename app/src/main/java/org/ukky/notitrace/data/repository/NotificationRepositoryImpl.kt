package org.ukky.notitrace.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.ukky.notitrace.data.db.dao.NotificationDao
import org.ukky.notitrace.data.db.dao.NotificationRawLogDao
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationRawLogEntity
import org.ukky.notitrace.data.db.entity.ReceivedNotificationWithTag
import org.ukky.notitrace.data.db.entity.NotificationWithTag
import org.ukky.notitrace.data.db.entity.RawLogWithTag
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: NotificationDao,
    private val rawLogDao: NotificationRawLogDao,
) : NotificationRepository {

    override fun getAllWithTag(): Flow<List<ReceivedNotificationWithTag>> =
        dao.getAllReceivedWithTag()

    override fun getByTag(tag: String): Flow<List<ReceivedNotificationWithTag>> =
        dao.getReceivedByTag(tag)

    override fun search(query: String): Flow<List<ReceivedNotificationWithTag>> {
        val pattern = query.toLikePattern()
        return dao.searchReceivedFts(query)
            .catch { emit(emptyList()) }
            .flatMapLatest { ftsResults ->
                if (ftsResults.isNotEmpty()) {
                    flowOf(ftsResults)
                } else {
                    dao.searchReceivedPartial(pattern)
                }
            }
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
}
