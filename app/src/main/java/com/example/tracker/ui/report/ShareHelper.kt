package com.example.tracker.ui.report

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareHelper {

    /**
     * Saves the rendered image card directly to Pictures/Dinrekha in MediaStore.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val filename = "Dinrekha_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.TITLE, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Dinrekha")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return runCatching {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Toast.makeText(context, com.example.tracker.ui.i18n.tr("Saved to Pictures/Dinrekha"), Toast.LENGTH_SHORT).show()
            uri
        }.getOrElse {
            resolver.delete(uri, null, null)
            Toast.makeText(context, com.example.tracker.ui.i18n.tr("Failed to save image"), Toast.LENGTH_SHORT).show()
            null
        }
    }

    /**
     * Prepares and shares image via Android standard share sheet (Facebook, WhatsApp, Insta, etc.).
     */
    fun shareImage(context: Context, bitmap: Bitmap, chooserTitle: String) {
        val uri = writeBitmapToCache(context, bitmap) ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Direct share to Facebook Story using com.facebook.stories.ADD_TO_STORY intent.
     * Falls back to standard share sheet if Facebook is not installed or resolution fails.
     */
    fun shareToFacebookStory(context: Context, bitmap: Bitmap, fbAppId: String): Boolean {
        val uri = writeBitmapToCache(context, bitmap) ?: return false

        // Check if Facebook app is installed
        val pm = context.packageManager
        val isFbInstalled = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo("com.facebook.katana", PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("com.facebook.katana", 0)
            }
            true
        }.getOrDefault(false)

        if (!isFbInstalled) {
            shareImage(context, bitmap, "Share to Facebook")
            return false
        }

        val storyIntent = Intent("com.facebook.stories.ADD_TO_STORY").apply {
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("interactive_asset_uri", uri)
            if (fbAppId.isNotBlank()) {
                putExtra("com.facebook.platform.extra.APPLICATION_ID", fbAppId)
            }
            setPackage("com.facebook.katana")
        }

        return runCatching {
            context.startActivity(storyIntent)
            true
        }.getOrElse {
            // Fallback to share sheet
            shareImage(context, bitmap, "Share Story Card")
            false
        }
    }

    private fun writeBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        val shareFolder = File(context.cacheDir, "shares").apply { mkdirs() }
        val shareFile = File(shareFolder, "card_${System.currentTimeMillis()}.png")

        return runCatching {
            FileOutputStream(shareFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
        }.getOrNull()
    }
}

