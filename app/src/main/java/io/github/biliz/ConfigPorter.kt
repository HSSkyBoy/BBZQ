package io.github.biliz

import android.content.Context
import android.content.SharedPreferences
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ConfigPorter {
    private const val ZIP_SWITCHES_ENTRY = "switches.xml"
    private const val ZIP_MANUAL_ENTRY = "manual.xml"
    private const val ZIP_DEVICES_ENTRY = "devices.txt"

    private const val ROOT_TAG = "map"
    private const val TAG_BOOLEAN = "boolean"
    private const val TAG_INT = "int"
    private const val TAG_STRING = "string"
    private const val TAG_SET = "set"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"

    private val switchSpecsByKey = ModuleSettings.exportableSwitchSpecs.associateBy { it.key }
    private val manualSpecsByKey = ModuleSettings.exportableManualSpecs.associateBy { it.key }

    data class ExportPackage(
        val bytes: ByteArray,
        val switchCount: Int,
        val manualCount: Int,
    )

    sealed class ImportResult {
        data class Success(
            val switchCount: Int,
            val manualCount: Int,
            val skippedCount: Int,
        ) : ImportResult()

        data class Failure(
            val reason: String,
        ) : ImportResult()
    }

    fun exportToZip(context: Context, prefs: SharedPreferences): ExportPackage {
        val switchesXml = buildXml(ModuleSettings.exportableSwitchSpecs, prefs)
        val manualXml = buildXml(ModuleSettings.exportableManualSpecs, prefs)
        val devicesText = RuntimeEnvironmentInfo.devicesText(context, prefs)
        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { zip ->
            writeZipEntry(zip, ZIP_SWITCHES_ENTRY, switchesXml)
            writeZipEntry(zip, ZIP_MANUAL_ENTRY, manualXml)
            writeZipEntry(zip, ZIP_DEVICES_ENTRY, devicesText)
        }

        return ExportPackage(
            bytes = output.toByteArray(),
            switchCount = ModuleSettings.exportableSwitchSpecs.size,
            manualCount = ModuleSettings.exportableManualSpecs.size,
        )
    }

    fun importFromZip(bytes: ByteArray, prefs: SharedPreferences): ImportResult {
        if (bytes.isEmpty()) {
            return ImportResult.Failure("匯入檔案是空的")
        }

        if (!isZipArchive(bytes)) {
            return importLegacyXml(bytes.toString(Charsets.UTF_8), prefs)
        }

        val entryMap = runCatching { readZipEntries(bytes) }.getOrElse {
            return ImportResult.Failure(it.message ?: "無法讀取 ZIP 檔案")
        }

        val parsedSwitches = entryMap[ZIP_SWITCHES_ENTRY]?.let {
            parseConfigXml(it, switchSpecsByKey)
        }
        val parsedManual = entryMap[ZIP_MANUAL_ENTRY]?.let {
            parseConfigXml(it, manualSpecsByKey)
        }

        if (parsedSwitches == null && parsedManual == null) {
            return ImportResult.Failure("ZIP 內沒有可匯入的設定檔")
        }

        val editor = prefs.edit()
        var switchCount = 0
        var manualCount = 0
        var skippedCount = 0

        clearExportedKeys(editor, switchSpecsByKey.keys)
        clearExportedKeys(editor, manualSpecsByKey.keys)

        parsedSwitches?.let { parsed ->
            applyParsedValues(editor, parsed.values)
            switchCount = parsed.values.size
            skippedCount += parsed.skippedCount
        }
        parsedManual?.let { parsed ->
            applyParsedValues(editor, parsed.values)
            manualCount = parsed.values.size
            skippedCount += parsed.skippedCount
        }

        if (!editor.commit()) {
            return ImportResult.Failure("無法寫入設定")
        }

        refreshCachesAfterImport(prefs)
        return ImportResult.Success(
            switchCount = switchCount,
            manualCount = manualCount,
            skippedCount = skippedCount,
        )
    }

    private fun importLegacyXml(xml: String, prefs: SharedPreferences): ImportResult {
        val parsed = runCatching { parseConfigXml(xml, switchSpecsByKey) }.getOrElse {
            return ImportResult.Failure(it.message ?: "匯入檔案格式不正確")
        }

        val editor = prefs.edit()
        clearExportedKeys(editor, switchSpecsByKey.keys)
        applyParsedValues(editor, parsed.values)
        if (!editor.commit()) {
            return ImportResult.Failure("無法寫入設定")
        }

        refreshCachesAfterImport(prefs)
        return ImportResult.Success(
            switchCount = parsed.values.size,
            manualCount = 0,
            skippedCount = parsed.skippedCount,
        )
    }

    private fun refreshCachesAfterImport(prefs: SharedPreferences) {
        ModuleSettings.refreshSkipVideoAdCache(prefs)
        ModuleSettings.refreshKnownBottomBarItemsCache(prefs)
        ModuleSettings.refreshKnownHomeRecommendTabsCache(prefs)
    }

    private fun buildXml(
        specs: List<ModuleSettings.ExportableConfigSpec>,
        prefs: SharedPreferences,
    ): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("utf-8", true)
        serializer.startTag(null, ROOT_TAG)

        specs.forEach { spec ->
            writeEntry(serializer, spec.key, spec.type, spec.read(prefs))
        }

        serializer.endTag(null, ROOT_TAG)
        serializer.endDocument()
        serializer.flush()
        return writer.toString()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun parseConfigXml(
        xml: String,
        allowedSpecs: Map<String, ModuleSettings.ExportableConfigSpec>,
    ): ParsedConfig {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }

        parser.nextTag()
        require(parser.name == ROOT_TAG) { "匯入檔案不是有效的設定 XML" }

        val values = LinkedHashMap<String, Any?>()
        var skippedCount = 0

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    val name = parser.getAttributeValue(null, ATTR_NAME)?.takeIf { it.isNotBlank() }
                    if (name == null) {
                        skippedCount += 1
                        skipCurrentTag(parser)
                        continue
                    }

                    val spec = allowedSpecs[name]
                    if (spec == null) {
                        skippedCount += 1
                        skipCurrentTag(parser)
                        continue
                    }

                    val value = when (tagName) {
                        TAG_BOOLEAN -> parser.getAttributeValue(null, ATTR_VALUE)?.let {
                            when (it) {
                                "true" -> true
                                "false" -> false
                                else -> throw IllegalArgumentException("設定值格式錯誤：$name")
                            }
                        }

                        TAG_INT -> parser.getAttributeValue(null, ATTR_VALUE)?.toIntOrNull()
                            ?: throw IllegalArgumentException("設定值格式錯誤：$name")

                        TAG_STRING -> parser.nextText()

                        TAG_SET -> parseStringSet(parser)

                        else -> {
                            skippedCount += 1
                            skipCurrentTag(parser)
                            continue
                        }
                    }

                    if (!isTypeCompatible(spec.type, value)) {
                        throw IllegalArgumentException("設定型別不符：$name")
                    }

                    values[name] = value

                    if (tagName == TAG_BOOLEAN || tagName == TAG_INT) {
                        skipCurrentTag(parser)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == ROOT_TAG) {
                        break
                    }
                }

                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (values.isEmpty()) {
            throw IllegalArgumentException("沒有可匯入的設定")
        }

        return ParsedConfig(values = values, skippedCount = skippedCount)
    }

    private fun parseStringSet(parser: XmlPullParser): Set<String> {
        val values = linkedSetOf<String>()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == TAG_STRING) {
                        values += parser.nextText()
                    } else {
                        skipCurrentTag(parser)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == TAG_SET) {
                        return values
                    }
                }
            }
        }
    }

    private fun writeEntry(
        serializer: org.xmlpull.v1.XmlSerializer,
        key: String,
        type: ModuleSettings.ExportableValueType,
        value: Any?,
    ) {
        when (type) {
            ModuleSettings.ExportableValueType.BOOLEAN -> {
                serializer.startTag(null, TAG_BOOLEAN)
                serializer.attribute(null, ATTR_NAME, key)
                serializer.attribute(null, ATTR_VALUE, (value as? Boolean ?: false).toString())
                serializer.endTag(null, TAG_BOOLEAN)
            }

            ModuleSettings.ExportableValueType.INT -> {
                serializer.startTag(null, TAG_INT)
                serializer.attribute(null, ATTR_NAME, key)
                serializer.attribute(null, ATTR_VALUE, (value as? Int ?: 0).toString())
                serializer.endTag(null, TAG_INT)
            }

            ModuleSettings.ExportableValueType.STRING -> {
                serializer.startTag(null, TAG_STRING)
                serializer.attribute(null, ATTR_NAME, key)
                serializer.text((value as? String).orEmpty())
                serializer.endTag(null, TAG_STRING)
            }

            ModuleSettings.ExportableValueType.STRING_SET -> {
                serializer.startTag(null, TAG_SET)
                serializer.attribute(null, ATTR_NAME, key)
                (value as? Set<*>)?.filterIsInstance<String>()?.forEach { item ->
                    serializer.startTag(null, TAG_STRING)
                    serializer.text(item)
                    serializer.endTag(null, TAG_STRING)
                }
                serializer.endTag(null, TAG_SET)
            }
        }
    }

    private fun applyParsedValues(
        editor: SharedPreferences.Editor,
        values: Map<String, Any?>,
    ) {
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, safeStringSet(value.filterIsInstance<String>()))
                else -> Unit
            }
        }
    }

    private fun clearExportedKeys(
        editor: SharedPreferences.Editor,
        keys: Iterable<String>,
    ) {
        keys.forEach { editor.remove(it) }
    }

    private fun isTypeCompatible(
        expected: ModuleSettings.ExportableValueType,
        value: Any?,
    ): Boolean {
        return when (expected) {
            ModuleSettings.ExportableValueType.BOOLEAN -> value is Boolean
            ModuleSettings.ExportableValueType.INT -> value is Int
            ModuleSettings.ExportableValueType.STRING -> value is String
            ModuleSettings.ExportableValueType.STRING_SET -> value is Set<*>
        }
    }

    private fun skipCurrentTag(parser: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth += 1
                XmlPullParser.END_TAG -> depth -= 1
            }
        }
    }

    private fun isZipArchive(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    private data class ParsedConfig(
        val values: LinkedHashMap<String, Any?>,
        val skippedCount: Int,
    )
}
