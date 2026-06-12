package com.example.domonapperfect.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isAutoOpen: Boolean,
    onAutoOpenChange: (Boolean) -> Unit,
    isCallNotificationOnly: Boolean,
    onCallNotificationOnlyChange: (Boolean) -> Unit,
    isOpenButtonOnLeft: Boolean,
    onOpenButtonOnLeftChange: (Boolean) -> Unit,
    isRingtoneEnabled: Boolean,
    onRingtoneEnabledChange: (Boolean) -> Unit,
    isDoNotDisturbEnabled: Boolean,
    onDoNotDisturbEnabledChange: (Boolean) -> Unit,
    callWindowDelaySeconds: Int,
    onCallWindowDelayChange: (Int) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Профиль", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Вы вошли в систему", style = MaterialTheme.typography.bodyMedium)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Авто-Открытие", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Открывать дверь при звонке", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isAutoOpen,
                            onCheckedChange = onAutoOpenChange
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Звонки как уведомления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Не открывать на весь экран", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isCallNotificationOnly,
                            onCheckedChange = onCallNotificationOnlyChange
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Кнопка «Открыть» слева", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = isOpenButtonOnLeft,
                            onCheckedChange = onOpenButtonOnLeftChange
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Звук звонка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Проигрывать рингтон при вызове", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isRingtoneEnabled,
                            onCheckedChange = onRingtoneEnabledChange
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Не беспокоить", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("Игнорировать все звонки с домофона", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Switch(
                            checked = isDoNotDisturbEnabled,
                            onCheckedChange = onDoNotDisturbEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.error,
                                checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) {
                        Text("Окно просмотра после открытия (сек): $callWindowDelaySeconds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Не закрывать звонок сразу после открытия двери", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = callWindowDelaySeconds.toFloat(),
                            onValueChange = { onCallWindowDelayChange(it.toInt()) },
                            valueRange = 0f..15f,
                            steps = 14
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Выйти")
                        Spacer(Modifier.width(8.dp))
                        Text("Выйти из аккаунта")
                    }
                }
            }
        }
    }
}
