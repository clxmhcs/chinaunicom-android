package com.clxmhcs.chinaunicom.ui.navigation

enum class RootTab(
    val route: String,
    val label: String,
    val glyph: String,
) {
    Flow(route = "flow", label = "流量", glyph = "▥"),
    Voice(route = "voice", label = "语音", glyph = "☎"),
    Comprehensive(route = "comprehensive", label = "综合业务", glyph = "▦"),
    OtherBusiness(route = "other-business", label = "其它业务", glyph = "☷"),
    Settings(route = "settings", label = "设置", glyph = "⚙"),
}
