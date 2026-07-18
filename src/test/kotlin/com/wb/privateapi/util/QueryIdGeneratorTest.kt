package com.wb.privateapi.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueryIdGeneratorTest {

    @Test
    fun `query id starts with qid prefix`() {
        val qid = QueryIdGenerator.getQueryIdForSearch()
        assertTrue(qid.startsWith("qid"), "qid=$qid")
    }

    /** `qid` + userID + `yyyyMMddHHmmss` (14 цифр) → длина > 14. */
    @Test
    fun `query id ends with 14-digit timestamp`() {
        val qid = QueryIdGenerator.getQueryIdForSearch()
        val tail = qid.removePrefix("qid")
        assertTrue(tail.length > 14, "tail=$tail")
        assertTrue(tail.takeLast(14).matches(Regex("\\d{14}")), "tail=$tail")
    }

    @Test
    fun `genNewUserId is numeric`() {
        val uid = QueryIdGenerator.genNewUserId()
        assertTrue(uid.matches(Regex("\\d+")), "uid=$uid")
    }

    @Test
    fun `formatDateForQueryId is 14 digits`() {
        val date = QueryIdGenerator.formatDateForQueryId()
        assertEquals(14, date.length)
        assertTrue(date.matches(Regex("\\d{14}")))
    }
}
