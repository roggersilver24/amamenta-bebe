package br.com.amamentabebe.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

data class ImportResult(val feedingsAdded: Int, val diapersAdded: Int)

/** Versioned local backup. IDs belong to a device; merge by complete record content. */
class BackupManager(private val database: AppDatabase, private val settings: SettingsStore) {
    suspend fun export(): String {
        val prefs = settings.current()
        return database.withTransaction {
            JSONObject().put("format", "amamenta-bebe").put("version", 1)
                .put("feedings", JSONArray().apply { database.feedingDao().all().forEach { f ->
                    put(JSONObject().put("timeMillis", f.timeMillis).put("type", f.type.name)
                        .put("side", f.side.name).put("amountMl", f.amountMl ?: JSONObject.NULL)
                        .put("note", f.note).put("leftDurationMillis", f.leftDurationMillis)
                        .put("rightDurationMillis", f.rightDurationMillis))
                } })
                .put("diapers", JSONArray().apply { database.diaperDao().all().forEach { d ->
                    put(JSONObject().put("timeMillis", d.timeMillis).put("type", d.type.name))
                } })
                .put("settings", JSONObject().put("intervalMinutes", prefs.intervalMinutes)
                    .put("sound", prefs.sound).put("vibration", prefs.vibration).put("theme", prefs.theme.name)
                    .put("nightMode", prefs.nightMode.name))
                .toString(2)
        }
    }

    suspend fun import(json: String): ImportResult {
        require(json.length <= 10_000_000) { "Arquivo de backup muito grande." }
        val root = JSONObject(json)
        require(root.get("format") is String && root.get("version") is Number)
        require(root.getString("format") == "amamenta-bebe" && root.getInt("version") == 1) { "Backup incompatível." }
        val savedSettings = root.optJSONObject("settings")?.let { value ->
            require(value.get("sound") is Boolean && value.get("vibration") is Boolean && value.get("theme") is String)
            checkedLong(value, "intervalMinutes", 1, 1440)
            AppSettings(intervalMinutes = value.getInt("intervalMinutes"), sound = value.getBoolean("sound"),
                vibration = value.getBoolean("vibration"), theme = ThemeMode.valueOf(value.getString("theme")),
                nightMode = NightMode.valueOf(value.optString("nightMode", "OFF"))).also {
                require(it.intervalMinutes in 1..1440) { "Intervalo inválido no backup." }
            }
        }
        val feedingArray = root.getJSONArray("feedings")
        val diaperArray = root.getJSONArray("diapers")
        require(feedingArray.length() + diaperArray.length() <= 100_000) { "Backup excede o limite de registros." }
        // Validate every record before touching the database. Invalid files never partially restore.
        val feedings = List(feedingArray.length()) { index ->
            val value = feedingArray.getJSONObject(index)
            require(value.get("type") is String && value.get("side") is String && value.get("note") is String)
            if (!value.isNull("amountMl")) checkedLong(value, "amountMl", 0, 100_000)
            Feeding(timeMillis = checkedLong(value, "timeMillis", 1, 9_000_000_000_000_000), type = FeedingType.valueOf(value.getString("type")),
                side = BreastSide.valueOf(value.getString("side")),
                amountMl = if (value.isNull("amountMl")) null else value.getInt("amountMl"),
                note = value.getString("note"), leftDurationMillis = if (value.has("leftDurationMillis")) checkedLong(value, "leftDurationMillis", 0, 9_000_000_000_000_000) else 0,
                rightDurationMillis = if (value.has("rightDurationMillis")) checkedLong(value, "rightDurationMillis", 0, 9_000_000_000_000_000) else 0).also {
                require(it.timeMillis > 0 && it.leftDurationMillis >= 0 && it.rightDurationMillis >= 0)
                require(it.amountMl == null || it.amountMl in 0..100_000)
                require(it.note.length <= 10_000_000)
            }
        }
        val diapers = List(diaperArray.length()) { index ->
            val value = diaperArray.getJSONObject(index)
            require(value.get("type") is String)
            Diaper(timeMillis = checkedLong(value, "timeMillis", 1, 9_000_000_000_000_000), type = DiaperType.valueOf(value.getString("type")))
                .also { require(it.timeMillis > 0) }
        }
        val result = database.withTransaction {
            val knownFeedings = database.feedingDao().all().map { it.copy(id = 0) }.toMutableSet()
            val knownDiapers = database.diaperDao().all().map { it.copy(id = 0) }.toMutableSet()
            var feedingCount = 0
            var diaperCount = 0
            feedings.forEach { if (knownFeedings.add(it)) { database.feedingDao().insert(it); feedingCount++ } }
            diapers.forEach { if (knownDiapers.add(it)) { database.diaperDao().insert(it); diaperCount++ } }
            ImportResult(feedingCount, diaperCount)
        }
        // Alarm timestamps and an active timer are device state, never replaced by an old file.
        savedSettings?.let { settings.restoreSettings(it) }
        return result
    }
    private fun checkedLong(value: JSONObject, key: String, min: Long, max: Long): Long {
        val raw = value.get(key)
        require(raw is Number) { "Campo numérico inválido: $key" }
        val number = raw.toLong()
        require(raw.toDouble().isFinite() && raw.toDouble() == number.toDouble() && number in min..max) { "Valor inválido: $key" }
        return number
    }
}
