package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.engine.ForecastVerificationEngine
import com.example.domain.engine.VerificationBenchmarkRegion
import com.example.domain.model.AlertProvenance
import com.example.domain.model.AlertSeverity
import com.example.domain.model.ChatMessage
import com.example.domain.model.WeatherAlert
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.VerificationBarChart
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldClear
import com.example.ui.theme.LavenderMist
import com.example.ui.theme.SolarAmber
import com.example.viewmodel.WeatherUiState
import java.util.Locale

enum class IntelSubTab(val label: String) {
    ASSISTANT("AI Assistant"),
    ALERTS("Alerts"),
    VERIFICATION("Accuracy Lab")
}

@Composable
fun IntelligenceScreen(
    uiState: WeatherUiState,
    initialSubTab: IntelSubTab = IntelSubTab.ASSISTANT,
    onAskAssistant: (String) -> Unit,
    onClearChat: () -> Unit,
    onSelectBenchmark: (VerificationBenchmarkRegion) -> Unit,
    onVerifyActiveLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialSubTab) { mutableStateOf(initialSubTab) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == IntelSubTab.VERIFICATION && uiState.verificationReport == null && !uiState.isVerifying) {
            onSelectBenchmark(uiState.selectedBenchmark)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                IntelSubTab.entries.forEachIndexed { idx, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(idx, IntelSubTab.entries.size),
                        modifier = Modifier.testTag("intel_tab_${tab.name.lowercase()}")
                    ) {
                        val badge = if (tab == IntelSubTab.ALERTS && (uiState.weatherBundle?.alerts?.size ?: 0) > 0) {
                            " (${uiState.weatherBundle?.alerts?.size})"
                        } else ""
                        Text("${tab.label}$badge", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }

            when (selectedTab) {
                IntelSubTab.ASSISTANT -> AssistantSubScreen(
                    uiState = uiState,
                    onAsk = onAskAssistant,
                    onClear = onClearChat,
                    modifier = Modifier.weight(1f)
                )
                IntelSubTab.ALERTS -> AlertsSubScreen(
                    alerts = uiState.weatherBundle?.alerts.orEmpty(),
                    locationName = uiState.activeLocation?.name ?: "Active Location",
                    modifier = Modifier.weight(1f)
                )
                IntelSubTab.VERIFICATION -> VerificationLabSubScreen(
                    uiState = uiState,
                    onSelectBenchmark = onSelectBenchmark,
                    onVerifyActiveLocation = onVerifyActiveLocation,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AssistantSubScreen(
    uiState: WeatherUiState,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val quickPrompts = remember {
        listOf(
            "Will it rain in the next 2 hours?",
            "What is the best time to travel today?",
            "Should I carry an umbrella?",
            "Is there thunderstorm potential tonight?",
            "What is the expected temperature at 8 PM?",
            "Which of my saved locations has the best weather today?"
        )
    }

    LaunchedEffect(uiState.chatMessages.size, uiState.chatMessages.lastOrNull()?.text?.length) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header banner
        GlassCard(cornerRadius = 20.dp, contentPadding = 12.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_assistant_orb_1795075421932),
                    contentDescription = "Aether AI Weather Orb",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aether Meteorological AI",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Grounded in live ${uiState.activeLocation?.name ?: "local"} telemetry · Gemini 3.8 Flash",
                        style = MaterialTheme.typography.bodySmall,
                        color = AuroraCyan
                    )
                }
                if (uiState.chatMessages.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear conversation",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Chat stream
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (uiState.chatMessages.isEmpty()) {
                item {
                    GlassCard(cornerRadius = 20.dp, contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = SolarAmber, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Ask a live weather intelligence question",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Every answer queries live hourly, daily, ensemble, AQI, and alert data for your active and saved locations, citing exact timestamps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(uiState.chatMessages, key = { it.id }) { msg ->
                val lastUserQuestion = remember(uiState.chatMessages) {
                    uiState.chatMessages.lastOrNull { it.isUser }?.text
                }
                ChatBubbleItem(
                    msg = msg,
                    onRetry = if (msg.isError && !lastUserQuestion.isNullOrBlank() && !uiState.isAssistantThinking) {
                        { onAsk(lastUserQuestion) }
                    } else null
                )
            }
        }

        // Quick meteorological suggestion chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickPrompts) { prompt ->
                FilterChip(
                    selected = false,
                    onClick = { onAsk(prompt) },
                    enabled = !uiState.isAssistantThinking,
                    label = { Text(prompt, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        val submitCurrentInput = {
            if (inputText.isNotBlank() && !uiState.isAssistantThinking) {
                val query = inputText.trim()
                inputText = ""
                onAsk(query)
            }
        }

        // Message composer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Ask about rain, travel timing, storms…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitCurrentInput() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = AuroraCyan,
                    focusedBorderColor = AuroraCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("assistant_input_field")
            )

            Surface(
                color = AuroraCyan,
                contentColor = Color(0xFF06111F),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = submitCurrentInput,
                    enabled = inputText.isNotBlank() && !uiState.isAssistantThinking,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_send_button")
                ) {
                    if (uiState.isAssistantThinking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF06111F))
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send weather question")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    msg: ChatMessage,
    onRetry: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = when {
                msg.isError -> CoralAlert.copy(alpha = 0.20f)
                msg.isUser -> AuroraCyan.copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.10f)
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (msg.isUser) 18.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (msg.text.isBlank() && msg.isStreaming) "Analyzing live forecast telemetry…" else msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.isError) CoralAlert else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (msg.isUser) "You" else "Aether AI"} · ${msg.timestampLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onRetry != null) {
                        Button(
                            onClick = onRetry,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("assistant_retry_button")
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsSubScreen(
    alerts: List<WeatherAlert>,
    locationName: String,
    modifier: Modifier = Modifier
) {
    val official = alerts.filter { it.provenance == AlertProvenance.OFFICIAL_AGENCY }
    val modelRisks = alerts.filter { it.provenance == AlertProvenance.MODEL_RISK_INDICATOR }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            GlassCard(cornerRadius = 20.dp, contentPadding = 14.dp) {
                GlassSectionHeader(
                    title = "Official Agency Warnings",
                    icon = Icons.Rounded.Shield,
                    subtitle = "Prioritizing IMD (India) & NOAA NWS (US) feeds for $locationName"
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (official.isEmpty()) {
                    Text(
                        text = "No active official agency severe weather bulletins matched for $locationName right now. Official government warnings are never fabricated from model outputs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(official, key = { it.id }) { alert ->
            AlertDetailCard(alert = alert)
        }

        item {
            GlassCard(cornerRadius = 20.dp, contentPadding = 14.dp) {
                GlassSectionHeader(
                    title = "Model-Derived Risk Indicators",
                    icon = Icons.Rounded.WarningAmber,
                    subtitle = "Automated meteorological thresholds (CAPE, rainfall, heat, wind, fog, UV)"
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (modelRisks.isEmpty()) {
                    Text(
                        text = "No elevated NWP risk thresholds triggered in the next 24 hours for $locationName.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldClear
                    )
                }
            }
        }

        items(modelRisks, key = { it.id }) { alert ->
            AlertDetailCard(alert = alert)
        }
    }
}

@Composable
private fun AlertDetailCard(alert: WeatherAlert) {
    val accent = when (alert.severity) {
        AlertSeverity.EXTREME -> CoralAlert
        AlertSeverity.SEVERE -> Color(0xFFFB923C)
        AlertSeverity.MODERATE -> SolarAmber
        AlertSeverity.MINOR -> AuroraCyan
    }

    GlassCard(highlightTint = accent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = "${alert.severity.label.uppercase()} · ${alert.eventType.uppercase()}",
                containerColor = accent.copy(alpha = 0.22f),
                contentColor = accent
            )
            StatusPill(
                text = if (alert.provenance == AlertProvenance.OFFICIAL_AGENCY) "OFFICIAL" else "MODEL RISK",
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = alert.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Authority: ${alert.issuingAuthority}",
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
        Text(
            text = "Area: ${alert.affectedArea} · Valid: ${alert.validFrom.take(16)} → ${alert.validUntil.take(16)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = alert.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (alert.precautions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("RECOMMENDED PRECAUTIONS", style = MaterialTheme.typography.labelSmall, color = AuroraCyan)
            alert.precautions.forEach { p ->
                Text("• $p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VerificationLabSubScreen(
    uiState: WeatherUiState,
    onSelectBenchmark: (VerificationBenchmarkRegion) -> Unit,
    onVerifyActiveLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                GlassSectionHeader(
                    title = "Empirical Forecast Verification Pipeline",
                    icon = Icons.Rounded.Science,
                    subtitle = "10-day archived forecasts vs ERA5 observations (MAE, RMSE, Bias, POD, FAR, CSI, Brier)"
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ForecastVerificationEngine.indiaAndGlobalBenchmarks) { region ->
                        FilterChip(
                            selected = uiState.selectedBenchmark.id == region.id,
                            onClick = { onSelectBenchmark(region) },
                            label = { Text(region.name.substringBefore(","), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuroraCyan.copy(alpha = 0.25f),
                                selectedLabelColor = AuroraCyan
                            ),
                            modifier = Modifier.testTag("verify_region_${region.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onVerifyActiveLocation,
                    enabled = !uiState.isVerifying,
                    modifier = Modifier.testTag("verify_active_city_button")
                ) {
                    Text("Verify Active City (${uiState.activeLocation?.name ?: "Current"})")
                }
            }
        }

        if (uiState.isVerifying) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AuroraCyan)
                        Text(
                            text = "Running live empirical verification for ${uiState.selectedBenchmark.name}…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (uiState.verificationError != null) {
            item {
                GlassCard(highlightTint = CoralAlert, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uiState.verificationError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoralAlert
                    )
                }
            }
        }

        uiState.verificationReport?.let { report ->
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(report.regionName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(report.regionCategory, style = MaterialTheme.typography.bodySmall, color = AuroraCyan)
                        }
                        StatusPill(
                            text = "${report.evaluationWindowDays}d Window",
                            containerColor = EmeraldClear.copy(alpha = 0.18f),
                            contentColor = EmeraldClear
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Period: ${report.periodStartIso} → ${report.periodEndIso} · ${report.referenceSource}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    VerificationBarChart(models = report.models)
                }
            }

            items(report.models, key = { it.modelId }) { m ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.modelName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${m.sampleHours}h pairs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricMini("MAE", String.format(Locale.US, "%.2f°C", m.tempMaeC), AuroraCyan)
                        MetricMini("RMSE", String.format(Locale.US, "%.2f°C", m.tempRmseC), LavenderMist)
                        MetricMini("Bias", String.format(Locale.US, "%+.2f°C", m.tempBiasC), SolarAmber)
                        MetricMini("Precip Acc", String.format(Locale.US, "%.0f%%", m.precipAccuracyPct), EmeraldClear)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricMini("POD", String.format(Locale.US, "%.2f", m.pod), MaterialTheme.colorScheme.onSurface)
                        MetricMini("FAR", String.format(Locale.US, "%.2f", m.far), MaterialTheme.colorScheme.onSurface)
                        MetricMini("CSI", String.format(Locale.US, "%.2f", m.csi), MaterialTheme.colorScheme.onSurface)
                        MetricMini("Brier", String.format(Locale.US, "%.3f", m.brierScore), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            item {
                GlassCard(highlightTint = SolarAmber, modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, tint = SolarAmber, modifier = Modifier.size(16.dp))
                        Text("WeatherNext 3 Verification Transparency", style = MaterialTheme.typography.labelMedium, color = SolarAmber)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report.weatherNext3Note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricMini(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
