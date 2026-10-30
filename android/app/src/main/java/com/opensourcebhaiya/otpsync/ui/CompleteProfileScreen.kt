package com.opensourcebhaiya.otpsync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opensourcebhaiya.otpsync.data.AppLanguage
import com.opensourcebhaiya.otpsync.data.AppStrings
import com.opensourcebhaiya.otpsync.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(
    settingsViewModel: SettingsViewModel,
    onContinue: () -> Unit
) {
    val currentLang by settingsViewModel.appLanguage.collectAsState()
    val initialName = remember { settingsViewModel.settingsManager.getManualUserName() ?: "" }

    var fullName by remember { mutableStateOf(initialName) }
    var expandedLanguageDropdown by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar circle matching the exact design language of Onboarding screen (120dp with primary alpha)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = AppStrings.get("profile_title", currentLang),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = AppStrings.get("profile_subtitle", currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Card container for Full Name input
            Card(
                modifier = Modifier.fillMaxWidth(0.95f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(AppStrings.get("full_name_label", currentLang)) },
                        placeholder = { Text(AppStrings.get("full_name_placeholder", currentLang)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF2E344A),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language Selector Card with exact same UI as Settings page
            Surface(
                modifier = Modifier.fillMaxWidth(0.95f),
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = AppStrings.get("change_app_language_title", currentLang),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = AppStrings.get("change_app_language_sub", currentLang),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E2333),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { expandedLanguageDropdown = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentLang.displayName,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Language",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = expandedLanguageDropdown,
                                onDismissRequest = { expandedLanguageDropdown = false },
                                modifier = Modifier
                                    .widthIn(min = 170.dp, max = 240.dp)
                                    .heightIn(max = 280.dp)
                                    .background(Color(0xFF161925))
                            ) {
                                AppLanguage.entries.forEach { lang ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = lang.displayName,
                                                    color = if (lang == currentLang) PurpleAccent else TextPrimary,
                                                    fontWeight = if (lang == currentLang) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = lang.nativeName,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            settingsViewModel.setAppLanguage(lang)
                                            expandedLanguageDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button styled identically to Onboarding action buttons
            Button(
                onClick = {
                    val trimmedName = fullName.trim()
                    if (trimmedName.isNotEmpty()) {
                        settingsViewModel.setManualUserName(trimmedName)
                    }
                    onContinue()
                },
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = AppStrings.get("continue_btn", currentLang),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
