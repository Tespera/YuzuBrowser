/*
 * Copyright (C) 2017 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.speeddial

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import jp.hazuki.yuzubrowser.utils.image.Gochiusearch
import java.util.*

class SpeedDialManager(context: Context) {

    private val mOpenHelper = MyOpenHelper(context)

    val all: ArrayList<SpeedDial>
        @Synchronized get() {
            val db = mOpenHelper.readableDatabase
            db.query(TABLE_NAME, null, null, null, null, null, COLUMN_ORDER + " asc").use { c ->
                val list = ArrayList<SpeedDial>()
                while (c.moveToNext()) {
                    list.add(SpeedDial(c.getInt(COLUMN_ID_INDEX),
                            c.getString(COLUMN_URL_INDEX),
                            c.getString(COLUMN_TITLE_INDEX),
                            WebIcon(c.getBlob(COLUMN_ICON_INDEX)),
                            c.getInt(COLUMN_FAVICON_INDEX) == 1))
                }
                return list
            }
        }

    private val nowId: Int
        @Synchronized get() {
            val db = mOpenHelper.readableDatabase
            var id = -1
            val c = db.rawQuery("SELECT max($COLUMN_ID) FROM $TABLE_NAME", null)
            if (c.moveToFirst()) {
                id = c.getInt(0)
            }
            c.close()
            return id
        }

    val listUpdateTime: Long
        get() {
            val db = mOpenHelper.readableDatabase
            db.query(INFO_TABLE_NAME, null, INFO_COLUMN_NAME + " = ?", arrayOf(TABLE_NAME), null, null, null, "1").use { c ->
                var time: Long = -1
                if (c.moveToFirst())
                    time = c.getLong(c.getColumnIndex(INFO_COLUMN_LAST_TIME))
                return time
            }
        }

    fun update(speedDial: SpeedDial) {
        if (speedDial.id >= 0) {
            updateInternal(speedDial)
        } else {
            addInternal(speedDial)
        }
        updateListTime()
    }

    @Synchronized
    private fun addInternal(speedDial: SpeedDial) {
        if (!checkUrl(speedDial.url)) return

        val db = mOpenHelper.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_URL, speedDial.url)
            put(COLUMN_TITLE, speedDial.title)
            put(COLUMN_ORDER, nowId + 1)
            put(COLUMN_ICON, speedDial.icon!!.iconBytes)
            put(COLUMN_FAVICON, speedDial.isFavicon)
            put(COLUMN_LAST_UPDATE, 0)
        }
        val id = db.insert(TABLE_NAME, null, values)
        speedDial.id = id.toInt()
    }

    @Synchronized
    private fun updateInternal(speedDial: SpeedDial) {
        val db = mOpenHelper.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_URL, speedDial.url)
            put(COLUMN_TITLE, speedDial.title)
            put(COLUMN_ICON, speedDial.icon!!.iconBytes)
            put(COLUMN_FAVICON, speedDial.isFavicon)
            put(COLUMN_LAST_UPDATE, 0)
        }
        db.update(TABLE_NAME, values, COLUMN_ID + " = ?", arrayOf(Integer.toString(speedDial.id)))
    }

    @Synchronized
    fun update(url: String, icon: Bitmap) {
        val db = mOpenHelper.writableDatabase
        val time = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        var updated = false

        db.query(TABLE_NAME, null, COLUMN_FAVICON + " = 1 AND " + COLUMN_URL + " = ? AND " +
                COLUMN_LAST_UPDATE + " <= ?", arrayOf(url, java.lang.Long.toString(time)), null, null, null).use { c ->
            if (c.moveToFirst()) {
                val hash = Gochiusearch.getVectorHash(icon)
                do {
                    if (c.getLong(COLUMN_FAVICON_HASH_INDEX) != hash) {
                        val values = ContentValues().apply {
                            put(COLUMN_ICON, WebIcon.createIcon(icon).iconBytes)
                            put(COLUMN_LAST_UPDATE, System.currentTimeMillis())
                        }
                        db.update(TABLE_NAME, values, COLUMN_ID + " = ?", arrayOf(Integer.toString(c.getInt(COLUMN_ID_INDEX))))
                        updated = true
                    }
                } while (c.moveToNext())
            }
        }

        if (updated)
            updateListTime()
    }


    @Synchronized
    operator fun get(id: Int): SpeedDial? {
        val db = mOpenHelper.readableDatabase
        db.query(TABLE_NAME, null, COLUMN_ID + " = ?", arrayOf(java.lang.Long.toString(id.toLong())), null, null, null).use { c ->
            if (c.moveToFirst()) {
                return SpeedDial(c.getInt(COLUMN_ID_INDEX),
                        c.getString(COLUMN_URL_INDEX),
                        c.getString(COLUMN_TITLE_INDEX),
                        WebIcon(c.getBlob(COLUMN_ICON_INDEX)),
                        c.getInt(COLUMN_FAVICON_INDEX) == 1)
            }
            return null
        }
    }

    @Synchronized
    fun updateOrder(speedDials: List<SpeedDial>) {
        val db = mOpenHelper.writableDatabase
        db.beginTransaction()
        for (i in 0 until speedDials.size) {
            val data = speedDials[i]
            val values = ContentValues().apply { put(COLUMN_ORDER, i) }
            db.update(TABLE_NAME, values, COLUMN_ID + "=" + data.id, null)
        }
        db.setTransactionSuccessful()
        db.endTransaction()
        updateListTime()
    }


    @Synchronized
    fun delete(id: Int) {
        val db = mOpenHelper.writableDatabase
        db.delete(TABLE_NAME, COLUMN_ID + " = ?", arrayOf(Integer.toString(id)))
        updateListTime()
    }

    private fun updateListTime() {
        val db = mOpenHelper.writableDatabase
        val values = ContentValues().apply { put(INFO_COLUMN_LAST_TIME, System.currentTimeMillis()) }
        db.update(INFO_TABLE_NAME, values, INFO_COLUMN_NAME + " = ?", arrayOf(TABLE_NAME))
    }

    private fun checkUrl(url: String?): Boolean {
        return !url.isNullOrEmpty() && !url!!.regionMatches(0, "about:", 0, 6, ignoreCase = true)
    }

    private class MyOpenHelper private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE " + INFO_TABLE_NAME + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY" +
                        ", " + INFO_COLUMN_NAME + " TEXT NOT NULL" +
                        ", " + INFO_COLUMN_LAST_TIME + " INTEGER DEFAULT 0" +
                        ")")
                db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY" +
                        ", " + COLUMN_URL + " TEXT NOT NULL" +
                        ", " + COLUMN_TITLE + " TEXT" +
                        ", " + COLUMN_ORDER + " INTEGER" +
                        ", " + COLUMN_ICON + " BLOB" +
                        ", " + COLUMN_FAVICON + " INTEGER DEFAULT 0" +
                        ", " + COLUMN_LAST_UPDATE + " INTEGER DEFAULT 0" +
                        ", " + COLUMN_FAVICON_HASH + " INTEGER DEFAULT 0" +
                        ")")

                val values = ContentValues().apply {
                    put(INFO_COLUMN_LAST_TIME, System.currentTimeMillis())
                    put(INFO_COLUMN_NAME, TABLE_NAME)
                }
                db.insert(INFO_TABLE_NAME, null, values)

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            when (oldVersion) {
                1 -> {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_LAST_UPDATE INTEGER DEFAULT 0")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_FAVICON_HASH INTEGER DEFAULT 0")
                    db.execSQL("CREATE TABLE " + INFO_TABLE_NAME + " (" +
                            COLUMN_ID + " INTEGER PRIMARY KEY" +
                            ", " + INFO_COLUMN_NAME + " TEXT NOT NULL" +
                            ", " + INFO_COLUMN_LAST_TIME + " INTEGER DEFAULT 0" +
                            ")")

                    val values = ContentValues()
                    values.put(INFO_COLUMN_LAST_TIME, System.currentTimeMillis())
                    values.put(INFO_COLUMN_NAME, TABLE_NAME)
                    db.insert(INFO_TABLE_NAME, null, values)
                }
                2 -> {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_FAVICON_HASH INTEGER DEFAULT 0")
                    db.execSQL("CREATE TABLE $INFO_TABLE_NAME ($COLUMN_ID INTEGER PRIMARY KEY, $INFO_COLUMN_NAME TEXT NOT NULL, $INFO_COLUMN_LAST_TIME INTEGER DEFAULT 0)")
                    val values = ContentValues()
                    values.put(INFO_COLUMN_LAST_TIME, System.currentTimeMillis())
                    values.put(INFO_COLUMN_NAME, TABLE_NAME)
                    db.insert(INFO_TABLE_NAME, null, values)
                }
                else -> {
                    db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
                    db.execSQL("DROP TABLE IF EXISTS " + INFO_TABLE_NAME)
                    onCreate(db)
                }
            }
        }

        companion object {
            private var instance: MyOpenHelper? = null

            operator fun invoke(context: Context): MyOpenHelper {
                if (instance == null) {
                    instance = MyOpenHelper(context)
                }
                return instance!!
            }
        }
    }

    companion object {
        const val DB_NAME = "speeddial1.db"
        private const val DB_VERSION = 3
        private const val TABLE_NAME = "main_table"

        private const val COLUMN_ID = "_id"
        private const val COLUMN_URL = "url"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_ORDER = "item_order"
        private const val COLUMN_ICON = "icon"
        private const val COLUMN_FAVICON = "favicon"
        private const val COLUMN_LAST_UPDATE = "last_update"
        private const val COLUMN_FAVICON_HASH = "favicon_hash"

        private const val INFO_TABLE_NAME = "info"

        private const val INFO_COLUMN_NAME = "name"
        private const val INFO_COLUMN_LAST_TIME = "time"

        private const val COLUMN_ID_INDEX = 0
        private const val COLUMN_URL_INDEX = 1
        private const val COLUMN_TITLE_INDEX = 2
        private const val COLUMN_ORDER_INDEX = 3
        private const val COLUMN_ICON_INDEX = 4
        private const val COLUMN_FAVICON_INDEX = 5
        private const val COLUMN_LAST_UPDATE_INDEX = 6
        private const val COLUMN_FAVICON_HASH_INDEX = 7
    }
}
