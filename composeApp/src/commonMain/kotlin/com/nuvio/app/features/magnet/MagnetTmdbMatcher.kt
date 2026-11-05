package com.nuvio.app.features.magnet

internal data class MagnetTmdbCandidate(
    val id: Int,
    val kind: MagnetMediaKind,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val popularity: Double,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?,
)

internal fun selectMagnetCandidate(
    hint: MagnetMediaHint,
    candidates: List<MagnetTmdbCandidate>,
): MagnetTmdbCandidate? = candidates
    .map { candidate -> candidate to scoreMagnetCandidate(hint, candidate) }
    .filter { (_, score) -> score >= MIN_CANDIDATE_SCORE }
    .maxByOrNull { (_, score) -> score }
    ?.first

internal fun scoreMagnetCandidate(
    hint: MagnetMediaHint,
    candidate: MagnetTmdbCandidate,
): Double {
    val titleScore = listOfNotNull(candidate.title, candidate.originalTitle)
        .maxOfOrNull { titleSimilarity(hint.title, it) }
        ?: 0.0
    val typeBonus = when {
        hint.isSeries && candidate.kind == MagnetMediaKind.SERIES -> 0.12
        !hint.isSeries && candidate.kind == MagnetMediaKind.MOVIE -> 0.08
        else -> 0.0
    }
    val yearScore = when {
        hint.year == null || candidate.year == null -> 0.0
        hint.year == candidate.year -> 0.2
        kotlin.math.abs(hint.year - candidate.year) == 1 -> 0.08
        else -> -0.18
    }
    val popularityBonus = candidate.popularity.coerceIn(0.0, MAX_POPULARITY_SCORE_WEIGHT) /
        MAX_POPULARITY_SCORE_WEIGHT * 0.03
    return titleScore * 0.8 + typeBonus + yearScore + popularityBonus
}

internal fun titleSimilarity(left: String, right: String): Double {
    val a = normalizeTitleKey(left)
    val b = normalizeTitleKey(right)
    if (a.isBlank() || b.isBlank()) return 0.0
    if (a == b) return 1.0
    val maxLength = maxOf(a.length, b.length)
    val distanceScore = 1.0 - levenshteinDistance(a, b).toDouble() / maxLength
    val containmentScore = if (a in b || b in a) {
        0.9 + 0.1 * minOf(a.length, b.length).toDouble() / maxLength
    } else {
        0.0
    }
    return maxOf(distanceScore, containmentScore).coerceIn(0.0, 1.0)
}

private fun normalizeTitleKey(value: String): String = value
    .lowercase()
    .replace(Regex("""[^a-z0-9]+"""), " ")
    .replace(Regex("""^(the|a|an) """), "")
    .trim()

private fun levenshteinDistance(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    for (leftIndex in left.indices) {
        current[0] = leftIndex + 1
        for (rightIndex in right.indices) {
            val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + substitutionCost,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}

private const val MIN_CANDIDATE_SCORE = 0.55
private const val MAX_POPULARITY_SCORE_WEIGHT = 1000.0
