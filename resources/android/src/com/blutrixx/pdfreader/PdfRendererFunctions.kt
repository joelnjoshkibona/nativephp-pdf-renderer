package com.blutrixx.pdfreader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import java.io.File
import java.io.FileOutputStream

object PdfRendererFunctions {

    private const val TAG = "PdfRenderer"

    private fun <T> openRenderer(path: String, block: (PdfRenderer) -> T): T {
        val file = File(path)
        require(file.exists() && file.canRead() && file.length() > 0) { "PDF not accessible: $path" }
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        return try {
            block(renderer)
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private fun renderToDisk(renderer: PdfRenderer, pageIndex: Int, targetWidth: Int, outputFile: File): Map<String, Any> {
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            error("Page index $pageIndex out of range (total: ${renderer.pageCount})")
        }
        val page = renderer.openPage(pageIndex)
        val scale = targetWidth.toFloat() / page.width
        val bitmapHeight = (page.height * scale).toInt()
        val bitmap = Bitmap.createBitmap(targetWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        page.close()
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return mapOf("cachePath" to outputFile.absolutePath, "width" to targetWidth, "height" to bitmapHeight)
    }

    /**
     * Render a single PDF page to a PNG file.
     *
     * Parameters:
     *   path        — absolute path to the PDF file
     *   page        — 1-indexed page number
     *   width       — target pixel width (default 800)
     *   output_path — (optional) absolute path where PNG is saved;
     *                 falls back to app cache at pdf_pages/page_N.png
     *
     * Returns: { cachePath, width, height }
     */
    class RenderPage(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val path = parameters["path"] as? String
                ?: return BridgeResponse.error("MISSING_PATH", "path is required")
            val pageNum = ((parameters["page"] as? Number)?.toInt() ?: 1) - 1
            val targetWidth = (parameters["width"] as? Number)?.toInt() ?: 800
            val outputPath = parameters["output_path"] as? String

            return try {
                openRenderer(path) { renderer ->
                    val outputFile = if (outputPath != null) {
                        File(outputPath)
                    } else {
                        val cacheDir = File(activity.cacheDir, "pdf_pages").also { it.mkdirs() }
                        File(cacheDir, "page_${pageNum + 1}.png")
                    }
                    val result = renderToDisk(renderer, pageNum, targetWidth, outputFile)
                    Log.d(TAG, "RenderPage: page ${pageNum + 1} → ${outputFile.absolutePath} (${result["width"]}×${result["height"]})")
                    BridgeResponse.success(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "RenderPage failed", e)
                BridgeResponse.error("RENDER_ERROR", e.message ?: "Failed to render page")
            }
        }
    }

    /**
     * Return the total page count for a PDF without rendering anything.
     *
     * Parameters:
     *   path — absolute path to the PDF file
     *
     * Returns: { pageCount }
     */
    class GetPageCount(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val path = parameters["path"] as? String
                ?: return BridgeResponse.error("MISSING_PATH", "path is required")

            return try {
                openRenderer(path) { renderer ->
                    Log.d(TAG, "GetPageCount: ${renderer.pageCount} pages in $path")
                    BridgeResponse.success(mapOf("pageCount" to renderer.pageCount))
                }
            } catch (e: Exception) {
                Log.e(TAG, "GetPageCount failed", e)
                BridgeResponse.error("READ_ERROR", e.message ?: "Failed to read PDF")
            }
        }
    }

    /**
     * Return the native width and height of a page without rendering it.
     * Used to pre-size image placeholders before the page loads.
     *
     * Parameters:
     *   path — absolute path to the PDF file
     *   page — 1-indexed page number
     *
     * Returns: { width, height }
     */
    class GetPageDimensions(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val path = parameters["path"] as? String
                ?: return BridgeResponse.error("MISSING_PATH", "path is required")
            val pageNum = ((parameters["page"] as? Number)?.toInt() ?: 1) - 1

            return try {
                openRenderer(path) { renderer ->
                    if (pageNum < 0 || pageNum >= renderer.pageCount) {
                        return@openRenderer BridgeResponse.error("INVALID_PAGE", "Page index $pageNum out of range")
                    }
                    val page = renderer.openPage(pageNum)
                    val result = BridgeResponse.success(mapOf("width" to page.width, "height" to page.height))
                    page.close()
                    result
                }
            } catch (e: Exception) {
                Log.e(TAG, "GetPageDimensions failed", e)
                BridgeResponse.error("READ_ERROR", e.message ?: "Failed to read page dimensions")
            }
        }
    }

    /**
     * Render page 1 at a small fixed width for use as a library cover image.
     *
     * Parameters:
     *   path        — absolute path to the PDF file
     *   output_path — absolute path where the PNG thumbnail should be saved
     *   width       — thumbnail width in pixels (default 320)
     *
     * Returns: { cachePath, width, height }
     */
    class RenderThumbnail(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val path = parameters["path"] as? String
                ?: return BridgeResponse.error("MISSING_PATH", "path is required")
            val outputPath = parameters["output_path"] as? String
                ?: return BridgeResponse.error("MISSING_OUTPUT_PATH", "output_path is required")
            val thumbnailWidth = (parameters["width"] as? Number)?.toInt() ?: 320

            return try {
                openRenderer(path) { renderer ->
                    val outputFile = File(outputPath)
                    val result = renderToDisk(renderer, 0, thumbnailWidth, outputFile)
                    Log.d(TAG, "RenderThumbnail: → ${outputFile.absolutePath} (${result["width"]}×${result["height"]})")
                    BridgeResponse.success(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "RenderThumbnail failed", e)
                BridgeResponse.error("RENDER_ERROR", e.message ?: "Failed to render thumbnail")
            }
        }
    }
}
