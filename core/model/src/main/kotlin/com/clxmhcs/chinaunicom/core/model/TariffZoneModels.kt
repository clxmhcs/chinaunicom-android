package com.clxmhcs.chinaunicom.core.model

enum class TariffZoneScope(val rawValue: String) {
    NATIONAL("1"),
    LOCAL("2");
}

data class TariffZoneRegion(
    val provinceCode: String,
    val cityCode: String,
    val provinceName: String,
    val cityName: String,
) {
    val id: String get() = "$provinceCode|$cityCode"
}

data class TariffZoneRegionGroup(
    val id: String,
    val provinceName: String,
    val regions: List<TariffZoneRegion>,
)

data class TariffZoneSecondLevel(
    val id: String,
    val name: String,
)

data class TariffZoneFirstLevel(
    val id: String,
    val name: String,
    val secondLevels: List<TariffZoneSecondLevel>,
) {
    companion object {
        val FALLBACK: List<TariffZoneFirstLevel> = listOf(
            TariffZoneFirstLevel("1", "套餐", listOf(
                TariffZoneSecondLevel("1001", "移网"),
                TariffZoneSecondLevel("1002", "宽带"),
                TariffZoneSecondLevel("1003", "固话"),
                TariffZoneSecondLevel("1004", "融合"),
            )),
            TariffZoneFirstLevel("2", "加装包", listOf(
                TariffZoneSecondLevel("2001", "流量包"),
                TariffZoneSecondLevel("2002", "短信包"),
                TariffZoneSecondLevel("2003", "语音包"),
                TariffZoneSecondLevel("2004", "权益包"),
                TariffZoneSecondLevel("2005", "新业务"),
                TariffZoneSecondLevel("2006", "其他"),
            )),
            TariffZoneFirstLevel("3", "营销活动", listOf(
                TariffZoneSecondLevel("3001", "促销"),
                TariffZoneSecondLevel("3002", "合约"),
            )),
            TariffZoneFirstLevel("4", "国际及港澳台资费", listOf(
                TariffZoneSecondLevel("4001", "国际/港澳台加装包"),
                TariffZoneSecondLevel("4002", "国际/港澳台移网套餐"),
                TariffZoneSecondLevel("4003", "国际/港澳台融合套餐"),
                TariffZoneSecondLevel("4004", "国际/港澳台标准资费"),
            )),
            TariffZoneFirstLevel("5", "国内标准资费", listOf(
                TariffZoneSecondLevel("5001", "国内标准资费"),
            )),
            TariffZoneFirstLevel("99", "停售套餐", listOf(
                TariffZoneSecondLevel("1", "套餐"),
                TariffZoneSecondLevel("2", "加装包"),
                TariffZoneSecondLevel("3", "营销活动"),
                TariffZoneSecondLevel("4", "国际及港澳台资费"),
                TariffZoneSecondLevel("5", "国内标准资费"),
            )),
        )
    }
}

data class TariffZoneProductReference(
    val id: String,
    val name: String,
)

data class TariffZoneSearchResult(
    val reference: TariffZoneProductReference,
    val firstLevelID: String,
    val firstLevelName: String,
    val secondLevelID: String,
    val secondLevelName: String,
) {
    val id: String get() = "$firstLevelID|$secondLevelID|${reference.id}"
    val name: String get() = reference.name
}

data class TariffZoneDetail(
    val id: String,
    val reportNo: String,
    val name: String,
    val codeType: String,
    val feesStandard: String,
    val feeUnit: String,
    val otherFees: String,
    val extraFees: String,
    val minute: String,
    val commonData: String,
    val dataUnit: String,
    val sms: String,
    val orientTraffic: String,
    val orientTrafficUnit: String,
    val iptv: String,
    val broadBand: String,
    val equityCoupon: String,
    val serviceContent: String,
    val useScope: String,
    val validPeriod: String,
    val onlinePeriod: String,
    val saleChnl: String,
    val unsubscribe: String,
    val startDate: String,
    val endDate: String,
    val contractDuty: String,
    val otherDesc: String,
) {
    val standardFeeText: String
        get() {
            val fee = feesStandard.ifBlank { "--" }
            return if (feeUnit.isBlank()) fee else "$fee$feeUnit"
        }

    val commonDataText: String get() = "${commonData.ifBlank { "0" }}$dataUnit"
    val orientTrafficText: String get() = "${orientTraffic.ifBlank { "0" }}$orientTrafficUnit"
}

data class TariffZoneIndex(
    val regions: List<TariffZoneRegion>,
    val levels: List<TariffZoneFirstLevel>,
    val userProvinceCode: String,
    val userCityCode: String,
)

data class TariffZoneIndexFetchResult(
    val index: TariffZoneIndex,
    val updatedCredentials: AccountCredentials?,
)

data class TariffZoneReferencesFetchResult(
    val references: List<TariffZoneProductReference>,
    val updatedCredentials: AccountCredentials?,
)

data class TariffZoneDetailsFetchResult(
    val details: List<TariffZoneDetail>,
    val timeText: String?,
    val updatedCredentials: AccountCredentials?,
)
