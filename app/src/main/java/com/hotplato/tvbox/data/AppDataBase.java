package com.hotplato.tvbox.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.hotplato.tvbox.cache.Cache;
import com.hotplato.tvbox.cache.CacheDao;
import com.hotplato.tvbox.cache.VodCollect;
import com.hotplato.tvbox.cache.VodCollectDao;
import com.hotplato.tvbox.cache.VodRecord;
import com.hotplato.tvbox.cache.VodRecordDao;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
@Database(entities = {Cache.class, VodRecord.class, VodCollect.class}, version = 1)
public abstract class AppDataBase extends RoomDatabase {
    public abstract CacheDao getCacheDao();

    public abstract VodRecordDao getVodRecordDao();

    public abstract VodCollectDao getVodCollectDao();
}
