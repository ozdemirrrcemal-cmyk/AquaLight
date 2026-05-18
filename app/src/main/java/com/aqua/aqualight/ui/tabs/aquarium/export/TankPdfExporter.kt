package com.aqua.aqualight.ui.tabs.aquarium.export

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryCatalog
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object TankPdfExporter {

  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842
  private const val PAGE_MARGIN = 40f
  private const val PAGE_BOTTOM = 790f

  private val volumeFormatter = DecimalFormat("#.##")

  fun createTankReportPdf(
    context: Context,
    tank: SavedAquariumTank
  ): Uri {
    val document = PdfDocument()
    val writer = PdfWriter(document)

    writer.drawReportHeader(
      tankName = tank.name,
      generatedDate = getGeneratedDateText()
    )

    writer.drawTankPhoto(
      bitmap = getTankPhotoBitmap(
        context = context,
        photoUri = tank.photoUri
      )
    )

    writer.drawSectionTitle("1. Tank Summary")

    writer.drawLabelValue("Tank Name", tank.name)
    writer.drawLabelValue("Tank Type", tank.tankType.ifBlank { "-" })
    writer.drawLabelValue("Size", getSizeText(tank))
    writer.drawLabelValue("Volume", getVolumeText(tank))
    writer.drawLabelValue("Setup Date", getSetupDateText(tank.setupDateMillis))
    writer.drawLabelValue("Tank Style", tank.tankStyle.ifBlank { "-" })
    writer.drawLabelValue("Idea", tank.description.ifBlank { "-" })

    writer.drawSectionTitle("3. Plants")

    if (tank.plants.isEmpty()) {
      writer.drawMutedText("No plants selected.")
    } else {
      tank.plants.forEachIndexed { index, plant ->
        writer.drawBulletText(
          title = "${index + 1}. ${plant.plantName}",
          subtitle = plant.category
        )
      }
    }

    writer.drawSectionTitle("4. Bio Components")

    MaterialCategoryCatalog.bioCategories.forEach { category ->
      val materials = tank.materials.filter { material ->
        material.categoryKey == category.key
      }

      writer.drawMaterialCategory(
        title = category.title,
        materials = materials
      )
    }

    writer.drawSectionTitle("5. Hardware Components")

    MaterialCategoryCatalog.hardwareCategories.forEach { category ->
      val materials = tank.materials.filter { material ->
        material.categoryKey == category.key
      }

      writer.drawMaterialCategory(
        title = category.title,
        materials = materials
      )
    }

    writer.finish()

    val outputDir = File(
      context.cacheDir,
      "tank_exports"
    )

    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    val outputFile = File(
      outputDir,
      "${createSafeFileName(tank.name)}_${System.currentTimeMillis()}.pdf"
    )

    FileOutputStream(outputFile).use { output ->
      document.writeTo(output)
    }

    document.close()

    return FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      outputFile
    )
  }

  fun shareTankReportPdf(
    context: Context,
    pdfUri: Uri,
    tankName: String
  ) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/pdf"
      putExtra(Intent.EXTRA_STREAM, pdfUri)
      putExtra(Intent.EXTRA_SUBJECT, "AquaLight Tank Report - $tankName")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(
      shareIntent,
      "Export Tank Data"
    )

    if (context !is Activity) {
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(chooser)
  }

  private fun getTankPhotoBitmap(
    context: Context,
    photoUri: String?
  ): Bitmap? {
    if (photoUri.isNullOrBlank()) {
      return null
    }

    return try {
      val uri = Uri.parse(photoUri)

      when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> {
          BitmapFactory.decodeFile(uri.path)
        }

        else -> {
          context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
          }
        }
      }
    } catch (exception: Exception) {
      exception.printStackTrace()
      null
    }
  }

  private fun getGeneratedDateText(): String {
    return SimpleDateFormat(
      "dd MMM yyyy HH:mm",
      Locale.getDefault()
    ).format(Date())
  }

  private fun getSetupDateText(
    setupDateMillis: Long?
  ): String {
    if (setupDateMillis == null) {
      return "-"
    }

    return SimpleDateFormat(
      "dd MMM yyyy",
      Locale.getDefault()
    ).format(Date(setupDateMillis))
  }

  private fun getSizeText(
    tank: SavedAquariumTank
  ): String {
    return "${tank.widthCm} W x ${tank.lengthCm} L x ${tank.heightCm} H"
  }

  private fun getVolumeText(
    tank: SavedAquariumTank
  ): String {
    val liter = (
      tank.widthCm *
        tank.lengthCm *
        tank.heightCm
      ) / 1000.0

    return if (tank.volumeUnit.equals("gal", ignoreCase = true)) {
      "${volumeFormatter.format(liter * 0.264172)} gal"
    } else {
      "${volumeFormatter.format(liter)} L"
    }
  }

  private fun createSafeFileName(
    name: String
  ): String {
    return name
      .ifBlank { "aquarium" }
      .replace(
        Regex("[^a-zA-Z0-9._-]"),
        "_"
      )
      .take(45)
  }

  private fun formatMaterial(
    material: SavedAquariumMaterial
  ): String {
    val name = material.name.ifBlank {
      "Unnamed material"
    }

    val brandText = material.brand.ifBlank {
      ""
    }

    val noteText = material.note.ifBlank {
      ""
    }

    return buildString {
      append(name)

      if (brandText.isNotBlank()) {
        append(" (")
        append(brandText)
        append(")")
      }

      if (noteText.isNotBlank()) {
        append(" - ")
        append(noteText)
      }
    }
  }

  private class PdfWriter(
    private val document: PdfDocument
  ) {

    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = PAGE_MARGIN

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#10233A")
      textSize = 22f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#667085")
      textSize = 11f
    }

    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#10233A")
      textSize = 16f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#475467")
      textSize = 11f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#101828")
      textSize = 12f
    }

    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#8FA4BE")
      textSize = 12f
    }

    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#98A2B3")
      textSize = 9f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#D0D5DD")
      strokeWidth = 1f
    }

    init {
      startPage()
    }

    fun finish() {
      finishCurrentPage()
    }

    private fun startPage() {
      pageNumber++

      val pageInfo = PdfDocument.PageInfo.Builder(
        PAGE_WIDTH,
        PAGE_HEIGHT,
        pageNumber
      ).create()

      page = document.startPage(pageInfo)
      canvas = page.canvas
      canvas.drawColor(Color.WHITE)
      y = PAGE_MARGIN
    }

    private fun finishCurrentPage() {
      drawFooter()
      document.finishPage(page)
    }

    private fun newPage() {
      finishCurrentPage()
      startPage()
    }

    private fun ensureSpace(
      requiredHeight: Float
    ) {
      if (y + requiredHeight > PAGE_BOTTOM) {
        newPage()
      }
    }

    fun drawReportHeader(
      tankName: String,
      generatedDate: String
    ) {
      ensureSpace(70f)

      canvas.drawText(
        "AquaLight Tank Report",
        PAGE_MARGIN,
        y,
        titlePaint
      )

      y += 24f

      canvas.drawText(
        "Tank Name: ${tankName.ifBlank { "-" }}",
        PAGE_MARGIN,
        y,
        subtitlePaint
      )

      y += 16f

      canvas.drawText(
        "Generated: $generatedDate",
        PAGE_MARGIN,
        y,
        subtitlePaint
      )

      y += 18f

      canvas.drawLine(
        PAGE_MARGIN,
        y,
        PAGE_WIDTH - PAGE_MARGIN,
        y,
        linePaint
      )

      y += 22f
    }

    fun drawTankPhoto(
      bitmap: Bitmap?
    ) {
      drawSectionTitle("2. Tank Photo")

      ensureSpace(170f)

      val photoRect = RectF(
        PAGE_MARGIN,
        y,
        PAGE_WIDTH - PAGE_MARGIN,
        y + 150f
      )

      val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2F4F7")
      }

      canvas.drawRoundRect(
        photoRect,
        12f,
        12f,
        backgroundPaint
      )

      if (bitmap != null) {
        drawBitmapCenterCrop(
          bitmap = bitmap,
          destination = photoRect
        )
      } else {
        val noPhotoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#8FA4BE")
          textSize = 13f
          textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
          "No tank photo available",
          photoRect.centerX(),
          photoRect.centerY(),
          noPhotoPaint
        )
      }

      y += 172f
    }

    fun drawSectionTitle(
      title: String
    ) {
      ensureSpace(38f)

      if (y > PAGE_MARGIN + 8f) {
        y += 4f
      }

      canvas.drawText(
        title,
        PAGE_MARGIN,
        y,
        sectionPaint
      )

      y += 18f

      canvas.drawLine(
        PAGE_MARGIN,
        y,
        PAGE_WIDTH - PAGE_MARGIN,
        y,
        linePaint
      )

      y += 16f
    }

    fun drawLabelValue(
      label: String,
      value: String
    ) {
      val valueX = PAGE_MARGIN + 130f
      val maxValueWidth = PAGE_WIDTH - PAGE_MARGIN - valueX

      val lines = wrapText(
        text = value,
        paint = valuePaint,
        maxWidth = maxValueWidth
      )

      val requiredHeight = max(
        22f,
        lines.size * 16f + 6f
      )

      ensureSpace(requiredHeight)

      canvas.drawText(
        label,
        PAGE_MARGIN,
        y,
        labelPaint
      )

      lines.forEachIndexed { index, line ->
        canvas.drawText(
          line,
          valueX,
          y + (index * 16f),
          valuePaint
        )
      }

      y += requiredHeight
    }

    fun drawMutedText(
      text: String
    ) {
      val lines = wrapText(
        text = text,
        paint = mutedPaint,
        maxWidth = PAGE_WIDTH - (PAGE_MARGIN * 2)
      )

      ensureSpace(lines.size * 15f + 8f)

      lines.forEach { line ->
        canvas.drawText(
          line,
          PAGE_MARGIN,
          y,
          mutedPaint
        )

        y += 15f
      }

      y += 6f
    }

    fun drawBulletText(
      title: String,
      subtitle: String
    ) {
      val maxWidth = PAGE_WIDTH - (PAGE_MARGIN * 2) - 16f

      val titleLines = wrapText(
        text = title,
        paint = valuePaint,
        maxWidth = maxWidth
      )

      val subtitleLines = wrapText(
        text = subtitle,
        paint = mutedPaint,
        maxWidth = maxWidth
      )

      val requiredHeight =
        titleLines.size * 15f +
          subtitleLines.size * 14f +
          10f

      ensureSpace(requiredHeight)

      canvas.drawText(
        "-",
        PAGE_MARGIN,
        y,
        valuePaint
      )

      titleLines.forEachIndexed { index, line ->
        canvas.drawText(
          line,
          PAGE_MARGIN + 16f,
          y + (index * 15f),
          valuePaint
        )
      }

      y += titleLines.size * 15f

      subtitleLines.forEach { line ->
        canvas.drawText(
          line,
          PAGE_MARGIN + 16f,
          y,
          mutedPaint
        )

        y += 14f
      }

      y += 8f
    }

    fun drawMaterialCategory(
      title: String,
      materials: List<SavedAquariumMaterial>
    ) {
      ensureSpace(24f)

      canvas.drawText(
        title,
        PAGE_MARGIN,
        y,
        labelPaint
      )

      y += 16f

      if (materials.isEmpty()) {
        drawMutedText("Not selected")
        return
      }

      materials.forEach { material ->
        drawBulletText(
          title = formatMaterial(material),
          subtitle = material.categoryTitle.ifBlank { title }
        )
      }
    }

    private fun drawFooter() {
      val footerText = "Generated by AquaLight"
      val pageText = "Page $pageNumber"

      canvas.drawLine(
        PAGE_MARGIN,
        PAGE_BOTTOM + 16f,
        PAGE_WIDTH - PAGE_MARGIN,
        PAGE_BOTTOM + 16f,
        linePaint
      )

      canvas.drawText(
        footerText,
        PAGE_MARGIN,
        PAGE_BOTTOM + 34f,
        footerPaint
      )

      val pageTextWidth = footerPaint.measureText(pageText)

      canvas.drawText(
        pageText,
        PAGE_WIDTH - PAGE_MARGIN - pageTextWidth,
        PAGE_BOTTOM + 34f,
        footerPaint
      )
    }

    private fun drawBitmapCenterCrop(
      bitmap: Bitmap,
      destination: RectF
    ) {
      val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
      val destinationRatio = destination.width() / destination.height()

      val sourceRect = if (bitmapRatio > destinationRatio) {
        val sourceWidth = (bitmap.height * destinationRatio).roundToInt()
        val sourceLeft = (bitmap.width - sourceWidth) / 2

        Rect(
          sourceLeft,
          0,
          sourceLeft + sourceWidth,
          bitmap.height
        )
      } else {
        val sourceHeight = (bitmap.width / destinationRatio).roundToInt()
        val sourceTop = (bitmap.height - sourceHeight) / 2

        Rect(
          0,
          sourceTop,
          bitmap.width,
          sourceTop + sourceHeight
        )
      }

      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
      }

      canvas.drawBitmap(
        bitmap,
        sourceRect,
        destination,
        paint
      )
    }

    private fun wrapText(
      text: String,
      paint: Paint,
      maxWidth: Float
    ): List<String> {
      if (text.isBlank()) {
        return listOf("-")
      }

      val words = text.split(" ")
      val lines = mutableListOf<String>()
      var currentLine = ""

      words.forEach { word ->
        val candidate = if (currentLine.isBlank()) {
          word
        } else {
          "$currentLine $word"
        }

        if (paint.measureText(candidate) <= maxWidth) {
          currentLine = candidate
        } else {
          if (currentLine.isNotBlank()) {
            lines.add(currentLine)
          }

          currentLine = word
        }
      }

      if (currentLine.isNotBlank()) {
        lines.add(currentLine)
      }

      return lines
    }
  }
}