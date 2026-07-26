package com.movit.core.data.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ImageDownloadSupportTest {

    @Test
    fun filename_isStableForTheSameUrl() {
        val url = "https://cdn.movit.app/uploads/exercises/squat.png"
        assertEquals(imageFilenameFor(url), imageFilenameFor(url))
    }

    @Test
    fun filename_doesNotCollideForSameBasenameInDifferentFolders() {
        val a = imageFilenameFor("https://cdn.movit.app/uploads/exercises/cover.png")
        val b = imageFilenameFor("https://cdn.movit.app/uploads/programs/cover.png")
        assertNotEquals(a, b)
    }

    @Test
    fun filename_keepsKnownExtensionAndFallsBackOtherwise() {
        assertTrue(imageFilenameFor("https://x.test/a/photo.WEBP").endsWith(".webp"))
        assertTrue(imageFilenameFor("https://x.test/a/photo.jpg?v=3").endsWith(".jpg"))
        // Signed/opaque URLs with no usable extension still get a decodable file name.
        assertTrue(imageFilenameFor("https://x.test/media/9f81a2").endsWith(".img"))
    }

    @Test
    fun filename_isFilesystemSafe() {
        val name = imageFilenameFor("https://x.test/a b/عربي image!.png?token=a/b")
        assertTrue(name.none { it in "/\\:?*\"<>| " }, "unsafe characters in '$name'")
    }

    @Test
    fun resolveUrl_prefixesRelativePathsWithApiBase() {
        assertEquals(
            "https://api.movit.app/uploads/a.png",
            resolveImageDownloadUrl("https://api.movit.app/", "/uploads/a.png"),
        )
        assertEquals(
            "https://api.movit.app/uploads/a.png",
            resolveImageDownloadUrl("https://api.movit.app", "uploads/a.png"),
        )
    }

    @Test
    fun resolveUrl_leavesAbsoluteUrlsUntouched() {
        val absolute = "https://cdn.movit.app/uploads/a.png"
        assertEquals(absolute, resolveImageDownloadUrl("https://api.movit.app", absolute))
    }

    @Test
    fun cachedFilenameFilter_ignoresPartialDownloads() {
        assertTrue(isCachedImageFilename("abc123_squat.png"))
        assertTrue(!isCachedImageFilename("abc123_squat.png.part"))
        assertTrue(!isCachedImageFilename("no-extension"))
    }
}
