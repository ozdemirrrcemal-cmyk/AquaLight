from pathlib import Path

path = Path(
    "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/"
    "AquariumDimensionInputPolicy.kt"
)
text = path.read_text(encoding="utf-8")
old = """    private fun canonicalCentimeters(value: Double, unit: String): Int? {
        if (!value.isFinite()) return null
        return toCentimeters(value, unit)
            .roundToInt()
            .takeIf(AquariumMeasurementPolicy::isValidDimensionCm)
    }
"""
new = """    private fun canonicalCentimeters(value: Double, unit: String): Int? {
        if (!value.isFinite()) return null
        val centimeters = toCentimeters(value, unit)
        if (centimeters <= 0.0 ||
            centimeters > AquariumMeasurementPolicy.MAX_DIMENSION_CM.toDouble()
        ) {
            return null
        }
        return centimeters.roundToInt()
            .takeIf(AquariumMeasurementPolicy::isValidDimensionCm)
    }
"""
if text.count(old) != 1:
    raise SystemExit("AquariumDimensionInputPolicy canonical method did not match exactly once")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
