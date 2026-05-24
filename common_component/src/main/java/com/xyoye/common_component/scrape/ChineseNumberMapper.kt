package com.xyoye.common_component.scrape

object ChineseNumberMapper {

    private val digitMap = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        '两' to 2, '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4,
        '伍' to 5, '陆' to 6, '柒' to 7, '捌' to 8, '玖' to 9
    )

    private val units = mapOf(
        '十' to 10, '百' to 100
    )

    fun numberToChinese(number: String): String {
        val n = number.toIntOrNull() ?: return number
        if (n == 0) return "零"
        if (n < 10) return digitMap.entries.first { it.value == n }.key.toString()
        if (n >= 1000) return number
        val result = StringBuilder()
        if (n >= 100) {
            result.append(digitMap.entries.first { it.value == n / 100 }.key)
            result.append('百')
            val remainder = n % 100
            if (remainder < 10 && remainder > 0) result.append('零')
        }
        val tens = (n % 100) / 10
        if (tens > 0) {
            if (tens > 1 || n >= 100) result.append(digitMap.entries.first { it.value == tens }.key)
            result.append('十')
        }
        val ones = n % 10
        if (ones > 0) result.append(digitMap.entries.first { it.value == ones }.key)
        return result.toString()
    }

    fun chineseToNumber(chinese: String): Int {
        if (chinese.isEmpty()) return -1
        val isDigits = chinese.all { it.isDigit() }
        if (isDigits) return chinese.toIntOrNull() ?: -1

        var result = 0
        var current = 0
        for (char in chinese) {
            if (digitMap.containsKey(char)) {
                current = digitMap[char]!!
            } else if (units.containsKey(char)) {
                val unit = units[char]!!
                if (current == 0) current = 1
                result += current * unit
                current = 0
            }
        }
        result += current
        return if (result > 0) result else -1
    }
}
