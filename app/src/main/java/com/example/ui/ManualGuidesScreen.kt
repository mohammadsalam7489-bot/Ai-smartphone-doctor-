package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.knowledge.KnowledgeCoverageAuditor
import com.example.data.knowledge.KnowledgeIssueRecord
import com.example.data.knowledge.KnowledgeRepository
import com.example.data.knowledge.PlaybookRagRetriever
import com.example.data.model.OemCompatibilityProfile
import com.example.ui.components.LanguageToggleSwitch
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalizedStrings
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualGuidesScreen(
    viewModel: DoctorViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        KnowledgeRepository.ensureLoadedSynchronously(context)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryNumber by remember { mutableStateOf<Int?>(null) }
    var showAuditPanel by remember { mutableStateOf(false) }

    val oemProfile = remember { DeviceCompatibilityEngine.getProfile(context) }
    val allCategories by KnowledgeRepository.categories.collectAsStateWithLifecycle()
    val allIssues by KnowledgeRepository.issues.collectAsStateWithLifecycle()
    val vmAuditState = viewModel?.coverageAudit?.collectAsStateWithLifecycle()
    val vmImportState = viewModel?.lastImportResult?.collectAsStateWithLifecycle()
    val currentLanguage = viewModel?.currentLanguage?.collectAsStateWithLifecycle()?.value ?: "English"
    val strings = AppStrings.forLanguage(currentLanguage)
    val isHindi = currentLanguage.equals("Hindi", ignoreCase = true)

    val audit = remember(allIssues.size, vmAuditState?.value) {
        vmAuditState?.value ?: KnowledgeCoverageAuditor.generateLiveAudit(context)
    }

    val listState = rememberLazyListState()

    // Play Policy compliant OpenDocument launcher (zero broad storage permissions)
    val updateFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && viewModel != null) {
            viewModel.importKnowledgeUpdate(context, uri)
        }
    }

    val filteredIssues by remember(searchQuery, selectedCategoryNumber, allIssues) {
        derivedStateOf {
            val catFiltered = if (selectedCategoryNumber == null) {
                allIssues
            } else {
                allIssues.filter { it.categoryNumber == selectedCategoryNumber }
            }
            if (searchQuery.isBlank()) {
                catFiltered
            } else {
                val q = searchQuery.trim()
                catFiltered.filter { issue ->
                    issue.problemId.contains(q, ignoreCase = true) ||
                        issue.canonicalProblemName.contains(q, ignoreCase = true) ||
                        issue.categoryName.contains(q, ignoreCase = true) ||
                        issue.possibleCauses.any { it.contains(q, ignoreCase = true) } ||
                        issue.solutions.any { it.contains(q, ignoreCase = true) }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CyberBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.doctor_badge_transparent),
                            contentDescription = "Smartphone Doctor Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, CyberCyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = strings.guidesTitle,
                                color = CyberTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = strings.guidesCountLabel(allCategories.size, allIssues.size),
                                fontSize = 9.sp,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                actions = {
                    if (viewModel != null) {
                        LanguageToggleSwitch(
                            currentLanguage = currentLanguage,
                            onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // OEM & Android Version Compatibility Card + Live Audit Toggle
            item(key = "audit_card") {
                Surface(
                    color = CyberCard,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAuditPanel = !showAuditPanel }
                        .testTag("kb_audit_card")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${oemProfile.oemName.uppercase()} • ${oemProfile.coverageTier.code}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (showAuditPanel) strings.guidesHideAudit else strings.guidesAuditImport,
                                fontSize = 9.sp,
                                color = CyberGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val verifiedSuffix = when {
                            isHindi && oemProfile.isPathVerifiedForBuild -> "ओईएम नेविगेशन सत्यापित।"
                            isHindi -> "सेटिंग्स खोज फ़ॉलबैक सक्रिय।"
                            oemProfile.isPathVerifiedForBuild -> "OEM navigation verified."
                            else -> "Settings Search fallback active."
                        }
                        Text(
                            text = "${oemProfile.androidCompatibility.versionLabel} • $verifiedSuffix",
                            fontSize = 10.sp,
                            color = CyberTextSecondary
                        )

                        AnimatedVisibility(visible = showAuditPanel) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                HorizontalDivider(color = CyberCardBorder)
                                Text(
                                    text = if (isHindi) "लाइव कवरेज ऑडिट रिपोर्ट:" else "LIVE COVERAGE AUDIT REPORT:",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTeal,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isHindi) {
                                        "• श्रेणियां: ${audit.totalCategories}/29 | अद्वितीय समस्या आईडी: ${audit.uniqueProblemIds}/466\n" +
                                            "• सत्यापन स्थिति: ${audit.verificationStatusBreakdown.entries.joinToString { "${it.key}=${it.value}" }}"
                                    } else {
                                        "• Categories: ${audit.totalCategories}/29 | Unique Issue IDs: ${audit.uniqueProblemIds}/466\n" +
                                            "• Verification Statuses: ${audit.verificationStatusBreakdown.entries.joinToString { "${it.key}=${it.value}" }}"
                                    },
                                    fontSize = 10.sp,
                                    color = CyberTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )

                                vmImportState?.value?.let { res ->
                                    Text(
                                        text = res.summaryMessage,
                                        fontSize = 10.sp,
                                        color = if (res.success) CyberGreen else CyberRed
                                    )
                                }

                                if (viewModel != null) {
                                    Button(
                                        onClick = {
                                            updateFileLauncher.launch(arrayOf("application/json", "text/plain"))
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberSurfaceVariant,
                                            contentColor = CyberCyan
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("import_kb_update_button")
                                    ) {
                                        Icon(
                                            Icons.Default.UploadFile,
                                            contentDescription = strings.guidesImportBtn,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.guidesImportBtn,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar across all 466 issues
            item(key = "search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            strings.guidesSearchPlaceholder,
                            color = CyberTextMuted,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = strings.guidesSearchPlaceholder,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guides_search_field"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = CyberTextPrimary,
                        unfocusedTextColor = CyberTextPrimary,
                        cursorColor = CyberCyan
                    )
                )
            }

            // Horizontal Category Filter Chips across all 29 Categories
            item(key = "category_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryNumber == null,
                        onClick = { selectedCategoryNumber = null },
                        label = {
                            Text(
                                strings.guidesAllChip(allIssues.size),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            labelColor = CyberTextSecondary
                        )
                    )
                    allCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryNumber == cat.categoryNumber,
                            onClick = {
                                selectedCategoryNumber = if (selectedCategoryNumber == cat.categoryNumber) null else cat.categoryNumber
                            },
                            label = {
                                Text(
                                    "${cat.categoryNumber}. ${cat.categoryName.substringBefore(" Problems")}",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan,
                                labelColor = CyberTextSecondary
                            )
                        )
                    }
                }
            }

            item(key = "results_count") {
                Text(
                    text = strings.guidesShowingResults(filteredIssues.size),
                    fontSize = 10.sp,
                    color = CyberTextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            items(
                items = filteredIssues,
                key = { it.problemId }
            ) { issue ->
                KnowledgeIssueCard(issue = issue, oemProfile = oemProfile, strings = strings)
            }
        }
    }
}

@Composable
fun KnowledgeIssueCard(
    issue: KnowledgeIssueRecord,
    oemProfile: OemCompatibilityProfile,
    strings: LocalizedStrings = AppStrings.English
) {
    var isExpanded by remember(issue.problemId) { mutableStateOf(false) }
    val pathResolution = remember(issue.oemSettingsKey, oemProfile) {
        DeviceCompatibilityEngine.resolveSettingsPath(issue.oemSettingsKey, oemProfile)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("kb_issue_card_${issue.problemId}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = CyberSurfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
                    ) {
                        Text(
                            text = issue.problemId,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = CyberSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
                    ) {
                        Text(
                            text = "${issue.severity}/${issue.riskTier}",
                            color = CyberTeal,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) strings.hideDetails else strings.viewEvidence,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = issue.canonicalProblemName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextPrimary
            )

            Text(
                text = "${issue.categoryNumber}. ${issue.categoryName}",
                fontSize = 10.sp,
                color = CyberTextSecondary
            )

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = CyberCardBorder)

                    PlaybookSectionTitle("${strings.guidesOemPath} (${oemProfile.oemName.uppercase()})", CyberCyan)
                    Text(
                        text = pathResolution.resolvedPath,
                        fontSize = 10.5.sp,
                        color = CyberGreen,
                        fontFamily = FontFamily.Monospace
                    )

                    if (issue.diagnosticQuestions.isNotEmpty()) {
                        PlaybookSectionTitle(strings.guidesClarifyingQuestions, CyberTeal)
                        issue.diagnosticQuestions.forEach { q ->
                            Text(text = "? $q", fontSize = 10.5.sp, color = CyberTextPrimary)
                        }
                    }

                    PlaybookSectionTitle(strings.guidesPossibleCauses, CyberAmber)
                    issue.possibleCauses.forEach { cause ->
                        Text(text = "• $cause", fontSize = 10.5.sp, color = CyberTextSecondary)
                    }

                    PlaybookSectionTitle(strings.guidesSafeWorkflow, CyberCyan)
                    issue.solutions.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(CyberSurfaceVariant)
                                    .border(1.dp, CyberCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = CyberCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = step,
                                color = CyberTextPrimary,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    PlaybookSectionTitle(strings.guidesVerification, CyberGreen)
                    Text(
                        text = issue.verification.joinToString(" "),
                        fontSize = 10.5.sp,
                        color = CyberTextPrimary
                    )

                    PlaybookSectionTitle(strings.guidesHardwareEscalation, CyberRed)
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = CyberRed,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = issue.escalation.joinToString(" "),
                            fontSize = 10.5.sp,
                            color = CyberTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybookSectionTitle(title: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = title,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp
    )
}
