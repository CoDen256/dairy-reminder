package coden.dairy.reminder.notion

import coden.dairy.reminder.model.DairyEntry
import coden.dairy.reminder.model.DairyRepository
import coden.dairy.reminder.notion.NotionDairyTableUtility.Companion.asRichText
import notion.api.v1.NotionClient
import notion.api.v1.model.databases.DatabaseProperty
import notion.api.v1.model.databases.DatabasePropertySchema
import notion.api.v1.model.databases.RichTextPropertySchema
import notion.api.v1.model.databases.TitlePropertySchema
import notion.api.v1.model.databases.query.filter.QueryTopLevelFilter
import notion.api.v1.model.pages.Page
import notion.api.v1.model.pages.PageParent
import notion.api.v1.model.pages.PageProperty
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val SCHEMA: Map<String, DatabasePropertySchema> = mapOf(
    "Month" to TitlePropertySchema(),
    "Description" to RichTextPropertySchema()
)

class NotionDairyTable(
    private val client: NotionClient,
    val id: String
) : DairyRepository {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM'/'yyyy")


    private val db = client.retrieveDatabase(id)

    init {
        verifySchema(db.properties, SCHEMA)
    }


    private fun verifySchema(
        properties: Map<String, DatabaseProperty>,
        target: Map<String, DatabasePropertySchema>
    ): Boolean {
        if (target.size != properties.size) throw IllegalStateException("${db.title} does not match schema of DairyRepository: expected <${target.size}> elements, but was <${properties.size}>")
        for ((k, v) in target) {
            val property = properties.getOrElse(k) {
                throw IllegalStateException("${db.title} does not match schema of DairyRepository: '$k' property is missing in the table")
            }
            val type = v.javaClass.simpleName.removeSuffix("PropertySchema")
            val actual = property.type.name
            if (actual != type) {
                throw IllegalStateException("${db.title} does not match schema of DairyRepository: '$k' must have <$type> type but was: <$actual>")

            }
        }
        return true
    }

    override fun entries(): Collection<DairyEntry> {
        return queryPages().mapNotNull { mapFromPage(it) }
    }

    private fun queryPages(filter: QueryTopLevelFilter? = null) =
        client.queryDatabase(id, filter).results

    override fun get(index: Int): DairyEntry {
        TODO("Not yet implemented")
    }

    override fun first(): DairyEntry {
        TODO("Not yet implemented")
    }

    override fun last(): DairyEntry {
        TODO("Not yet implemented")
    }

    override fun insert(entry: DairyEntry) {
        client.createPage(
            parent = PageParent.database(id),
            properties = mapToProperties(entry)
        )
    }

    private fun mapFromPage(page: Page): DairyEntry? {
        val descirption = getProperty(page, "Description").richText?.firstOrNull()?.plainText
        val month = getProperty(page, "Month").title?.firstOrNull()?.plainText
        if (descirption == null || month.isNullOrBlank()) return null
        val ym = YearMonth.parse(month, formatter)
        return DairyEntry(
            ym.atDay(1),
            descirption
        )
    }

    private fun getProperty(
        page: Page,
        prop: String
    ) = page.properties[prop] ?: throw IllegalArgumentException("Unknown page format: $page, missing 'Description'")

    private fun mapToProperties(entry: DairyEntry): Map<String, PageProperty> {
        return mapOf(
            "Month" to PageProperty(title = formatter.format(entry.month).asRichText()),
            "Description" to PageProperty(richText = entry.description.asRichText())
        )
    }

    override fun delete(index: Int) {
        TODO("Not yet implemented")
    }

    override fun clear() {
        queryPages().forEach {
            client.updatePage(
                pageId = it.id,
                properties = it.properties,
                archived = true
            )
        }
    }
}