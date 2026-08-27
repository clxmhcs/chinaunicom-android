package com.clxmhcs.chinaunicom.core.model

enum class ServiceHallCategory(val wireValue: String, val displayName: String) {
    SELF_OPERATED("zyt", "自营厅"),
    PARTNER("hzt", "合作厅"),
}

data class ServiceHallCoordinate(
    val longitude: Double,
    val latitude: Double,
)

data class ServiceHallCity(
    val id: String,
    val cityCode: String,
    val cityName: String,
    val provinceCode: String,
    val provinceName: String,
    val longitude: Double?,
    val latitude: Double?,
    val sortLetters: String,
)

data class ServiceHallListItem(
    val id: String,
    val epID: String,
    val name: String,
    val category: ServiceHallCategory,
    val provinceCode: String,
    val cityCode: String,
    val provinceName: String,
    val cityName: String,
    val districtName: String,
    val address: String,
    val longitude: Double?,
    val latitude: Double?,
    val distanceMeters: Double?,
    val businessHours: String,
    val businessStatus: String,
    val ratingText: String,
    val imageURL: String?,
    val labels: List<String>,
    val detailURL: String?,
    val supportsAppointment: Boolean?,
    val appointmentURL: String?,
)

data class AppointmentTicketSlot(
    val id: String,
    val day: String,
    val startTime: String,
    val endTime: String,
    val remainingCount: Int?,
    val isAvailable: Boolean,
) {
    val timeText: String get() = "$startTime-$endTime"
}

data class AppointmentTicketAvailabilityResult(
    val slots: List<AppointmentTicketSlot>,
    val businesses: List<String>,
    val orderDescription: String?,
    val appointmentCredentials: AccountCredentials,
    val updatedCredentials: AccountCredentials?,
)

data class AppointmentTicketSubmissionResult(
    val appointmentID: String?,
    val message: String,
    val updatedCredentials: AccountCredentials?,
)

enum class ServiceHallActionKind {
    MY_APPOINTMENTS,
    APPOINTMENT_TICKET,
}

data class ServiceHallAction(
    val id: String,
    val kind: ServiceHallActionKind,
    val title: String,
    val iconURL: String?,
    val destinationURL: String,
    val loginFlag: String,
    val sortOrder: Int,
)

data class ServiceHallOverview(
    val category: ServiceHallCategory,
    val pageIndex: Int,
    val halls: List<ServiceHallListItem>,
    val actions: List<ServiceHallAction>,
)

data class ServiceHallFetchResult(
    val overview: ServiceHallOverview,
    val updatedCredentials: AccountCredentials?,
)

data class ServiceHallCityFetchResult(
    val cities: List<ServiceHallCity>,
    val updatedCredentials: AccountCredentials?,
)
