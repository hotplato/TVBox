package com.hotplato.tvbox.data

import com.hotplato.tvbox.cache.RoomDataManger
import com.hotplato.tvbox.cache.VodCollect
import com.hotplato.tvbox.bean.VodInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CollectRepository {
    suspend fun load(): List<VodCollect> = withContext(Dispatchers.IO) {
        RoomDataManger.getAllVodCollect()
    }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) {
        RoomDataManger.deleteVodCollect(id)
    }

    suspend fun contains(sourceKey: String, vodId: String): Boolean = withContext(Dispatchers.IO) {
        RoomDataManger.getAllVodCollect().any { it.sourceKey == sourceKey && it.vodId == vodId }
    }

    suspend fun remove(sourceKey: String, vodId: String) = withContext(Dispatchers.IO) {
        RoomDataManger.getAllVodCollect()
            .firstOrNull { it.sourceKey == sourceKey && it.vodId == vodId }
            ?.let { RoomDataManger.deleteVodCollect(it.id) }
    }

    suspend fun save(sourceKey: String, item: VodInfo) = withContext(Dispatchers.IO) {
        RoomDataManger.insertVodCollect(sourceKey, item)
    }
}
