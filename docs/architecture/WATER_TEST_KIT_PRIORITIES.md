# Named water-test products: implementation priorities

Manufacturer sources reviewed 27 September 2026 UTC; continuation recorded on
28 September. This closes **C.1 prioritization only**. No product is enabled,
method revision published, chemical conversion authorized or health threshold
added. S.6 and C.2–C.5 remain open.

## Coverage and order

The application mapping is our inference from manufacturer evidence and
`WaterTankMeasurementPolicy`, not manufacturer endorsement of AquaLight's
taxonomy. The four freshwater profiles are Freshwater Fish, Planted, Shrimp and
Other Freshwater. The four reef profiles are Soft Coral Reef, LPS Reef, SPS Reef
and Mixed Reef; Marine Fish and Other Marine complete the six marine profiles.
Neither brackish profile inherits assumed support from a freshwater/marine label.

Order reflects useful parameter coverage and implementation dependencies, not a
purchase recommendation. A broad kit is not several independently verified methods.

| Priority | Exact candidate | Manufacturer scope / application opportunity | Admission blocker |
| --- | --- | --- | --- |
| 1 | API **Freshwater Master Test Kit**, liquid kit, 1 CT | Freshwater pH, high-range pH, ammonia, nitrite and nitrate. pH/nitrite/nitrate intersect all four freshwater profiles; planted nitrite is additional. [API] | Reconcile exact booklet revision, scales, interferences and ammonia basis. Two pH ranges are not two analytes. Do not assume TAN-as-N or direct NH3 from the ammonia label; no invented SKU. |
| 2 | Red Sea **Foundation Pro Multi Test Kit**, Ca / KH / Mg titration set | Calcium, magnesium and alkalinity; all three are recommended for the four reef profiles. Marine-fish extension requires method/matrix verification. [RED] | Current manual, endpoints, range and ppm basis. Do not relabel marine alkalinity as freshwater carbonate hardness. Preserve independent results from the set. |
| 3 | JBL **PROAQUATEST GH**, complete test, Great Britain item **2410817** | Freshwater general hardness; recommended in all four freshwater profiles. [JBL-P], [JBL-M] | Bind displayed market variant and exact method revision; do not substitute a refill or another market's item number. A small first titration candidate after broad field coverage. |
| 4 | Hanna **HI782 Marine Nitrate High Range Checker HC** | Explicit seawater method; nitrate is recommended in all six marine profiles. [HAN-P], [HAN-M] | Digital reagent method, not an assigned continuous sensor. Preserve source ppm-as-NO3 until normalization is verified. Reject freshwater and unverified brackish use. |
| 5 | sera **GH Test**, **15 ml**, item **04110** | Freshwater product listing, total hardness; alternate GH option for four freshwater profiles. [SERA] | Confirm current instructions, matrix and scale. Capacity is not analytical range or precision. Generic water-test marketing is not proof of marine support. |

The initial candidates do not cover freshwater phosphate/potassium/iron,
salinity/SG, EC/TDS, dissolved oxygen, chlorine or verified brackish methods.
These remain gaps, not implied capabilities of any brand.

## Primary source register

All sources were retrieved **2026-09-27 UTC**. A crawl timestamp, copyright year,
safety-sheet revision or upload folder is not a method publication date.
Product-page publication dates were not established. Frozen source evidence and
exact revision binding remain C.2 work.

- **[API]** [Manufacturer product page](https://www.apifishcare.com/products/api-freshwater-master-test-kit),
  title, available size and measured-parameters FAQ. The description says six
  parameters, while the enumerated FAQ lists five including two pH ranges.
  Reconcile the discrepancy with the booklet; do not invent another analyte.
  The page does not establish the booklet revision or ammonia reporting basis.
- **[RED]** [Manufacturer product page](https://redseafish.com/reef-care-program/foundation-elements/foundation-pro-multi-test-kit/),
  overview and Kit Details. Its reported accuracy and infinity-range presentation
  require reconciliation with current instructions; infinity is not a validated
  numeric range. The linked
  [manual support route](https://g1.redseafish.com/support/product-support/products/foundation-pro/tabs/files/manuals/)
  is a discovery endpoint, not a frozen method document.
- **[JBL-P]** [Manufacturer product page](https://www.jbl.de/en-gb/productsv2/detail/25211277),
  item number, freshwater features and instructions link. Different sample
  volumes in the FAQ require separate verified scale/mode treatment.
- **[JBL-M]** [Manufacturer-linked GH instructions](https://www.jbl.de/en-gb/productsv2/download_instruction_manual_pdf/25009467),
  English Information for use, PDF page 2. Freshwater, 5 ml sample and a
  one-drop/one-degree German general-hardness scale are explicit. No exact
  publication date/revision was established from the English section. General
  husbandry advice does not become a livestock threshold.
- **[HAN-P]** [Manufacturer HI782 page](https://hannainst.com/marine-nitrate-high-range-checker-hc-hi782-r.html),
  Specifications and sample matrix. Range 0.0–75.0 ppm as NO3, resolution 0.1 ppm,
  accuracy ±2.0 ppm ±5% of reading at 25 °C. Resolution and accuracy remain
  distinct; range alone does not prove suitability for every reef target.
- **[HAN-M]** [Manufacturer-linked HI782 manual](https://cdn2.hubspot.net/hubfs/2134380/product-manuals/IST782_05_21.pdf),
  printed **IST782 05/21**, pages 1–2; specifications, seawater method and nitrite
  interference. Retain the printed month/year without inventing a publication
  day. Check for superseding revisions before publishing a profile.
- **[SERA]** [Manufacturer product page](https://www.sera.de/en/product/freshwater-aquarium/sera-gh-test-1/),
  identity and 15 ml / 04110 table. The linked evaluation-card PDF was not
  accepted as complete instruction/revision evidence. Capacity does not establish
  analytical precision.

## Existing Salifert prerequisite

The named Salifert Nitrate UI entry remains source-native. The
[manufacturer nitrate overview](https://www.salifert.com/pt/na.htm), retrieved
the same date, mentions nitrate-nitrogen and nitrate-ion scales, but does not
bind the installed kit's exact variant, current manual, matrix, result mode or
precision. Do not infer them from retailer listings or treat both scale labels
as concurrent measurements. S.6 remains open.

## Next implementation boundary

C.2 must bind exact product/market variant, immutable method revision, matrix,
reporting species/basis, source unit, scale, precision, detection-limit states,
concurrent outputs versus exclusive modes and interferences. A research candidate
does not become a `WaterMethodProfile` merely because the schema exists.

C.3 verifies source vectors and rejection of unknown revisions, unsupported
matrices and unjustified conversions. C.4/C.5 cover localized presentation,
catalog publication, preference reselection and unchanged historical provenance.
No manufacturer dosing recommendation or chemistry constant was added here.
