package com.bioscankit.android.cloudsync.drive

import com.bioscankit.android.cloudsync.CloudFavoriteSnapshot
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM 单测：不依赖 android.util.Base64，用十六进制编解码验证字段映射。 */
class CloudFavoriteSnapshotCodecTest {

    private val hexCoder = object : ByteArrayTextCoder {
        override fun encode(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        override fun decode(text: String): ByteArray =
            text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    @Test
    fun `round trip keeps every field`() {
        val snapshot = CloudFavoriteSnapshot(
            id = "favorite-1",
            sourceCreatedAt = 1_700_000_000_000,
            favoriteModifiedAt = 1_700_000_111_000,
            isFavorite = false,
            payload = "payload".toByteArray(),
            thumbnailData = byteArrayOf(1, 2, 3),
            schemaVersion = 2,
        )

        val decoded = CloudFavoriteSnapshotCodec.decode(
            CloudFavoriteSnapshotCodec.encodeToString(snapshot, hexCoder),
            hexCoder,
        )

        assertEquals(snapshot.id, decoded?.id)
        assertEquals(snapshot.sourceCreatedAt, decoded?.sourceCreatedAt)
        assertEquals(snapshot.favoriteModifiedAt, decoded?.favoriteModifiedAt)
        assertEquals(snapshot.isFavorite, decoded?.isFavorite)
        assertArrayEquals(snapshot.payload, decoded?.payload)
        assertArrayEquals(snapshot.thumbnailData, decoded?.thumbnailData)
        assertEquals(snapshot.schemaVersion, decoded?.schemaVersion)
    }

    @Test
    fun `null thumbnail stays null`() {
        val snapshot = CloudFavoriteSnapshot(
            id = "favorite-2",
            sourceCreatedAt = 1,
            favoriteModifiedAt = 2,
            payload = byteArrayOf(9),
            thumbnailData = null,
        )

        val json = CloudFavoriteSnapshotCodec.encode(snapshot, hexCoder)
        assertTrue(!json.has("thumbnailData"))
        assertNull(CloudFavoriteSnapshotCodec.decode(json, hexCoder).thumbnailData)
    }

    @Test
    fun `field names match the iOS schema`() {
        val json = CloudFavoriteSnapshotCodec.encode(
            CloudFavoriteSnapshot(
                id = "x",
                sourceCreatedAt = 1,
                favoriteModifiedAt = 2,
                payload = byteArrayOf(0),
            ),
            hexCoder,
        )
        listOf(
            "id", "sourceCreatedAt", "favoriteModifiedAt",
            "isFavorite", "payload", "schemaVersion",
        ).forEach { key ->
            assertTrue("缺少字段 $key", json.has(key))
        }
        assertTrue(json.get("payload") is String)
    }

    @Test
    fun `malformed json returns null instead of throwing`() {
        assertNull(CloudFavoriteSnapshotCodec.decode("{not json", hexCoder))
        assertNull(CloudFavoriteSnapshotCodec.decode(JSONObject().toString(), hexCoder))
    }
}
