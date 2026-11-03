package app.ranger.domain

/** Where it hurts: the places runners usually mean. */
object Body {
    val parts: List<Pair<String, L>> = listOf(
        "blister" to L("натёрло", "blister"),
        "foot" to L("стопа", "foot"),
        "heel" to L("пятка", "heel"),
        "ankle" to L("голеностоп", "ankle"),
        "achilles" to L("ахилл", "Achilles"),
        "calf" to L("икра", "calf"),
        "shin" to L("голень", "shin"),
        "knee" to L("колено", "knee"),
        "thigh" to L("бедро", "thigh"),
        "hip" to L("таз", "hip"),
        "back" to L("спина", "back"),
        "side" to L("бок", "side stitch"),
    )
    val byKey = parts.toMap()
}
