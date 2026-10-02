package app.lectures.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Subject(val id: String = UUID.randomUUID().toString(), val name: String)
data class Attachment(val id: String = UUID.randomUUID().toString(), val name: String, val type: String, val data: String)
data class Lecture(
    val id: String = UUID.randomUUID().toString(), val title: String = "", val date: String,
    val subjectId: String? = null, val content: String = "", val attachments: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis()
)
data class Homework(
    val id: String = UUID.randomUUID().toString(), val text: String = "", val addedDate: String,
    val dueDate: String = "", val subjectId: String? = null, val done: Boolean = false
)
data class AppData(val subjects: List<Subject> = emptyList(), val lectures: List<Lecture> = emptyList(), val homeworks: List<Homework> = emptyList(), val version: Int = 5)

fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("version", 5)
    put("subjects", JSONArray().also { a -> subjects.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) } })
    put("lectures", JSONArray().also { a -> lectures.forEach { l ->
        val item = JSONObject().put("id", l.id).put("title", l.title).put("date", l.date)
            .put("subjectId", l.subjectId ?: JSONObject.NULL).put("content", l.content)
            .put("createdAt", l.createdAt).put("updatedAt", l.updatedAt)
        item.put("attachments", JSONArray().also { files -> l.attachments.forEach { f ->
            files.put(JSONObject().put("id", f.id).put("name", f.name).put("type", f.type).put("data", f.data))
        } })
        a.put(item)
    } })
    put("homeworks", JSONArray().also { a -> homeworks.forEach { h ->
        a.put(JSONObject().put("id", h.id).put("text", h.text).put("addedDate", h.addedDate)
            .put("dueDate", h.dueDate).put("subjectId", h.subjectId ?: JSONObject.NULL).put("done", h.done))
    } })
}

fun parseAppData(text: String): AppData {
    val root = JSONObject(text)
    val subjects = root.optJSONArray("subjects").toObjects { Subject(it.optString("id", UUID.randomUUID().toString()), it.optString("name")) }
    val lectures = root.optJSONArray("lectures").toObjects { item ->
        val attachments = item.optJSONArray("attachments").toObjects { file ->
            Attachment(file.optString("id", UUID.randomUUID().toString()), file.optString("name", "Файл"), file.optString("type", "application/octet-stream"), file.optString("data"))
        }.ifEmpty {
            item.optJSONArray("images").toObjectsIndexed { i, image -> Attachment(name = "image-${i + 1}.jpg", type = "image/jpeg", data = image) }
        }
        Lecture(
            id = item.optString("id", UUID.randomUUID().toString()), title = item.optString("title"),
            date = item.optString("date"), subjectId = item.optNullableString("subjectId"),
            content = item.optString("content"), attachments = attachments,
            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
        )
    }
    val homeworks = root.optJSONArray("homeworks").toObjects { item ->
        Homework(item.optString("id", UUID.randomUUID().toString()), item.optString("text"),
            item.optString("addedDate"), item.optString("dueDate"), item.optNullableString("subjectId"), item.optBoolean("done"))
    }
    return AppData(subjects, lectures, homeworks)
}

private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
private inline fun <T> JSONArray?.toObjects(factory: (JSONObject) -> T): List<T> = buildList {
    val array = this@toObjects ?: return@buildList
    for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(factory(it)) }
}
private inline fun <T> JSONArray?.toObjectsIndexed(factory: (Int, String) -> T): List<T> = buildList {
    val array = this@toObjectsIndexed ?: return@buildList
    for (i in 0 until array.length()) add(factory(i, array.optString(i)))
}
