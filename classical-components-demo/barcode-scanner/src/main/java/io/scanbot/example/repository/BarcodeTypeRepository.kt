package io.scanbot.example.repository

import io.scanbot.sdk.barcode.entity.BarcodeFormat

object BarcodeTypeRepository {
    val selectedTypes = mutableSetOf<BarcodeFormat>().also {
        it.addAll(BarcodeFormat.values())
    }

    fun selectType(type: BarcodeFormat) {
        selectedTypes.add(type)
    }

    fun deselectType(type: BarcodeFormat) {
        selectedTypes.remove(type)
    }

}