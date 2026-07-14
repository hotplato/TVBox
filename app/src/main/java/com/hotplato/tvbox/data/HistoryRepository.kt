package com.hotplato.tvbox.data

import com.hotplato.tvbox.bean.VodInfo
import com.hotplato.tvbox.cache.RoomDataManger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HistoryRepository {
    suspend fun load(limit: Int = 100): List<VodInfo> = withContext(Dispatchers.IO) {
        RoomDataManger.getAllVodRecord(limit)
    }

    suspend fun delete(item: VodInfo) = withContext(Dispatchers.IO) {
        RoomDataManger.deleteVodRecord(item.sourceKey, item)
    }
}
