package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AiState
import com.example.viewmodel.DoctorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimatorScreen(
    viewModel: DoctorViewModel,
    onBack: () -> Unit
) {
    var issueInput by remember { mutableStateOf("Cracked AMOLED Screen Replacement") }
    var brandInput by remember { mutableStateOf("Samsung Galaxy / Google Pixel") }
    val repairEstimate by viewModel.repairEstimate.collectAsState()
    val aiState by viewModel.aiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repair Cost Estimator") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("estimator_back_btn")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Describe Phone Issue", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    OutlinedTextField(
                        value = issueInput,
                        onValueChange = { issueInput = it },
                        label = { Text("Issue / Damaged Component") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("issue_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = brandInput,
                        onValueChange = { brandInput = it },
                        label = { Text("Phone Brand / Model") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("brand_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = { viewModel.estimateRepair(issueInput, brandInput) },
                        enabled = aiState !is AiState.Loading && issueInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("estimate_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (aiState is AiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Calculating AI Estimate...")
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get AI Repair Estimate & Guide")
                        }
                    }
                }
            }

            repairEstimate?.let { estimate ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("estimate_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Estimate for ${estimate.issueName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Badge {
                                Text(estimate.difficulty)
                            }
                        }

                        Divider()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Estimated Cost: ${estimate.estimatedCost}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(text = "AI Repair Guide & Breakdown", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = estimate.diyGuide,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
