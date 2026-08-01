package com.aqua.aqualight.data.devices.runtime.core

import org.json.JSONObject

internal object DeviceRuntimeJson {
    fun requireExactKeys(source: JSONObject, expected: Set<String>, label: String) {
        val actual = buildSet {
            val iterator = source.keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        require(actual == expected) {
            "$label keys differ from the firmware contract; expected=$expected actual=$actual"
        }
    }

    fun objectValue(source: JSONObject, key: String, label: String = key): JSONObject {
        require(source.has(key) && !source.isNull(key)) { "$label is required." }
        return source.get(key) as? JSONObject ?: error("$label must be a JSON object.")
    }

    fun stringValue(source: JSONObject, key: String, label: String = key): String {
        val value = stringAllowEmpty(source, key, label)
        require(value.isNotEmpty()) { "$label must not be empty." }
        return value
    }

    fun stringAllowEmpty(source: JSONObject, key: String, label: String = key): String {
        require(source.has(key) && !source.isNull(key)) { "$label is required." }
        val value = source.get(key) as? String ?: error("$label must be a string.")
        require(value.none(Char::isISOControl)) { "$label must not contain control characters." }
        require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
            "$label must not contain surrounding whitespace."
        }
        return value
    }

    fun booleanValue(source: JSONObject, key: String, label: String = key): Boolean {
        require(source.has(key) && !source.isNull(key)) { "$label is required." }
        return source.get(key) as? Boolean ?: error("$label must be a boolean.")
    }

    fun intValue(source: JSONObject, key: String, label: String = key): Int {
        val value = integralValue(source, key, label)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "$label is outside the supported integer range."
        }
        return value.toInt()
    }

    fun longValue(source: JSONObject, key: String, label: String = key): Long =
        integralValue(source, key, label)

    fun doubleValue(source: JSONObject, key: String, label: String = key): Double {
        require(source.has(key) && !source.isNull(key)) { "$label is required." }
        val value = source.get(key) as? Number ?: error("$label must be numeric.")
        return value.toDouble().also { number ->
            require(number.isFinite()) { "$label must be finite." }
        }
    }

    fun copyObject(source: JSONObject): JSONObject = JSONObject(source.toString())

    private fun integralValue(source: JSONObject, key: String, label: String): Long {
        require(source.has(key) && !source.isNull(key)) { "$label is required." }
        val value = source.get(key) as? Number ?: error("$label must be an integer.")
        val asDouble = value.toDouble()
        val asLong = value.toLong()
        require(asDouble.isFinite() && asDouble == asLong.toDouble()) {
            "$label must be an integer."
        }
        return asLong
    }
}

internal fun String.utf8ByteCount(): Int = toByteArray(Charsets.UTF_8).size
