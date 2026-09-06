package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LoginRed = Color(0xFFFF3B30)
private val LoginSecondary = Color(0xFF8E8E93)
private val LoginTertiary = Color(0xFFC7C7CC)
private val LoginCardBackground = Color(0xFFF2F2F7)
private val LoginOuterBackground = Color(0xFFD5D7DC)

@Composable
internal fun SMSCredentialLoginIosPage(
    onboarding: AccountOnboardingUiState,
    mobile: String,
    onMobileChange: (String) -> Unit,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
    onClearStatus: () -> Unit,
    onBack: () -> Unit,
) {
    var agreesToTerms by remember { mutableStateOf(true) }
    val isBusy = onboarding.isSendingCode || onboarding.isLoggingIn || onboarding.isImporting
    val canSendCode = mobile.length == 11 && !isBusy
    val canSubmit = mobile.length == 11 && verificationCode.length == 6 && agreesToTerms && !isBusy

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginOuterBackground),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoginSheetHeader(onBack = onBack, enabled = !isBusy)
                Spacer(Modifier.height(74.dp))
                LoginHeroBubble()
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "验证码登录",
                    color = Color.Black,
                    fontSize = 25.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "使用中国联通手机号和短信验证码登录",
                    color = LoginSecondary,
                    fontSize = 15.5.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(30.dp))
                LoginModeTabs()
                Spacer(Modifier.height(34.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LoginCardBackground,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        LoginInputRow(
                            kind = LoginInputKind.PHONE,
                            value = mobile,
                            placeholder = "请输入联通手机号",
                            onValueChange = onMobileChange,
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp)
                                .height(1.dp)
                                .background(Color(0xFFD8D8DC)),
                        )
                        LoginInputRow(
                            kind = LoginInputKind.MESSAGE,
                            value = verificationCode,
                            placeholder = "请输入短信验证码",
                            onValueChange = onVerificationCodeChange,
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                            trailing = {
                                TextButton(
                                    onClick = onSendCode,
                                    enabled = canSendCode,
                                ) {
                                    if (onboarding.isSendingCode) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(17.dp),
                                            color = LoginRed,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text(
                                            text = "获取验证码",
                                            color = if (canSendCode) LoginRed else LoginTertiary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            },
                        )

                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoginCheckCircle(
                                checked = agreesToTerms,
                                onClick = { agreesToTerms = !agreesToTerms },
                            )
                            Text(
                                text = "我已阅读并同意使用短信验证码进行本次登录。验证码仅用于本次请求，不会保存。",
                                modifier = Modifier.weight(1f),
                                color = LoginSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        }

                        Spacer(Modifier.height(26.dp))
                        Button(
                            onClick = onLogin,
                            enabled = canSubmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LoginRed,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFD1D1D6),
                                disabledContentColor = Color.White.copy(alpha = 0.72f),
                            ),
                        ) {
                            if (onboarding.isLoggingIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = if (onboarding.isLoggingIn) "正在登录" else "登录",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(34.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LoginShieldIcon()
                    Text(
                        text = "验证码登录成功后仅保存 Cookie、appId 和 token_online。\n短信验证码只用于本次登录，不会保存。",
                        modifier = Modifier.weight(1f),
                        color = LoginSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    if (!onboarding.statusTitle.isNullOrBlank() || !onboarding.statusMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onClearStatus,
            title = { Text(onboarding.statusTitle.orEmpty().ifBlank { "提示" }) },
            text = { Text(onboarding.statusMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = onClearStatus) { Text("好") }
            },
        )
    }
}

@Composable
private fun LoginSheetHeader(onBack: () -> Unit, enabled: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(66.dp)
                .height(44.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF8F8FA),
            shadowElevation = 1.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = enabled, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    color = Color(0xFF3D6CF4),
                    fontSize = 17.sp,
                )
            }
        }
        Text(
            text = "登录",
            modifier = Modifier.align(Alignment.Center),
            color = Color.Black,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoginHeroBubble() {
    Canvas(modifier = Modifier.size(68.dp)) {
        val bubble = Path().apply {
            moveTo(size.width * .20f, size.height * .20f)
            cubicTo(size.width * .36f, size.height * .04f, size.width * .75f, size.height * .05f, size.width * .85f, size.height * .25f)
            cubicTo(size.width * .94f, size.height * .43f, size.width * .88f, size.height * .70f, size.width * .67f, size.height * .79f)
            cubicTo(size.width * .51f, size.height * .86f, size.width * .34f, size.height * .81f, size.width * .24f, size.height * .75f)
            cubicTo(size.width * .18f, size.height * .82f, size.width * .12f, size.height * .85f, size.width * .08f, size.height * .82f)
            cubicTo(size.width * .14f, size.height * .72f, size.width * .14f, size.height * .65f, size.width * .10f, size.height * .58f)
            cubicTo(size.width * .02f, size.height * .43f, size.width * .07f, size.height * .30f, size.width * .20f, size.height * .20f)
            close()
        }
        drawPath(bubble, LoginRed)
        drawCircle(
            color = LoginRed,
            radius = size.width * .16f,
            center = Offset(size.width * .79f, size.height * .18f),
        )
        drawCircle(
            color = Color.White,
            radius = size.width * .19f,
            center = Offset(size.width * .79f, size.height * .18f),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun LoginModeTabs() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "验证码登录",
                color = Color.Black,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LoginRed),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "密码登录",
                color = LoginSecondary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.width(64.dp).height(4.dp))
        }
    }
}

private enum class LoginInputKind { PHONE, MESSAGE }

@Composable
private fun LoginInputRow(
    kind: LoginInputKind,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoginInputIcon(kind)
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            cursorBrush = SolidColor(LoginRed),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = LoginTertiary,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    inner()
                }
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
private fun LoginInputIcon(kind: LoginInputKind) {
    Canvas(modifier = Modifier.size(28.dp)) {
        when (kind) {
            LoginInputKind.PHONE -> {
                drawRoundRect(
                    color = LoginRed,
                    topLeft = Offset(size.width * .30f, size.height * .10f),
                    size = androidx.compose.ui.geometry.Size(size.width * .40f, size.height * .78f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.8.dp.toPx()),
                )
                drawLine(
                    color = LoginRed,
                    start = Offset(size.width * .42f, size.height * .78f),
                    end = Offset(size.width * .58f, size.height * .78f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            LoginInputKind.MESSAGE -> {
                drawOval(
                    color = LoginRed,
                    topLeft = Offset(size.width * .12f, size.height * .22f),
                    size = androidx.compose.ui.geometry.Size(size.width * .70f, size.height * .48f),
                )
                val tail = Path().apply {
                    moveTo(size.width * .26f, size.height * .62f)
                    lineTo(size.width * .13f, size.height * .82f)
                    lineTo(size.width * .38f, size.height * .70f)
                    close()
                }
                drawPath(tail, LoginRed)
            }
        }
    }
}

@Composable
private fun LoginCheckCircle(checked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) LoginRed else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                text = "✓",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(LoginSecondary, style = Stroke(width = 1.6.dp.toPx()))
            }
        }
    }
}

@Composable
private fun LoginShieldIcon() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val shield = Path().apply {
            moveTo(size.width * .50f, size.height * .05f)
            lineTo(size.width * .82f, size.height * .18f)
            lineTo(size.width * .82f, size.height * .55f)
            cubicTo(size.width * .82f, size.height * .76f, size.width * .66f, size.height * .90f, size.width * .50f, size.height * .96f)
            cubicTo(size.width * .34f, size.height * .90f, size.width * .18f, size.height * .76f, size.width * .18f, size.height * .55f)
            lineTo(size.width * .18f, size.height * .18f)
            close()
        }
        drawPath(shield, LoginSecondary)
        drawCircle(Color.White, radius = size.width * .08f, center = Offset(size.width * .50f, size.height * .47f))
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * .43f, size.height * .50f),
            size = androidx.compose.ui.geometry.Size(size.width * .14f, size.height * .20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
        )
    }
}
