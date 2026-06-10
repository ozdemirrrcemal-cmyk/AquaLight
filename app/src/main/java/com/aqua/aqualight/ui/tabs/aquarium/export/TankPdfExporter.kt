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
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import java.util.concurrent.TimeUnit
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
  private const val PAGE_MARGIN = 38f
  private const val PAGE_BOTTOM = 790f

  private const val PHOTO_HEIGHT = 125f

  private val volumeFormatter = DecimalFormat("#.##")

  fun createTankReportPdf(
    context: Context,
    tank: SavedAquariumTank,
    devices: List<DevicesDataStoreManager.DeviceInfo> = emptyList()
  ): Uri {
    val document = PdfDocument()
    val writer = PdfWriter(document)

    writer.drawReportHeader(
      tankName = tank.name,
      generatedDate = getGeneratedDateText()
    )

    writer.drawSectionTitle("1. Tank Summary")

    writer.drawLabelValue("Tank Name", tank.name)
    writer.drawLabelValue("Tank Type", tank.tankType.ifBlank {
      "-"
    })
    writer.drawLabelValue("Size", getSizeText(tank))
    writer.drawLabelValue("Volume", getVolumeText(tank))
    writer.drawLabelValue("Setup Date", getSetupDateText(tank.setupDateMillis))
    writer.drawLabelValue("Tank Style", tank.tankStyle.ifBlank {
      "-"
    })
    writer.drawLabelValue("Idea", tank.description.ifBlank {
      "-"
    })

    writer.drawSectionTitle("2. Tank Photo")

    writer.drawTankPhoto(
      bitmap = getTankPhotoBitmap(
        context = context,
        photoUri = tank.photoUri
      )
    )

    writer.drawSectionTitle("3. Devices")

    if (devices.isEmpty()) {
      writer.drawMutedText("No devices connected.")
    } else {
      devices.forEachIndexed {
        index, device ->
        writer.drawDeviceInfo(
          number = index + 1,
          type = getDeviceTypeText(device),
          serial = device.serial,
          firmware = getDeviceFirmwareText(device)
        )
      }
    }

    writer.drawSectionTitle("4. Tank Life")

    if (tank.livestock.isEmpty()) {
      writer.drawMutedText("No livestock added.")
    } else {
      tank.livestock.forEachIndexed {
        index, livestock ->
        writer.drawLivestockInfo(
          number = index + 1,
          name = livestock.name,
          category = livestock.category,
          quantity = getLivestockQuantityText(livestock.quantity)
        )
      }
    }

    writer.drawSectionTitle("5. Plants")

    if (tank.plants.isEmpty()) {
      writer.drawMutedText("No plants selected.")
    } else {
      tank.plants.forEachIndexed {
        index, plant ->
        writer.drawPlantText(
          number = index + 1,
          name = plant.plantName,
          category = plant.category
        )
      }
    }

    writer.drawSectionTitle("6. Bio Components")

    MaterialCategoryCatalog.bioCategories.forEach {
      category ->
      val materials = tank.materials.filter {
        material ->
        material.categoryKey == category.key
      }

      writer.drawMaterialCategory(
        title = category.title,
        materials = materials
      )
    }

    writer.drawSectionTitle("7. Hardware Components")

    MaterialCategoryCatalog.hardwareCategories.forEach {
      category ->
      val materials = tank.materials.filter {
        material ->
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

    FileOutputStream(outputFile).use {
      output ->
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

  private fun getDeviceNameText(
    device: DevicesDataStoreManager.DeviceInfo
  ): String {
    return device.name.ifBlank {
      "Device"
    }
  }

  private fun getDeviceTypeText(
    device: DevicesDataStoreManager.DeviceInfo
  ): String {
    return device.aquaName.ifBlank {
      "Device"
    }
  }


  private fun getDeviceFirmwareText(
    device: DevicesDataStoreManager.DeviceInfo
  ): String {
    return device.firmwareBuild
    .substringBefore(" (")
    .ifBlank {
      "-"
    }
  }

  private fun getLivestockQuantityText(
    quantity: Int
  ): String {
    val safeQuantity = quantity.coerceAtLeast(1)

    return if (safeQuantity == 1) {
      "1 pc"
    } else {
      "$safeQuantity pcs"
    }
  }

  private fun getTankPhotoBitmap(
    context: Context,
    photoUri: String?
  ): Bitmap? {
    val customPhoto = if (!photoUri.isNullOrBlank()) {
      try {
        val uri = Uri.parse(photoUri)

        when (uri.scheme) {
          ContentResolver.SCHEME_FILE -> {
            BitmapFactory.decodeFile(uri.path)
          } else -> {
            context.contentResolver.openInputStream(uri)?.use {
              input ->
              BitmapFactory.decodeStream(input)
            }
          }
        }
      } catch (exception: Exception) {
        exception.printStackTrace()
        null
      }
    } else {
      null
    }

    return customPhoto ?: BitmapFactory.decodeResource(
      context.resources,
      R.drawable.nature_aquarium
    )
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
    .ifBlank {
      "aquarium"
    }
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
      textSize = 21f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#667085")
      textSize = 10.5f
    }

    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#10233A")
      textSize = 15f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#10233A")
      textSize = 12f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#475467")
      textSize = 10.5f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#101828")
      textSize = 11.5f
    }

    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#8FA4BE")
      textSize = 11.5f
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
      ensureSpace(66f)

      canvas.drawText(
        "AquaLight Tank Report",
        PAGE_MARGIN,
        y,
        titlePaint
      )

      y += 23f

      canvas.drawText(
        "Tank Name: ${tankName.ifBlank { "-" }}",
        PAGE_MARGIN,
        y,
        subtitlePaint
      )

      y += 15f

      canvas.drawText(
        "Generated: $generatedDate",
        PAGE_MARGIN,
        y,
        subtitlePaint
      )

      y += 16f

      canvas.drawLine(
        PAGE_MARGIN,
        y,
        PAGE_WIDTH - PAGE_MARGIN,
        y,
        linePaint
      )

      y += 20f
    }

    fun drawTankPhoto(
      bitmap: Bitmap?
    ) {
      ensureSpace(PHOTO_HEIGHT + 18f)

      val photoRect = RectF(
        PAGE_MARGIN,
        y,
        PAGE_WIDTH - PAGE_MARGIN,
        y + PHOTO_HEIGHT
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
          textSize = 12f
          textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
          "No tank photo available",
          photoRect.centerX(),
          photoRect.centerY(),
          noPhotoPaint
        )
      }

      y += PHOTO_HEIGHT + 22f
    }

    fun drawSectionTitle(
      title: String
    ) {
      ensureSpace(34f)

      if (y > PAGE_MARGIN + 8f) {
        y += 2f
      }

      canvas.drawText(
        title,
        PAGE_MARGIN,
        y,
        sectionPaint
      )

      y += 16f

      canvas.drawLine(
        PAGE_MARGIN,
        y,
        PAGE_WIDTH - PAGE_MARGIN,
        y,
        linePaint
      )

      y += 13f
    }

    fun drawLabelValue(
      label: String,
      value: String
    ) {
      val valueX = PAGE_MARGIN + 125f
      val maxValueWidth = PAGE_WIDTH - PAGE_MARGIN - valueX

      val lines = wrapText(
        text = value,
        paint = valuePaint,
        maxWidth = maxValueWidth
      )

      val requiredHeight = max(
        19f,
        lines.size * 14f + 5f
      )

      ensureSpace(requiredHeight)

      canvas.drawText(
        label,
        PAGE_MARGIN,
        y,
        labelPaint
      )

      lines.forEachIndexed {
        index, line ->
        canvas.drawText(
          line,
          valueX,
          y + (index * 14f),
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

      ensureSpace(lines.size * 14f + 6f)

      lines.forEach {
        line ->
        canvas.drawText(
          line,
          PAGE_MARGIN,
          y,
          mutedPaint
        )

        y += 14f
      }

      y += 4f
    }

    fun drawDeviceInfo(
      number: Int,
      type: String,
      serial: String,
      firmware: String
    ) {
      ensureSpace(70f)

      canvas.drawText(
        "$number. Device",
        PAGE_MARGIN,
        y,
        categoryPaint
      )

      y += 17f

      drawDeviceDetailLine(
        label = "Type",
        value = type
      )

      if (serial.isNotBlank()) {
        drawDeviceDetailLine(
          label = "Serial",
          value = serial
        )
      }

      if (firmware.isNotBlank() && firmware != "-") {
        drawDeviceDetailLine(
          label = "Firmware",
          value = firmware
        )
      }

      y += 8f
    }

    fun drawLivestockInfo(
      number: Int,
      name: String,
      category: String,
      quantity: String
    ) {
      ensureSpace(70f)

      canvas.drawText(
        "$number. ${name.ifBlank { "Unnamed livestock" }}",
        PAGE_MARGIN,
        y,
        categoryPaint
      )

      y += 17f

      drawDeviceDetailLine(
        label = "Category",
        value = category.ifBlank {
          "-"
        }
      )

      drawDeviceDetailLine(
        label = "Quantity",
        value = quantity
      )

      y += 8f
    }

    private fun drawDeviceDetailLine(
      label: String,
      value: String
    ) {
      val labelX = PAGE_MARGIN + 14f
      val valueX = PAGE_MARGIN + 92f
      val maxValueWidth = PAGE_WIDTH - PAGE_MARGIN - valueX

      val lines = wrapText(
        text = value.ifBlank {
          "-"
        },
        paint = valuePaint,
        maxWidth = maxValueWidth
      )

      val requiredHeight = max(
        15f,
        lines.size * 13f + 3f
      )

      ensureSpace(requiredHeight)

      canvas.drawText(
        "$label:",
        labelX,
        y,
        labelPaint
      )

      lines.forEachIndexed {
        index, line ->
        canvas.drawText(
          line,
          valueX,
          y + (index * 13f),
          valuePaint
        )
      }

      y += requiredHeight
    }

    fun drawPlantText(
      number: Int,
      name: String,
      category: String
    ) {
      val prefix = "$number. "
      val prefixWidth = valuePaint.measureText(prefix)
      val nameX = PAGE_MARGIN + prefixWidth
      val maxNameWidth = PAGE_WIDTH - PAGE_MARGIN - nameX

      val nameLines = wrapText(
        text = name.ifBlank {
          "Unnamed plant"
        },
        paint = valuePaint,
        maxWidth = maxNameWidth
      )

      val categoryLines = wrapText(
        text = category.ifBlank {
          "-"
        },
        paint = mutedPaint,
        maxWidth = maxNameWidth
      )

      val requiredHeight =
      nameLines.size * 14f +
      categoryLines.size * 13f +
      7f

      ensureSpace(requiredHeight)

      canvas.drawText(
        prefix,
        PAGE_MARGIN,
        y,
        valuePaint
      )

      nameLines.forEachIndexed {
        index, line ->
        canvas.drawText(
          line,
          nameX,
          y + (index * 14f),
          valuePaint
        )
      }

      y += nameLines.size * 14f

      categoryLines.forEach {
        line ->
        canvas.drawText(
          line,
          nameX,
          y,
          mutedPaint
        )

        y += 13f
      }

      y += 7f
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
      titleLines.size * 14f +
      subtitleLines.size * 13f +
      8f

      ensureSpace(requiredHeight)

      canvas.drawText(
        "-",
        PAGE_MARGIN,
        y,
        valuePaint
      )

      titleLines.forEachIndexed {
        index, line ->
        canvas.drawText(
          line,
          PAGE_MARGIN + 14f,
          y + (index * 14f),
          valuePaint
        )
      }

      y += titleLines.size * 14f

      subtitleLines.forEach {
        line ->
        canvas.drawText(
          line,
          PAGE_MARGIN + 14f,
          y,
          mutedPaint
        )

        y += 13f
      }

      y += 6f
    }

    fun drawMaterialCategory(
      title: String,
      materials: List<SavedAquariumMaterial>
    ) {
      ensureSpace(26f)

      canvas.drawText(
        title,
        PAGE_MARGIN,
        y,
        categoryPaint
      )

      y += 15f

      if (materials.isEmpty()) {
        drawMutedText("Not selected")
        return
      }

      materials.forEach {
        material ->
        drawSimpleBulletText(
          text = formatMaterial(material)
        )
      }

      y += 4f
    }

    private fun drawSimpleBulletText(
      text: String
    ) {
      val maxWidth = PAGE_WIDTH - (PAGE_MARGIN * 2) - 16f

      val lines = wrapText(
        text = text,
        paint = valuePaint,
        maxWidth = maxWidth
      )

      val requiredHeight = lines.size * 14f + 6f

      ensureSpace(requiredHeight)

      canvas.drawText(
        "-",
        PAGE_MARGIN,
        y,
        valuePaint
      )

      lines.forEachIndexed {
        index, line ->
        canvas.drawText(
          line,
          PAGE_MARGIN + 14f,
          y + (index * 14f),
          valuePaint
        )
      }

      y += requiredHeight
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

      val safeMaxWidth = maxWidth.coerceAtLeast(80f)
      val words = text.trim().split(Regex("\\s+"))
      val lines = mutableListOf<String>()
      var currentLine = ""

      words.forEach {
        word ->
        val candidate = if (currentLine.isBlank()) {
          word
        } else {
          "$currentLine $word"
        }

        if (paint.measureText(candidate) <= safeMaxWidth) {
          currentLine = candidate
        } else {
          if (currentLine.isNotBlank()) {
            lines.add(currentLine)
          }

          if (paint.measureText(word) <= safeMaxWidth) {
            currentLine = word
          } else {
            val brokenWordLines = breakLongWord(
              word = word,
              paint = paint,
              maxWidth = safeMaxWidth
            )

            if (brokenWordLines.isNotEmpty()) {
              lines.addAll(
                brokenWordLines.dropLast(1)
              )
              currentLine = brokenWordLines.last()
            } else {
              currentLine = word
            }
          }
        }
      }

      if (currentLine.isNotBlank()) {
        lines.add(currentLine)
      }

      return lines
    }

    private fun breakLongWord(
      word: String,
      paint: Paint,
      maxWidth: Float
    ): List<String> {
      val result = mutableListOf<String>()
      var current = ""

      word.forEach {
        char ->
        val candidate = current + char

        if (paint.measureText(candidate) <= maxWidth) {
          current = candidate
        } else {
          if (current.isNotBlank()) {
            result.add(current)
          }

          current = char.toString()
        }
      }

      if (current.isNotBlank()) {
        result.add(current)
      }

      return result
    }
  }
}