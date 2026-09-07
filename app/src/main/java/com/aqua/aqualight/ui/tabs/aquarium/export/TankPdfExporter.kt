package com.aqua.aqualight.ui.tabs.aquarium.export

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.MaterialCategoryCatalog
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object TankPdfExporter {

  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842
  private const val PAGE_MARGIN = 38f
  private const val PAGE_BOTTOM = 790f

  private const val PHOTO_HEIGHT = 125f

  fun createTankReportPdf(
    context: Context,
    tank: AquariumTankSnapshot
  ): Uri {
    val document = PdfDocument()
    val texts = TankPdfTexts.from(context)
    val writer = PdfWriter(
      document = document,
      texts = texts,
      context = context
    )

    writer.drawReportHeader(
      tankName = tank.name,
      generatedDate = getGeneratedDateText(context)
    )

    writer.drawSectionTitle(texts.sectionTankSummary)

    writer.drawLabelValue(texts.labelTankName, tank.name)
    writer.drawLabelValue(
      texts.labelTankType,
      tank.tankType.takeIf(String::isNotBlank)
        ?.let { AquariumTankTaxonomyText.tankTypeLabel(context, it) }
        ?: texts.noValue
    )
    writer.drawLabelValue(texts.labelSize, getSizeText(context, tank))
    writer.drawLabelValue(texts.labelVolume, getVolumeText(context, tank))
    writer.drawLabelValue(texts.labelSetupDate, getSetupDateText(
      context = context,
      setupDateEpochDay = tank.setupDateEpochDay,
      noValue = texts.noValue
    ))
    writer.drawLabelValue(
      texts.labelTankStyle,
      tank.tankStyle.takeIf(String::isNotBlank)
        ?.let { AquariumTankTaxonomyText.tankStyleLabel(context, it) }
        ?: texts.noValue
    )
    writer.drawLabelValue(texts.labelIdea, tank.description.ifBlank {
      texts.noValue
    })

    writer.drawSectionTitle(texts.sectionTankPhoto)

    writer.drawTankPhoto(
      bitmap = getTankPhotoBitmap(
        context = context,
        photoUri = tank.photoUri
      )
    )

    writer.drawSectionTitle(texts.sectionDevices)
    writer.drawMutedText(texts.noDevices)

    writer.drawSectionTitle(texts.sectionTankLife)

    if (tank.livestock.isEmpty()) {
      writer.drawMutedText(texts.noLivestock)
    } else {
      tank.livestock.forEachIndexed {
        index, livestock ->
        writer.drawLivestockInfo(
          number = index + 1,
          name = livestock.name,
          category = livestock.category,
          quantity = getLivestockQuantityText(
            context = context,
            quantity = livestock.quantity
          )
        )
      }
    }

    writer.drawSectionTitle(texts.sectionPlants)

    if (tank.plants.isEmpty()) {
      writer.drawMutedText(texts.noPlants)
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

    writer.drawSectionTitle(texts.sectionBioComponents)

    MaterialCategoryCatalog.bioCategories.forEach {
      category ->
      val materials = tank.materials.filter {
        material ->
        material.categoryKey == category.key
      }

      writer.drawMaterialCategory(
        title = category.title(context),
        materials = materials
     )
    }

    writer.drawSectionTitle(texts.sectionHardwareComponents)

    MaterialCategoryCatalog.hardwareCategories.forEach {
      category ->
      val materials = tank.materials.filter {
        material ->
        material.categoryKey == category.key
      }

      writer.drawMaterialCategory(
        title = category.title(context),
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
      putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.tank_pdf_share_subject, tankName))
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(
      shareIntent,
      context.getString(R.string.tank_pdf_share_chooser)
   )

    if (context !is Activity) {
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(chooser)
  }

  private fun getLivestockQuantityText(
    context: Context,
    quantity: Int
  ): String {
    val safeQuantity = quantity.coerceAtLeast(1)

    return context.resources.getQuantityString(
      R.plurals.tank_pdf_livestock_quantity_piece,
      safeQuantity,
      safeQuantity
    )
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

  private fun getGeneratedDateText(
    context: Context
  ): String {
    return LocaleFormatter.formatDateTime(
      context = context,
      timeMillis = System.currentTimeMillis()
    )
  }

  private fun getSetupDateText(
    context: Context,
    setupDateEpochDay: Long?,
    noValue: String
  ): String {
    if (setupDateEpochDay == null) {
      return noValue
    }

    return LocaleFormatter.formatDateEpochDay(context, setupDateEpochDay)
  }

  private fun getSizeText(
    context: Context,
    tank: AquariumTankSnapshot
  ): String {
    return AquariumDimensionFormatter.labeledSizeText(
      context = context,
      widthCm = tank.widthCm,
      lengthCm = tank.lengthCm,
      heightCm = tank.heightCm,
      sizeUnit = tank.sizeUnit,
      formatRes = R.string.tank_pdf_size_localized_format
    )
  }

  private fun getVolumeText(
    context: Context,
    tank: AquariumTankSnapshot
  ): String {
    val liters = AquariumVolumeCalculator.grossLiters(
      widthCm = tank.widthCm,
      lengthCm = tank.lengthCm,
      heightCm = tank.heightCm
    )

    return if (tank.volumeUnit.equals("gal", ignoreCase = true)) {
      context.getString(
        R.string.aquarium_volume_gallon_format,
        LocaleFormatter.formatDecimal(
          context,
          AquariumVolumeCalculator.litersToGallons(liters)
        )
      )
    } else {
      context.getString(
        R.string.aquarium_volume_liter_format,
        LocaleFormatter.formatDecimal(context, liters)
      )
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
      Regex("[^a-zA,Z0-9._-]"),
      "_"
    )
    .take(45)
  }

  private fun formatMaterial(
    material: AquariumMaterialSelection,
    unnamedMaterialText: String
  ): String {
    val name = material.name.ifBlank {
      unnamedMaterialText
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

  private data class TankPdfTexts(
    val reportTitle: String,
    val sectionTankSummary: String,
    val sectionTankPhoto: String,
    val sectionDevices: String,
    val sectionTankLife: String,
    val sectionPlants: String,
    val sectionBioComponents: String,
    val sectionHardwareComponents: String,
    val labelTankName: String,
    val labelTankType: String,
    val labelSize: String,
    val labelVolume: String,
    val labelSetupDate: String,
    val labelTankStyle: String,
    val labelIdea: String,
    val labelType: String,
    val labelSerial: String,
    val labelFirmware: String,
    val labelCategory: String,
    val labelQuantity: String,
    val labelGenerated: String,
    val noDevices: String,
    val noLivestock: String,
    val noPlants: String,
    val noTankPhoto: String,
    val notSelected: String,
    val device: String,
    val unnamedLivestock: String,
    val unnamedPlant: String,
    val unnamedMaterial: String,
    val generatedBy: String,
    val pageFormat: String,
    val noValue: String,
    val locale: Locale
  ) {
    fun pageText(
      pageNumber: Int
    ): String {
      return String.format(
        locale,
        pageFormat,
        pageNumber
     )
    }

    companion object {
      fun from(
        context: Context
     ): TankPdfTexts {
        return TankPdfTexts(
          reportTitle = context.getString(R.string.tank_pdf_report_title),
          sectionTankSummary = context.getString(R.string.tank_pdf_section_tank_summary),
          sectionTankPhoto = context.getString(R.string.tank_pdf_section_tank_photo),
          sectionDevices = context.getString(R.string.tank_pdf_section_devices),
          sectionTankLife = context.getString(R.string.tank_pdf_section_tank_life),
          sectionPlants = context.getString(R.string.tank_pdf_section_plants),
          sectionBioComponents = context.getString(R.string.tank_pdf_section_bio_components),
          sectionHardwareComponents = context.getString(R.string.tank_pdf_section_hardware_components),
          labelTankName = context.getString(R.string.tank_pdf_label_tank_name),
          labelTankType = context.getString(R.string.tank_pdf_label_tank_type),
          labelSize = context.getString(R.string.tank_pdf_label_size),
          labelVolume = context.getString(R.string.tank_pdf_label_volume),
          labelSetupDate = context.getString(R.string.tank_pdf_label_setup_date),
          labelTankStyle = context.getString(R.string.tank_pdf_label_tank_style),
          labelIdea = context.getString(R.string.tank_pdf_label_idea),
          labelType = context.getString(R.string.tank_pdf_label_type),
          labelSerial = context.getString(R.string.tank_pdf_label_serial),
          labelFirmware = context.getString(R.string.tank_pdf_label_firmware),
          labelCategory = context.getString(R.string.tank_pdf_label_category),
          labelQuantity = context.getString(R.string.tank_pdf_label_quantity),
          labelGenerated = context.getString(R.string.tank_pdf_label_generated),
          noDevices = context.getString(R.string.tank_pdf_no_devices),
          noLivestock = context.getString(R.string.tank_pdf_no_livestock),
          noPlants = context.getString(R.string.tank_pdf_no_plants),
          noTankPhoto = context.getString(R.string.tank_pdf_no_tank_photo),
          notSelected = context.getString(R.string.tank_pdf_not_selected),
          device = context.getString(R.string.tank_pdf_device),
          unnamedLivestock = context.getString(R.string.tank_pdf_unnamed_livestock),
          unnamedPlant = context.getString(R.string.tank_pdf_unnamed_plant),
          unnamedMaterial = context.getString(R.string.tank_pdf_unnamed_material),
          generatedBy = context.getString(R.string.tank_pdf_generated_by),
          pageFormat = context.getString(R.string.tank_pdf_page_format),
          noValue = context.getString(R.string.aquarium_no_value_placeholder),
          locale = LocaleFormatter.appLocale(context)
        )
      }
    }
  }

  private class PdfWriter(
    private val document: PdfDocument,
    private val texts: TankPdfTexts,
    private val context: Context
  ) {

    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = PAGE_MARGIN

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_surface_deep)
      textSize = 21f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_tank_pdf_exporter_color)
      textSize = 10.5f
    }

    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_surface_deep)
      textSize = 15f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_surface_deep)
      textSize = 12f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_tank_pdf_exporter_color_variant_2)
      textSize =10.5f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_tank_pdf_exporter_color_variant_3)
      textSize = 11.5f
    }

    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_content_secondary)
      textSize = 11.5f
    }

    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_tank_pdf_exporter_color_variant_4)
      textSize = 9f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ContextCompat.getColor(context, R.color.aqua_tank_pdf_exporter_color_variant_5)
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
      canvas.drawColor(ContextCompat.getColor(context, R.color.aqua_content_on_dark))
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
        texts.reportTitle,
        PAGE_MARGIN,
        y,
        titlePaint
      )

      y += 23f

      canvas.drawText(
        "${texts.labelTankName}: ${tankName.ifBlank { texts.noValue }}",
        PAGE_MARGIN,
        y,
        subtitlePaint
      )

      y += 15f

      canvas.drawText(
        "${texts.labelGenerated}: $generatedDate",
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
        color = ContextCompat.getColor(context, R.color.aqua_tank_pdf_exporter_color_variant_6)
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
          color = ContextCompat.getColor(context, R.color.aqua_content_secondary)
          textSize = 12f
          textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
          texts.noTankPhoto,
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
        "$number. ${texts.device}",
        PAGE_MARGIN,
        y,
        categoryPaint
      )

      y += 17f

      drawDeviceDetailLine(
        label = texts.labelType,
        value = type
      )

      if (serial.isNotBlank()) {
        drawDeviceDetailLine(
          label = texts.labelSerial,
          value = serial
        )
      }

      if (firmware.isNotBlank() && firmware != texts.noValue) {
        drawDeviceDetailLine(
          label = texts.labelFirmware,
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
        "$number. ${name.ifBlank { texts.unnamedLivestock }}",
        PAGE_MARGIN,
        y,
        categoryPaint
      )

      y += 17f

      drawDeviceDetailLine(
        label = texts.labelCategory,
        value = category.ifBlank {
          texts.noValue
        }
      )

      drawDeviceDetailLine(
        label = texts.labelQuantity,
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
          texts.noValue
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
          texts.unnamedPlant
        },
        paint = valuePaint,
        maxWidth = maxNameWidth
     )

      val categoryLines = wrapText(
        text = category.ifBlank {
          texts.noValue
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
      materials: List<AquariumMaterialSelection>
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
        drawMutedText(texts.notSelected)
        return
      }

      materials.forEach {
        material ->
        drawSimpleBulletText(
          text = formatMaterial(
            material = material,
            unnamedMaterialText = texts.unnamedMaterial
          )
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
      val footerText = texts.generatedBy
      val pageText = texts.pageText(pageNumber)

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
        return listOf(texts.noValue)
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
