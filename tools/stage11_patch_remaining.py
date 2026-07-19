#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path.cwd()

def r(p): return (ROOT / p).read_text(encoding='utf-8')
def w(p,s): (ROOT / p).write_text(s.rstrip()+'\n',encoding='utf-8')
def imp(p, line):
    s=r(p)
    if line not in s:
        i=s.index('\n',s.index('package '))+1
        s=s[:i]+'\n'+line+'\n'+s[i:]
    w(p,s)
def strip(p,*lines):
    s=r(p)
    for line in lines:s=s.replace('import '+line+'\n','')
    w(p,s)

def sub(p,pattern,repl,flags=0):
    s=r(p);n=re.subn(pattern,repl,s,count=1,flags=flags)
    if n[1]!=1: raise RuntimeError(f'{p}: {pattern} -> {n[1]}')
    w(p,n[0])

p='app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/AppSettingsFragment.kt'
s=r(p).replace('import com.aqua.aqualight.platform.permissions.AppCapability\n','import com.aqua.aqualight.platform.permissions.AppCapability\nimport com.aqua.aqualight.localization.SupportedLocaleRegistry\n')
s,n=re.subn(r'    private fun observeLanguageSummary\(\) \{.*?\n    \}\n\n    private fun observeAutoUpdateState','''    private fun observeLanguageSummary() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.languageCode.collectLatest { code ->
                binding.tvLanguageSubtitle.setText(
                    SupportedLocaleRegistry.locale(code).displayNameRes
                )
            }
        }
    }

    private fun observeAutoUpdateState''',s,count=1,flags=re.S)
if n!=1:raise RuntimeError('language summary')
w(p,s)

p='app/src/main/res/values/strings.xml';s=r(p)
for lang in ('turkish','german','french','russian','chinese'):
    for suffix in ('','_flag_desc','_row_desc','_radio_desc'):
        s=re.sub(rf'\s*<string\s+name="language_{lang}{suffix}"[^>]*>.*?</string>','',s,flags=re.S)
w(p,s)
for pat in ('flag_tr.*','flag_de.*','flag_fr.*','flag_ru.*','flag_cn.*'):
    for f in (ROOT/'app/src/main/res').glob('drawable*/'+pat):f.unlink()

for p in ('app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/create/steps/TankInfoFragment.kt','app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt'):
    s=re.sub(r',\n\s*locale = AquariumDatePolicy\.setupDateLocale','',r(p));w(p,s)
for p in ('app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/create/steps/TankInfoFragment.kt','app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/TankSettingsBasicFragment.kt','app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/TankDetailTankFragment.kt'):
    s=r(p).replace('return AquariumDatePolicy.formatSetupDate(\n            millis =','return AquariumDatePolicy.formatSetupDate(\n            context = requireContext(),\n            millis =');w(p,s)

p='app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/TankSettingsEditorBottomSheet.kt';s=r(p)
s=s.replace('import java.text.DecimalFormat\n','').replace('import java.util.Locale\n','')
s=s.replace('import com.google.android.material.bottomsheet.BottomSheetDialogFragment\n','import com.google.android.material.bottomsheet.BottomSheetDialogFragment\nimport com.aqua.aqualight.localization.LocaleFormatters\n')
s=s.replace('        val formatter = DecimalFormat("#.##")\n','        val locale = LocaleFormatters.currentLocale(requireContext())\n').replace('            return formatter.format(value)','            return LocaleFormatters.formatNumber(value, locale)')
s=s.replace('            val width = binding.inputWidth.text.toString().trim().toDoubleOrNull()\n            val length = binding.inputLength.text.toString().trim().toDoubleOrNull()\n            val height = binding.inputHeight.text.toString().trim().toDoubleOrNull()','            val width = LocaleFormatters.parseNumber(binding.inputWidth.text, locale)?.toDouble()\n            val length = LocaleFormatters.parseNumber(binding.inputLength.text, locale)?.toDouble()\n            val height = LocaleFormatters.parseNumber(binding.inputHeight.text, locale)?.toDouble()')
s=re.sub(r'        val locale = Locale\.forLanguageTag\(args\.getString\(ARG_LOCALE_TAG\)\.orEmpty\(\)\)\n            \.takeUnless \{ it\.language\.isBlank\(\) \}\n            \?: Locale\.getDefault\(\)','        val locale = LocaleFormatters.currentLocale(requireContext())',s)
s=s.replace('        private const val ARG_LOCALE_TAG = "arg_locale_tag"\n','').replace('            locale: Locale = Locale.getDefault(),\n','').replace('                    ARG_LOCALE_TAG to locale.toLanguageTag(),\n','')
w(p,s)

P='app/src/main/java/com/aqua/aqualight/'
def locale(p): imp(p,'import com.aqua.aqualight.localization.LocaleFormatters')

p=P+'ui/tabs/maintenance/CareTaskAdapter.kt';locale(p);s=r(p)
s=re.sub(r'    val dateText = SimpleDateFormat\(\n      "dd\.MM\.yyyy",\n      Locale\.getDefault\(\)\n    \)\.format\(Date\(millis\)\)','    val dateText = LocaleFormatters.formatDate(context, millis)',s)
s=re.sub(r'    return SimpleDateFormat\(\n      "yyyyMMdd",\n      Locale\.getDefault\(\)\n    \)\.format\(Date\(millis\)\)','    return LocaleFormatters.localDayKey(millis)',s);w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/maintenance/AquariumMaintenanceFragment.kt';locale(p);s=r(p)
for a,b in [(r'return SimpleDateFormat\(\n            "dd\.MM\.yyyy",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatDate(requireContext(), millis)'),(r'return SimpleDateFormat\(\n            "HH:mm",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatTime(requireContext(), millis)'),(r'return SimpleDateFormat\(\n            "dd\.MM\.yyyy HH:mm",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatDateTime(requireContext(), millis)'),(r'return SimpleDateFormat\(\n            "yyyyMMdd",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','return LocaleFormatters.localDayKey(millis)')]:s=re.sub(a,b,s)
w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/maintenance/AddCareTaskFragment.kt';locale(p);s=r(p)
s=re.sub(r'binding\.tvDueDateValue\.text = SimpleDateFormat\(\n            "dd MMM yyyy",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(selectedCalendar\.timeInMillis\)\)','binding.tvDueDateValue.text = LocaleFormatters.formatDate(requireContext(), selectedCalendar.timeInMillis)',s)
s=re.sub(r'binding\.tvDueTimeValue\.text = SimpleDateFormat\(\n            "HH:mm",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(selectedCalendar\.timeInMillis\)\)','binding.tvDueTimeValue.text = LocaleFormatters.formatTime(requireContext(), selectedCalendar.timeInMillis)',s);w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/maintenance/TaskDetailFragment.kt';locale(p);s=r(p)
for a,b in [(r'return SimpleDateFormat\(\n      "dd\.MM\.yyyy",\n      Locale\.getDefault\(\)\n    \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatDate(requireContext(), millis)'),(r'return SimpleDateFormat\(\n      "HH:mm",\n      Locale\.getDefault\(\)\n    \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatTime(requireContext(), millis)'),(r'return SimpleDateFormat\(\n      "dd\.MM\.yyyy HH:mm",\n      Locale\.getDefault\(\)\n    \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatDateTime(requireContext(), millis)')]:s=re.sub(a,b,s)
w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/aquarium/AquariumTankAdapter.kt';locale(p);s=r(p)
s=re.sub(r'      val formatter = SimpleDateFormat\(\n        "yyyy/MM/dd",\n        Locale\.getDefault\(\)\n      \)\n\n      return context\.getString\(\n        R\.string\.aquarium_setup_date_card_format,\n        formatter\.format\(Date\(setupDateMillis\)\)\n      \)','      return context.getString(\n        R.string.aquarium_setup_date_card_format,\n        LocaleFormatters.formatDate(context, setupDateMillis)\n      )',s);w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/aquarium/detail/TankDetailLivestockFormFragment.kt';locale(p);s=r(p)
s=re.sub(r'        val formatter = SimpleDateFormat\(\n            "dd MMM yyyy",\n            Locale\.getDefault\(\)\n        \)\n\n        binding\.tvAddedDateValue\.text = formatter\.format\(\n            Date\(selectedAddedDateMillis\)\n        \)','        binding.tvAddedDateValue.text = LocaleFormatters.formatDate(requireContext(), selectedAddedDateMillis)',s)
s=s.replace('binding.tvQuantityValue.text = selectedQuantity.toString()','binding.tvQuantityValue.text = LocaleFormatters.formatInteger(requireContext(), selectedQuantity.toLong())').replace('height = resources.getDimensionPixelOffset(R.dimen.aqua_size_46)','height = resources.getDimensionPixelOffset(R.dimen.aqua_size_48)');w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/aquarium/detail/TankDetailLifeFragment.kt';locale(p);s=r(p)
s=re.sub(r'        val formatter = SimpleDateFormat\(\n            "dd MMM yyyy",\n            Locale\.getDefault\(\)\n        \)\n\n        return getString\(\n            R\.string\.aquarium_livestock_added_date_format,\n            formatter\.format\(Date\(addedDateMillis\)\)\n        \)','        return getString(\n            R.string.aquarium_livestock_added_date_format,\n            LocaleFormatters.formatDate(requireContext(), addedDateMillis)\n        )',s);w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/aquarium/detail/TankDetailActivityFragment.kt';locale(p);s=r(p)
for a,b in [(r'return SimpleDateFormat\(\n            "dd\.MM\.yyyy HH:mm",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatDateTime(requireContext(), millis)'),(r'val dateText = SimpleDateFormat\(\n            "dd\.MM\.yyyy",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','val dateText = LocaleFormatters.formatDate(requireContext(), millis)'),(r'return SimpleDateFormat\(\n            "HH:mm",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','return LocaleFormatters.formatTime(requireContext(), millis)'),(r'return SimpleDateFormat\(\n            "yyyyMMdd",\n            Locale\.getDefault\(\)\n        \)\.format\(Date\(millis\)\)','return LocaleFormatters.localDayKey(millis)')]:s=re.sub(a,b,s)
w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/maintenance/text/MaintenanceTextResolver.kt';s=r(p).replace('    fun completedTime(timeText: String): String\n','    fun completedTime(timeText: String): String\n\n    fun formatTime(epochMillis: Long): String\n');w(p,s)
p=P+'platform/text/AndroidMaintenanceTextResolver.kt';s=r(p).replace('import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver\n','import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver\nimport com.aqua.aqualight.localization.LocaleFormatters\n').replace('    override fun completedTime(timeText: String): String =\n        appContext.getString(R.string.maintenance_completed_time, timeText)\n','    override fun completedTime(timeText: String): String =\n        appContext.getString(R.string.maintenance_completed_time, timeText)\n\n    override fun formatTime(epochMillis: Long): String =\n        LocaleFormatters.formatTime(appContext, epochMillis)\n');w(p,s)
p=P+'ui/tabs/maintenance/MaintenanceViewModel.kt';s=r(p).replace('formatTime(completedAt)','textResolver.formatTime(completedAt)').replace('formatTime(task.dueAtMillis)','textResolver.formatTime(task.dueAtMillis)');s=re.sub(r'\n    private fun formatTime\(millis: Long\): String = SimpleDateFormat\(\n        "HH:mm",\n        Locale\.getDefault\(\)\n    \)\.format\(Date\(millis\)\)','',s);w(p,s);strip(p,'java.text.SimpleDateFormat','java.util.Date','java.util.Locale')

p=P+'ui/tabs/aquarium/export/TankPdfExporter.kt';locale(p);s=r(p).replace('  private val volumeFormatter = DecimalFormat("#.##")\n\n','').replace('generatedDate = getGeneratedDateText()','generatedDate = getGeneratedDateText(context)').replace('getSetupDateText(\n      setupDateMillis = tank.setupDateMillis,','getSetupDateText(\n      context = context,\n      setupDateMillis = tank.setupDateMillis,')
s=re.sub(r'  private fun getGeneratedDateText\(\): String \{.*?\n  \}\n\n  private fun getSetupDateText\(','  private fun getGeneratedDateText(context: Context): String =\n    LocaleFormatters.formatDateTime(context, System.currentTimeMillis())\n\n  private fun getSetupDateText(\n    context: Context,',s,flags=re.S)
s=re.sub(r'    return SimpleDateFormat\(\n      "dd MMM yyyy",\n      Locale\.getDefault\(\)\n    \)\.format\(Date\(setupDateMillis\)\)','    return LocaleFormatters.formatDate(context, setupDateMillis)',s).replace('volumeFormatter.format(liter * 0.264172)','LocaleFormatters.formatNumber(context, liter * 0.264172)').replace('volumeFormatter.format(liter)','LocaleFormatters.formatNumber(context, liter)');w(p,s);strip(p,'java.text.DecimalFormat','java.text.SimpleDateFormat','java.util.Date')

p=P+'ui/tabs/aquarium/common/AquariumDimensionFormatter.kt';s=r(p).replace('val locale = context.currentLocale()','val locale = LocaleFormatters.currentLocale(context)');s=re.sub(r'\n    private fun Context\.currentLocale\(\): Locale \{.*?\n    \}','',s,flags=re.S);w(p,s);strip(p,'java.util.Locale')
p=P+'ui/tabs/settings/device/DeviceStatusSnapshotMapper.kt';s=r(p).replace('import java.util.Locale\n','import java.text.Collator\nimport java.util.Locale\n').replace('        nowMillis: Long\n    ): List<DeviceStatusItem> {\n        return statuses\n            .sortedWith(\n                compareBy<OwnerDeviceStatusSnapshot> { status ->\n                    status.displayName.lowercase(Locale.US)\n                }.thenBy { status -> status.deviceUid }\n            )','        nowMillis: Long,\n        locale: Locale = Locale.getDefault()\n    ): List<DeviceStatusItem> {\n        val collator = Collator.getInstance(locale)\n        return statuses\n            .sortedWith { first, second ->\n                collator.compare(first.displayName, second.displayName)\n                    .takeIf { it != 0 }\n                    ?: first.deviceUid.compareTo(second.deviceUid)\n            }');w(p,s)
p=P+'ui/tabs/aquarium/detail/TankDetailTankFragment.kt';locale(p);s=r(p).replace('shortCode.uppercase(Locale.getDefault())','shortCode.uppercase(LocaleFormatters.currentLocale(requireContext()))');w(p,s);strip(p,'java.util.Locale')

p='app/src/main/res/values/component_semantic_colors.xml';s=r(p).replace('@color/aqua_palette_hex_7f91aa</color>','@color/aqua_palette_hex_8fa0b5</color>').replace('<color name="aqua_status_danger">@color/aqua_palette_hex_d85c5c</color>','<color name="aqua_status_danger">@color/aqua_palette_hex_ff6b6b</color>');w(p,s)

for f in (ROOT/(P+'ui')).rglob('*.kt'):
    for token in ('SimpleDateFormat','DecimalFormat','Locale.US'):
        if token in f.read_text(encoding='utf-8'):raise RuntimeError(f'{f}: {token}')
print('Stage 11 remaining source patch applied')
