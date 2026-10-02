package app.lectures.nativeapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class DataStore(private val context: Context) {
    private val file = File(context.filesDir, "lectures-data.json")
    private val _data = MutableStateFlow(read())
    val data = _data.asStateFlow()

    private fun read(): AppData = runCatching { parseAppData(file.readText()) }.getOrDefault(AppData())
    suspend fun update(transform: (AppData) -> AppData) = withContext(Dispatchers.IO) {
        val next = transform(_data.value)
        file.writeText(next.toJson().toString(2))
        _data.value = next
    }
    suspend fun replaceFromJson(json: String): AppData = withContext(Dispatchers.IO) {
        val next = parseAppData(json)
        require(JSONObject(json).has("lectures")) { "В резервной копии нет списка лекций" }
        File(context.filesDir, "backup-${System.currentTimeMillis()}.json").writeText(_data.value.toJson().toString(2))
        file.writeText(next.toJson().toString(2))
        _data.value = next
        next
    }
    fun exportJson(): String = _data.value.toJson().toString(2)
}
