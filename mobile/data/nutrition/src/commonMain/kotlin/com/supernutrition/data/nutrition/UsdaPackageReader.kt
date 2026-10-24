package com.supernutrition.data.nutrition

import com.supernutrition.domain.nutrition.CatalogProduct
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot
import com.supernutrition.domain.nutrition.PartialNutrientProfile

/** Platform SHA-256 verifies integrity; authenticity belongs to the enclosing application's signature. */
internal fun readVerifiedUsdaPackage(
    candidate: UsdaPackagePayload,
    sha256: (ByteArray) -> String,
): List<CatalogProduct> {
    require(candidate.snapshot.identity == usdaCatalogSnapshot().identity) { "Unsupported USDA release" }
    require(candidate.snapshot.catalogCountry == "US") { "Unsupported USDA catalog country" }
    require(candidate.foodCount in 1..MAX_FOOD_COUNT) { "Invalid USDA package size" }
    require(candidate.payload.length <= MAX_PACKAGE_LENGTH) { "USDA package exceeds its size limit" }
    require(sha256(candidate.payload.encodeToByteArray()) == candidate.snapshot.contentDigest) {
        "USDA package checksum mismatch"
    }
    require(candidate.payload.endsWith('\n') && '\r' !in candidate.payload) {
        "USDA package is not canonical LF-delimited data"
    }
    val ids = mutableSetOf<String>()
    val products = candidate.payload.dropLast(1).split('\n').map { row ->
        val product = readUsdaProduct(row, candidate.snapshot)
        require(ids.add(product.canonicalItemId)) { "Duplicate USDA product" }
        product
    }
    require(products.size == candidate.foodCount) { "USDA package row count mismatch" }
    return products
}

private fun readUsdaProduct(row: String, snapshot: NutritionCatalogSnapshot): CatalogProduct {
    val fields = row.split('\t')
    require(fields.size == FIELD_COUNT) { "Malformed USDA product" }
    require(fields[0].startsWith(ID_PREFIX) && fields[0].length == ID_PREFIX.length + NDB_LENGTH) {
        "Invalid USDA canonical ID"
    }
    require(fields[0].drop(ID_PREFIX.length).all { it in '0'..'9' }) { "Invalid USDA NDB number" }
    require(
        fields[1].isNotBlank() && fields[1].length <= MAX_NAME_LENGTH &&
            fields[1].none(Char::isISOControl),
    ) { "Invalid USDA product name" }
    val values = fields.drop(2).map { value ->
        if (value.isEmpty()) null else requireNotNull(value.toDoubleOrNull()) { "Invalid USDA nutrient" }
    }
    return CatalogProduct(
        snapshot,
        fields[0],
        fields[1],
        PartialNutrientProfile(values[0], values[1], values[3], values[2], values[4], values[5], values[6]),
    )
}

private const val FIELD_COUNT = 9
private const val ID_PREFIX = "food:usda-srl-"
private const val NDB_LENGTH = 5
private const val MAX_NAME_LENGTH = 160
private const val MAX_FOOD_COUNT = 10_000
private const val MAX_PACKAGE_LENGTH = 2 * 1024 * 1024
