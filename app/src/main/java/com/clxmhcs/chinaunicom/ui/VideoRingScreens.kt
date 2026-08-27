package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VideoRingMember
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** M9-H source-derived selector: enabled 11-digit mobile numbers beginning with 1 only. */
@Composable
fun VideoRingAccountSelectionScreen(
    accounts: List<UnicomAccount>,
    onBack: () -> Unit,
    onOpenAccount: (String) -> Unit,
) {
    val targets = accounts
        .filter { account ->
            val digits = account.mobile.filter(Char::isDigit)
            account.isEnabled && digits.length == 11 && digits.startsWith("1")
        }
        .sortedWith(compareBy<UnicomAccount> { it.sortOrder }.thenBy { it.mobile })

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("视频彩铃会员", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = {}, enabled = false) { Text("") }
        }

        if (targets.isEmpty()) {
            Surface(modifier = Modifier.padding(16.dp).fillMaxWidth(), tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("暂无手机号码", fontWeight = FontWeight.SemiBold)
                    Text("请先在设置中保存可用的联通手机号码凭据。", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text(
                "视频彩铃会员按手机号分别查询，请选择当前 App 已保存的手机号码。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(targets, key = { it.id }) { account ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenAccount(account.id.toString()) },
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(maskVideoRingMobile(account.mobile), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("使用该号码自己的登录凭据查询视频彩铃会员", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** M9-H rough functional member center. Final visual parity remains deferred. */
@Composable
fun VideoRingMemberCenterScreen(
    account: UnicomAccount,
    viewModel: VideoRingViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selectedMemberType by rememberSaveable(account.id) { mutableStateOf("15") }

    LaunchedEffect(account.id) {
        selectedMemberType = "15"
        viewModel.load(account)
    }

    val memberState = state.memberState.takeIf { state.accountID == account.id }
    val displayMembers = remember(memberState) { displayVideoRingMembers(memberState) }
    val selectedMember = displayMembers.firstOrNull { it.memberType == selectedMemberType } ?: displayMembers.first()

    LaunchedEffect(memberState) {
        val selectedIsActive = displayMembers.any { it.memberType == selectedMemberType && it.isMember }
        if (!selectedIsActive) {
            selectedMemberType = displayMembers.firstOrNull { it.memberType == "15" && it.isMember }?.memberType
                ?: displayMembers.firstOrNull(VideoRingMember::isMember)?.memberType
                ?: "15"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("视频彩铃会员", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { viewModel.refresh(account) }, enabled = !state.loading) { Text("刷新") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(maskVideoRingMobile(account.mobile), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(activeMemberText(memberState, state.loading), style = MaterialTheme.typography.bodyMedium)
                        state.lastRefreshTime?.let {
                            Text(
                                "${if (state.restoredFromCache) "缓存" else "刷新"}：${formatVideoRingTime(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.loading) item { Text("正在读取该号码会员信息…") }
            state.errorMessage?.let { message ->
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayMembers, key = { it.memberType }) { member ->
                        FilterChip(
                            selected = selectedMemberType == member.memberType,
                            onClick = { selectedMemberType = member.memberType },
                            label = { Text(member.name) },
                        )
                    }
                }
            }

            item {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selectedMember.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(videoRingMemberStatus(selectedMember), style = MaterialTheme.typography.titleMedium)
                        Text("我的会员管理", style = MaterialTheme.typography.bodyMedium)
                        VideoRingRights(selectedMember.memberType)
                    }
                }
            }

            if (selectedMember.memberType == "15") {
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("权益月月领", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("权益月月领已升级为沃券福利", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "当前 iOS 权益目录为 App 内置展示；本阶段只迁会员真实开通状态，不伪造领取、兑换或购买接口。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("更多独家会员特权", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("部分精选音频彩铃", style = MaterialTheme.typography.bodyMedium)
                            Text("部分精选音频彩铃免费设置", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRingRights(memberType: String) {
    val rights = when (memberType) {
        "87" -> listOf("爱奇艺全站影视", "影视IP彩铃", "精选短剧", "AI彩铃", "主叫彩铃", "会员活动")
        "15" -> listOf("权益月月领", "AI彩铃", "全站音频彩铃", "主叫彩铃", "视频名片", "通话速记", "通话字幕", "情景通话")
        "76" -> listOf("AI彩铃", "视频彩铃", "主叫彩铃", "会员专享", "通话字幕", "情景通话")
        else -> listOf("AI彩铃", "视频彩铃", "主叫彩铃", "会员专享")
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rights.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun displayVideoRingMembers(state: VideoRingMemberState?): List<VideoRingMember> {
    val defaults = listOf(
        VideoRingMember("87", "AI彩铃视听剧场会员", "87", false),
        VideoRingMember("15", "铂金会员", "15", false),
        VideoRingMember("76", "AI彩铃升级版", "76", false),
    )
    return defaults.map { fallback -> state?.members?.firstOrNull { it.memberType == fallback.memberType } ?: fallback }
}

private fun activeMemberText(state: VideoRingMemberState?, loading: Boolean): String {
    val names = displayVideoRingMembers(state).filter(VideoRingMember::isMember).map(VideoRingMember::name)
    return when {
        names.isNotEmpty() -> "您已开通 ${names.joinToString("、")}" 
        loading -> "正在查询当前号码的视频彩铃会员信息"
        else -> "当前号码未查询到已开通的视频彩铃会员"
    }
}

private fun videoRingMemberStatus(member: VideoRingMember): String {
    if (!member.isMember) return "未开通"
    val end = member.endTime?.trim().orEmpty()
    val start = member.startTime?.trim().orEmpty()
    return when {
        member.memberType == "87" && end.isNotEmpty() -> "有效期至：${videoRingMemberDate(end)}"
        start.isNotEmpty() -> "${videoRingMemberDate(start)} 开通"
        end.isNotEmpty() -> "有效期至：${videoRingMemberDate(end)}"
        else -> "已开通"
    }
}

private fun videoRingMemberDate(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.length < 8) return raw
    return "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
}

private fun maskVideoRingMobile(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length == 11) digits.take(3) + "****" + digits.takeLast(4) else value
}

private fun formatVideoRingTime(value: java.time.Instant): String = DateTimeFormatter
    .ofPattern("MM-dd HH:mm", Locale.CHINA)
    .withZone(ZoneId.systemDefault())
    .format(value)
