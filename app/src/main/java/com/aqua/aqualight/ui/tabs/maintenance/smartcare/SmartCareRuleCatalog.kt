package com.aqua.aqualight.ui.tabs.maintenance.smartcare

object SmartCareRuleCatalog {

  val startupRules: List<SmartCareRule> = listOf(

    SmartCareRule(
      id = "startup_day_1_general_check",
      dayStart = 1,
      dayEnd = 1,
      conditions = listOf(
        SmartCareCondition.STARTUP_PERIOD
      ),
      taskType = SmartCareTaskType.GENERAL_CHECK,
      titleTr = "Yeni kurulum kontrolü",
      messageTr = "Filtrenin çalıştığını, ekipmanların doğru kurulduğunu ve su akışının yeterli olduğunu kontrol edin.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("Tropica", "ADA", "Dennerle")
    ),

    SmartCareRule(
      id = "startup_light_6_hours_planted",
      dayStart = 1,
      dayEnd = 21,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.HAS_LIGHT
      ),
      taskType = SmartCareTaskType.LIGHTING,
      titleTr = "Işık süresini kontrol et",
      messageTr = "Yeni kurulum döneminde ışığı yaklaşık 6 saat civarında tutmak yosun riskini azaltır.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Tropica", "Chihiros", "CO2Art")
    ),

    SmartCareRule(
      id = "startup_co2_daily_check",
      dayStart = 1,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_CO2
      ),
      taskType = SmartCareTaskType.CO2_CHECK,
      titleTr = "CO₂ kontrolü",
      messageTr = "CO₂ zamanlamasını, drop checker rengini ve canlıların davranışını kontrol edin.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.DAILY,
      sourceTags = listOf("Tropica", "CO2Art", "Chihiros")
    ),

    SmartCareRule(
      id = "startup_active_soil_water_change_first_week",
      dayStart = 2,
      dayEnd = 7,
      conditions = listOf(
        SmartCareCondition.HAS_ACTIVE_SOIL
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleTr = "Başlangıç su değişimi",
      messageTr = "Aktif soil yeni kurulumda fazla besin salabilir. Su değişimi yosun riskini azaltmaya yardımcı olur.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.EVERY_2_DAYS,
      sourceTags = listOf("ADA", "Tropica", "CO2Art")
    ),

    SmartCareRule(
      id = "startup_planted_water_change_week_1_4",
      dayStart = 1,
      dayEnd = 28,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleTr = "Yeni kurulum su değişimi",
      messageTr = "İlk haftalarda düzenli su değişimi, fazla besinleri ve yosun riskini azaltmaya yardımcı olur.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Tropica", "ADA", "Dennerle")
    ),

    SmartCareRule(
      id = "startup_plant_melt_check",
      dayStart = 3,
      dayEnd = 21,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.PLANT_CHECK,
      titleTr = "Bitki adaptasyon kontrolü",
      messageTr = "Eriyen veya çürüyen yaprakları temizleyin. Yeni dikilen bitkiler ilk haftalarda adaptasyon gösterebilir.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.EVERY_3_DAYS,
      sourceTags = listOf("Tropica", "Dennerle")
    ),

    SmartCareRule(
      id = "startup_algae_check",
      dayStart = 5,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.GLASS_CLEANING,
      titleTr = "Yosun kontrolü",
      messageTr = "Cam, zemin ve bitki yapraklarında yosun başlangıcı olup olmadığını kontrol edin.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Tropica", "CO2Art", "Chihiros")
    ),

    SmartCareRule(
      id = "startup_fertilizer_unknown_week_2",
      dayStart = 8,
      dayEnd = 14,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.FERTILIZER_UNKNOWN
      ),
      taskType = SmartCareTaskType.FERTILIZER,
      titleTr = "Gübrelemeyi düşük dozla değerlendir",
      messageTr = "Yeni kurulum döneminde gübrelemeye düşük dozla başlamak yosun riskini azaltabilir.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("Dennerle", "Tropica")
    ),

    SmartCareRule(
      id = "startup_fertilizer_selected_week_2",
      dayStart = 8,
      dayEnd = 21,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.HAS_FERTILIZER
      ),
      taskType = SmartCareTaskType.FERTILIZER,
      titleTr = "Gübre dozunu kontrol et",
      messageTr = "Tankınız yeni kurulum döneminde. Seçtiğiniz gübre için düşük başlangıç dozu daha güvenli olabilir.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Tropica", "Dennerle", "2Hr Aquarist", "ADA")
    ),

    SmartCareRule(
      id = "startup_water_test_before_livestock",
      dayStart = 14,
      dayEnd = 30,
      conditions = listOf(
        SmartCareCondition.NO_LIVESTOCK
      ),
      taskType = SmartCareTaskType.WATER_TEST,
      titleTr = "Canlı ekleme öncesi su testi",
      messageTr = "Canlı eklemeden önce amonyak ve nitrit değerlerinin güvenli olduğundan emin olun.",
      priority = SmartCarePriority.CRITICAL,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      requiresWaterTest = true,
      sourceTags = listOf("ADA", "Dennerle", "Tropica")
    ),

    SmartCareRule(
      id = "startup_cleanup_crew_suggestion",
      dayStart = 14,
      dayEnd = 30,
      conditions = listOf(
        SmartCareCondition.NO_LIVESTOCK
      ),
      taskType = SmartCareTaskType.LIVESTOCK_CHECK,
      titleTr = "İlk canlı ekleme değerlendirmesi",
      messageTr = "Su değerleri güvenliyse temizlik ekibi veya dayanıklı canlıları kademeli eklemeyi değerlendirebilirsiniz.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      requiresWaterTest = true,
      sourceTags = listOf("ADA", "Dennerle", "Tropica")
    ),

    SmartCareRule(
      id = "startup_fish_addition_check",
      dayStart = 21,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.NO_LIVESTOCK
      ),
      taskType = SmartCareTaskType.WATER_TEST,
      titleTr = "Balık ekleme kontrolü",
      messageTr = "Amonyak ve nitrit güvenliyse canlıları az sayıda ve kademeli eklemeyi değerlendirebilirsiniz.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.ONCE,
      requiresWaterTest = true,
      sourceTags = listOf("ADA", "Tropica", "Dennerle")
    ),

    SmartCareRule(
      id = "startup_light_increase_after_week_3",
      dayStart = 22,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.HAS_LIGHT
      ),
      taskType = SmartCareTaskType.LIGHTING,
      titleTr = "Işık süresini artırmayı değerlendir",
      messageTr = "Yosun artışı yoksa ışık süresi kademeli olarak artırılabilir. Ani artışlardan kaçının.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("Tropica", "Chihiros", "CO2Art")
    ),

    SmartCareRule(
      id = "startup_first_trim_check",
      dayStart = 21,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.PLANT_TRIM,
      titleTr = "İlk budama kontrolü",
      messageTr = "Hızlı büyüyen bitkiler uzadıysa hafif budama yaparak yeni sürgünleri destekleyebilirsiniz.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("Tropica", "ADA")
    ),

    SmartCareRule(
      id = "startup_weekly_water_change_after_day_30",
      dayStart = 31,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.STARTUP_PERIOD
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleTr = "Haftalık su değişimi",
      messageTr = "Tank oturmaya başladıkça haftalık düzenli su değişimi su kalitesini korumaya yardımcı olur.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Tropica", "Dennerle", "ADA")
    ),

    SmartCareRule(
      id = "startup_filter_flow_check",
      dayStart = 30,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_FILTER
      ),
      taskType = SmartCareTaskType.FILTER_CHECK,
      titleTr = "Filtre akışını kontrol et",
      messageTr = "Filtre çıkış debisi azaldıysa temizlik gerekebilir. Biyolojik medyayı musluk suyuyla yıkamayın.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.EVERY_2_WEEKS,
      sourceTags = listOf("ADA", "Dennerle")
    ),

    SmartCareRule(
      id = "startup_shrimp_stability_warning",
      dayStart = 14,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_SHRIMP
      ),
      taskType = SmartCareTaskType.LIVESTOCK_CHECK,
      titleTr = "Karides stabilite kontrolü",
      messageTr = "Karidesler ani değişimlere hassastır. Su değişimlerinde sıcaklık ve değer farkını düşük tutmaya çalışın.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Dennerle", "ADA")
    ),

    SmartCareRule(
      id = "startup_fish_feeding_warning",
      dayStart = 1,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_FISH
      ),
      taskType = SmartCareTaskType.FEEDING,
      titleTr = "Yemleme miktarını kontrol et",
      messageTr = "Canlıların kısa sürede tüketebileceği kadar yem verin. Fazla yem su kalitesini bozabilir.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.DAILY,
      sourceTags = listOf("ADA", "Dennerle")
    ),

    SmartCareRule(
      id = "startup_day_90_complete",
      dayStart = 90,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.STARTUP_PERIOD
      ),
      taskType = SmartCareTaskType.GENERAL_CHECK,
      titleTr = "Kurulum dönemi tamamlandı",
      messageTr = "Tankınız ilk 90 günlük başlangıç dönemini tamamladı. Artık düzenli bakım rutinine geçebilirsiniz.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("Tropica")
    )
  )

  val matureTankRules: List<SmartCareRule> = listOf(

    SmartCareRule(
      id = "mature_weekly_water_change",
      dayStart = 91,
      dayEnd = 36500,
      conditions = listOf(
        SmartCareCondition.MATURE_TANK
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleTr = "Haftalık su değişimi",
      messageTr = "Düzenli su değişimi, su kalitesini ve tank dengesini korumaya yardımcı olur.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Dennerle", "ADA", "Tropica")
    ),

    SmartCareRule(
      id = "mature_planted_trim_weekly",
      dayStart = 91,
      dayEnd = 36500,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.PLANT_TRIM,
      titleTr = "Bitki budama kontrolü",
      messageTr = "Uzayan veya gölge yapan bitkileri kontrol edin. Düzenli budama bitki formunu korur.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("Tropica", "ADA")
    ),

    SmartCareRule(
      id = "mature_filter_monthly_check",
      dayStart = 91,
      dayEnd = 36500,
      conditions = listOf(
        SmartCareCondition.HAS_FILTER
      ),
      taskType = SmartCareTaskType.FILTER_CHECK,
      titleTr = "Filtre bakım kontrolü",
      messageTr = "Filtre akışı azaldıysa temizlik yapın. Biyolojik filtre medyasını akvaryum suyunda nazikçe durulayın.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.MONTHLY,
      sourceTags = listOf("ADA", "Dennerle")
    ),

    SmartCareRule(
      id = "mature_co2_weekly_check",
      dayStart = 91,
      dayEnd = 36500,
      conditions = listOf(
        SmartCareCondition.HAS_CO2
      ),
      taskType = SmartCareTaskType.CO2_CHECK,
      titleTr = "CO₂ denge kontrolü",
      messageTr = "CO₂, ışık ve gübre dengesini kontrol edin. Canlılarda stres belirtisi varsa CO₂ ayarını gözden geçirin.",
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("CO2Art", "Chihiros", "Tropica")
    ),

    SmartCareRule(
      id = "mature_livestock_weekly_check",
      dayStart = 91,
      dayEnd = 36500,
      conditions = listOf(
        SmartCareCondition.HAS_LIVESTOCK
      ),
      taskType = SmartCareTaskType.LIVESTOCK_CHECK,
      titleTr = "Canlı sağlık kontrolü",
      messageTr = "Balık ve karideslerin davranışını, iştahını ve görünümünü kontrol edin.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("ADA", "Dennerle")
    )
  )

  val allRules: List<SmartCareRule>
    get() = startupRules + matureTankRules
}