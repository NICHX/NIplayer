package com.nichx.niplayer.database.backup

import com.nichx.niplayer.common.backup.RestoreMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 业务身份对账的核心测试。
 *
 * 重点覆盖跨设备场景：自增 id 相同但业务身份不同、业务身份相同但 id 不同。
 */
class MergePlanTest {

    private data class Row(val id: Int, val key: String, val value: String = "")

    private fun plan(
        backup: List<Row>,
        local: List<Row>,
        mode: RestoreMode,
        onDuplicate: (String) -> Unit = {},
    ) = planMerge(
        backupRows = backup,
        localRows = local,
        mode = mode,
        identity = { it.key },
        idOf = { it.id },
        withLocalId = { row, id -> row.copy(id = id) },
        onDuplicate = onDuplicate,
    )

    @Test
    fun `cross-device id collision does not overwrite unrelated local row`() {
        val local = listOf(Row(id = 1, key = "local-a"))
        val backup = listOf(Row(id = 1, key = "backup-b"))

        val result = plan(backup, local, RestoreMode.MERGE)

        assertEquals(listOf("backup-b"), result.inserts.map { it.localRow.key })
        assertEquals(0, result.inserts.single().localRow.id)
        assertTrue(result.updates.isEmpty())
        assertTrue(result.deletes.isEmpty())
    }

    @Test
    fun `same business identity updates with local id and keeps it`() {
        val local = listOf(Row(id = 7, key = "same", value = "old"))
        val backup = listOf(Row(id = 1, key = "same", value = "new"))

        val result = plan(backup, local, RestoreMode.MERGE)

        assertTrue(result.inserts.isEmpty())
        val update = result.updates.single()
        assertEquals(7, update.localId)
        assertEquals(7, update.localRow.id)
        assertEquals("new", update.localRow.value)
    }

    @Test
    fun `merge keeps local-only rows`() {
        val local = listOf(Row(id = 1, key = "keep-me"))
        val backup = listOf(Row(id = 2, key = "from-backup"))

        val result = plan(backup, local, RestoreMode.MERGE)

        assertEquals(1, result.inserts.size)
        assertTrue(result.deletes.isEmpty())
    }

    @Test
    fun `replace mirrors backup state by deleting local-only rows`() {
        val local = listOf(Row(id = 1, key = "keep-me"), Row(id = 2, key = "shared"))
        val backup = listOf(Row(id = 9, key = "shared"))

        val result = plan(backup, local, RestoreMode.REPLACE)

        assertEquals(listOf("keep-me"), result.deletes.map { it.key })
        assertEquals(1, result.updates.size)
        assertEquals(2, result.updates.single().localId)
    }

    @Test
    fun `duplicate identities inside backup keep the first and report`() {
        val duplicates = mutableListOf<String>()
        val backup = listOf(
            Row(id = 1, key = "dup", value = "first"),
            Row(id = 2, key = "dup", value = "second"),
        )

        val result = plan(backup, emptyList(), RestoreMode.MERGE) { duplicates += it }

        assertEquals(listOf("first"), result.inserts.map { it.localRow.value })
        assertEquals(listOf("dup"), duplicates)
    }

    @Test
    fun `fresh target inserts all rows with zeroed ids`() {
        val backup = listOf(Row(id = 10, key = "a"), Row(id = 20, key = "b"))

        val result = plan(backup, emptyList(), RestoreMode.MERGE)

        assertEquals(listOf("a", "b"), result.inserts.map { it.localRow.key })
        assertTrue(result.inserts.all { it.localRow.id == 0 })
        assertTrue(result.updates.isEmpty())
        assertTrue(result.deletes.isEmpty())
    }

    @Test
    fun `repeated restore of same backup is idempotent`() {
        val backup = listOf(Row(id = 3, key = "a", value = "v"))
        val localAfterFirst = listOf(Row(id = 42, key = "a", value = "v"))

        val result = plan(backup, localAfterFirst, RestoreMode.MERGE)

        assertTrue(result.inserts.isEmpty())
        assertTrue(result.deletes.isEmpty())
        assertEquals(42, result.updates.single().localId)
    }
}
