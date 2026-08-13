package com.example.timetable.data

/** 一行 CSV 数据（index 为文件行号，从 1 开始）。 */
data class CsvRow(
    val line: Int,
    val fields: List<String>,
)

data class CsvParseResult(
    val rows: List<CsvRow>,
    val errors: List<String>,
)

/** 轻量解析器：支持逗号、空格/制表符作为分隔符，支持引号与转义，无第三方依赖。 */
fun parseCsv(text: String): CsvParseResult {
    val rows = mutableListOf<CsvRow>()
    val errors = mutableListOf<String>()
    val field = StringBuilder()
    val rowFields = mutableListOf<String>()
    var inQuotes = false
    var line = 1

    fun endField() {
        rowFields.add(field.toString())
        field.clear()
    }

    /** 空格分隔：跳过空字段（连续空格/行首空格不产生空列）。 */
    fun endSpaceField() {
        if (field.isNotEmpty()) {
            rowFields.add(field.toString())
            field.clear()
        }
    }

    fun endRow() {
        endField()
        if (rowFields.any { it.isNotBlank() }) {
            rows.add(CsvRow(line, rowFields.toList()))
        }
        rowFields.clear()
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            }
            c == '"' -> inQuotes = true
            c == ',' -> endField()
            c == ' ' || c == '\t' -> endSpaceField()
            c == '\n' || c == '\r' -> {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                endRow()
                line++
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || rowFields.isNotEmpty()) endRow()
    return CsvParseResult(rows, errors)
}
