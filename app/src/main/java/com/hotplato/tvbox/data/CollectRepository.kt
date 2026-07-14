package com.hotplato.tvbox.data

import com.hotplato.tvbox.cache.RoomDataManger
import com.hotplato.tvbox.cache.VodCollect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CollectRepository {
    suspend fun load(): List<VodCollect> = withContext(Dispatchers.IO) {
        RoomDataManger.getAllVodCollect()
    }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) {
        RoomDataManger.deleteVodCollect(id)
    }
}
