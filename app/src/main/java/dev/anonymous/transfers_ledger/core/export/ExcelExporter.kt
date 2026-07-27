package dev.anonymous.transfers_ledger.core.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.dhatim.fastexcel.Workbook
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExcelExporter(private val context: Context) {
    fun export(transactions: List<TransactionWithCustomer>): ExportResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "palpay_tracker_$timestamp.xlsx"

        return try {
            val uri = writeXlsx(fileName, transactions)
            ExportResult(fileName, false, uri, XLSX_MIME)
        } catch (_: Throwable) {
            val csvName = fileName.replace(".xlsx", ".csv")
            val uri = writeCsv(csvName, transactions)
            ExportResult(csvName, true, uri, CSV_MIME)
        }
    }

    private fun writeXlsx(fileName: String, transactions: List<TransactionWithCustomer>): Uri {
        val target = openDownloadTarget(fileName, XLSX_MIME)
        target.output.use { output ->
            val workbook = Workbook(output, "PalPay Tracker", "1.0")
            val sheet = workbook.newWorksheet("Transactions")
            val headers = listOf("التاريخ", "المصدر", "الاسم", "المبلغ", "الاتجاه")
            headers.forEachIndexed { index, header -> sheet.value(0, index, header) }
            transactions.forEachIndexed { rowIndex, item ->
                val row = rowIndex + 1
                val transaction = item.transaction
                sheet.value(row, 0, TimeUtils.getFullDateTimeArabic(transaction.timestamp, Locale("ar")))
                sheet.value(row, 1, transaction.walletSource)
                sheet.value(row, 2, item.displayName)
                sheet.value(row, 3, transaction.amount)
                sheet.value(row, 4, if (transaction.direction == TransactionDirection.OUTGOING) "صادر" else "وارد")
            }
            workbook.finish()
        }
        return target.uri
    }

    private fun writeCsv(fileName: String, transactions: List<TransactionWithCustomer>): Uri {
        val target = openDownloadTarget(fileName, CSV_MIME)
        target.output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("التاريخ,المصدر,الاسم,المبلغ,الاتجاه")
            transactions.forEach { item ->
                val transaction = item.transaction
                val values = listOf(
                    TimeUtils.getFullDateTimeArabic(transaction.timestamp, Locale("ar")),
                    transaction.walletSource,
                    item.displayName,
                    String.format(Locale.US, "%.2f", transaction.amount),
                    if (transaction.direction == TransactionDirection.OUTGOING) "صادر" else "وارد"
                )
                writer.appendLine(values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        }
        return target.uri
    }

    private fun openDownloadTarget(fileName: String, mimeType: String): ExportTarget {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create export file")
            ExportTarget(uri, resolver.openOutputStream(uri) ?: error("Unable to open export file"))
        } else {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            ExportTarget(Uri.fromFile(file), FileOutputStream(file))
        }
    }

    data class ExportResult(
        val fileName: String,
        val usedCsvFallback: Boolean,
        val uri: Uri,
        val mimeType: String
    )

    private data class ExportTarget(val uri: Uri, val output: OutputStream)

    companion object {
        private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val CSV_MIME = "text/csv"
    }
}
