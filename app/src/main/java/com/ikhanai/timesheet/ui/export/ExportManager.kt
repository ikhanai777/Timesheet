package com.ikhanai.timesheet.ui.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.ExportFormat
import com.ikhanai.timesheet.domain.ExportRow
import com.ikhanai.timesheet.domain.Hours
import com.ikhanai.timesheet.domain.PeriodSummary
import com.ikhanai.timesheet.domain.ProjectInfo
import java.io.File

/** Writes CSV / PDF exports to the cache and opens the share sheet (FR-5). */
object ExportManager {

    fun shareCsv(context: Context, fileStem: String, rows: List<ExportRow>) {
        val file = exportFile(context, "$fileStem.csv")
        file.writeText(ExportFormat.toCsv(rows))
        share(context, file, "text/csv")
    }

    fun sharePdf(
        context: Context,
        fileStem: String,
        title: String,
        summary: PeriodSummary,
        projects: Map<Long, ProjectInfo>,
        overheadSystem: BookingSystem?,
        rows: List<ExportRow>,
    ) {
        val file = exportFile(context, "$fileStem.pdf")
        PdfWriter().use { pdf ->
            pdf.heading(title)
            pdf.gap()
            pdf.line("Total: ${Hours.format(summary.totalMinutes)} h", bold = true)
            pdf.line("NTIME: ${Hours.format(summary.ntimeMinutes)} h    P6: ${Hours.format(summary.p6Minutes)} h")
            val overheadNote = overheadSystem?.let { " (booked in ${it.label})" }.orEmpty()
            pdf.line("Overhead / Department Hours: ${Hours.format(summary.overheadMinutes)} h$overheadNote")
            if (summary.overtimeMinutes > 0) pdf.line("Overtime: ${Hours.format(summary.overtimeMinutes)} h")
            pdf.gap()
            pdf.heading("By project", size = 13f)
            val cols = floatArrayOf(0f, 210f, 300f, 380f, 430f, 480f)
            pdf.row(listOf("Project", "Number", "Oracle code", "NTIME", "P6", "Total"), cols, bold = true)
            for (p in summary.projects) {
                val info = projects[p.projectId]
                pdf.row(
                    listOf(
                        info?.name ?: "Unknown",
                        info?.projectNumber.orEmpty(),
                        info?.oracleCode.orEmpty(),
                        Hours.format(p.ntimeMinutes),
                        Hours.format(p.p6Minutes),
                        Hours.format(p.totalMinutes),
                    ),
                    cols,
                )
            }
            pdf.row(listOf("Overhead", "", "", "", "", Hours.format(summary.overheadMinutes)), cols)
            pdf.row(
                listOf(
                    "Total", "", "",
                    Hours.format(summary.ntimeMinutes),
                    Hours.format(summary.p6Minutes),
                    Hours.format(summary.totalMinutes),
                ),
                cols,
                bold = true,
            )
            pdf.gap()
            pdf.heading("Daily detail", size = 13f)
            val detailCols = floatArrayOf(0f, 75f, 245f, 330f, 420f, 470f)
            pdf.row(listOf("Date", "Project", "Number", "Oracle code", "System", "Hours"), detailCols, bold = true)
            for (row in rows) {
                pdf.row(
                    listOf(
                        row.date.toString(), row.projectName, row.projectNumber, row.oracleCode,
                        row.system, Hours.format(row.minutes),
                    ),
                    detailCols,
                )
            }
            file.outputStream().use { pdf.writeTo(it) }
        }
        share(context, file, "application/pdf")
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun exportFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name)
    }

    private fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share timesheet"))
    }
}

/** Minimal paginated A4 text/table writer on top of [PdfDocument]. */
private class PdfWriter : AutoCloseable {
    private val document = PdfDocument()
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var y = 0f
    private val paint = Paint().apply { isAntiAlias = true; textSize = 10f }

    private fun ensureSpace(height: Float) {
        if (page == null || y + height > pageHeight - margin) {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin
        }
    }

    fun heading(text: String, size: Float = 16f) {
        ensureSpace(size + 6)
        paint.textSize = size
        paint.typeface = Typeface.DEFAULT_BOLD
        y += size
        page!!.canvas.drawText(text, margin, y, paint)
        y += 6
    }

    fun line(text: String, bold: Boolean = false) {
        ensureSpace(14f)
        paint.textSize = 10f
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        y += 12
        page!!.canvas.drawText(text, margin, y, paint)
        y += 2
    }

    fun row(cells: List<String>, columns: FloatArray, bold: Boolean = false) {
        ensureSpace(14f)
        paint.textSize = 9f
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        y += 12
        cells.forEachIndexed { i, cell ->
            val x = margin + columns[i]
            val maxWidth = (if (i + 1 < columns.size) columns[i + 1] - columns[i] else pageWidth - 2 * margin - columns[i]) - 6
            page!!.canvas.drawText(ellipsize(cell, maxWidth), x, y, paint)
        }
        y += 2
    }

    fun writeTo(out: java.io.OutputStream) {
        page?.let(document::finishPage)
        page = null
        document.writeTo(out)
    }

    fun gap() {
        y += 10
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    override fun close() {
        page?.let(document::finishPage)
        page = null
        document.close()
    }
}
