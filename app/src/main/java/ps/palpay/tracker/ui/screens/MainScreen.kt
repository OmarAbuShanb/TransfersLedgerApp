package ps.palpay.tracker.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ps.palpay.tracker.R
import ps.palpay.tracker.core.IntentUtils
import ps.palpay.tracker.core.PhoneNumberUtils
import ps.palpay.tracker.core.TimeUtils
import ps.palpay.tracker.data.local.db.TransactionEntity
import ps.palpay.tracker.ui.theme.*
import ps.palpay.tracker.ui.viewmodel.MainViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onNavigateToSettings: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title), fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (uiState.isReady) {
                if (!uiState.batteryOptimizationIgnored) {
                    item { BatteryOptimizationCard(context) }
                }
                if (!uiState.listenerConnected) {
                    item { ListenerStatusCard(context) }
                }
            }

            item {
                SummaryDashboard(uiState)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.last_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_transactions), color = Color.Gray)
                    }
                }
            } else {
                items(transactions) { transaction ->
                    TransactionItem(transaction, context)
                }
            }
        }
    }
}

@Composable
fun SummaryDashboard(state: ps.palpay.tracker.domain.model.AppStatus) {
    val configuration = LocalConfiguration.current
    val locale = TimeUtils.getLocale(configuration)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.today_summary),
                        color = Color.White.copy(0.85f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        TimeUtils.getArabicTransactionPlural(state.totalCount, locale),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.Bottom) {
                    val amountStr = String.format(locale, "%.2f", state.totalAmount)
                    Text(
                        TimeUtils.formatNumerals(amountStr, locale),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.currency_symbol),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WalletMiniCard(
                label = stringResource(R.string.palpay_label),
                amount = state.palpayTotal,
                count = state.palpayCount,
                modifier = Modifier.weight(1f),
                color = PalPayBrandColor,
                locale = locale
            )
            WalletMiniCard(
                label = stringResource(R.string.jawwalpay_label),
                amount = state.jawwalPayTotal,
                count = state.jawwalPayCount,
                modifier = Modifier.weight(1f),
                color = JawwalGreen,
                locale = locale
            )
        }
    }
}

@Composable
fun WalletMiniCard(label: String, amount: Double, count: Int, modifier: Modifier, color: Color, locale: Locale) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Black, color = color, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                val amountStr = String.format(locale, "%.1f", amount)
                Text(
                    TimeUtils.formatNumerals(amountStr, locale),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.currency_symbol),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
            Text(
                TimeUtils.getArabicTransactionPlural(count, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun TransactionItem(transaction: TransactionEntity, context: Context) {
    val configuration = LocalConfiguration.current
    val locale = TimeUtils.getLocale(configuration)
    val circleColor = if (transaction.walletSource == "PalPay") PalPayBrandColor else JawwalGreen
    
    val isPhone = PhoneNumberUtils.isPhoneNumber(transaction.senderName)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = circleColor,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if(transaction.walletSource == "PalPay") "ب" else "ج",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimeUtils.formatNumerals(transaction.senderName, locale), 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (isPhone) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${transaction.senderName}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = JawwalGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = TimeUtils.getRelativeTimeArabic(transaction.timestamp, locale),
                    fontSize = 11.sp,
                    color = circleColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = TimeUtils.getFullDateTimeArabic(transaction.timestamp, locale),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val amountStr = String.format(locale, "%.2f", transaction.amount)
                Text(
                    "${stringResource(R.string.currency_symbol)}${TimeUtils.formatNumerals(amountStr, locale)}",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp
                )
            }
        }
    }
}

@Composable
fun BatteryOptimizationCard(context: Context) {
    AlertCard(
        title = stringResource(R.string.battery_alert_title),
        desc = stringResource(R.string.battery_alert_desc),
        btnText = stringResource(R.string.battery_allow_btn),
        icon = Icons.Default.BatteryAlert,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onClick = { context.startActivity(IntentUtils.getBatteryOptimizationIntent(context)) }
    )
}

@Composable
fun ListenerStatusCard(context: Context) {
    AlertCard(
        title = stringResource(R.string.notif_alert_title),
        desc = stringResource(R.string.notif_alert_desc),
        btnText = stringResource(R.string.activate_permission_btn),
        icon = Icons.Default.NotificationsActive,
        containerColor = SoftWarning,
        contentColor = Color.Black,
        onClick = { context.startActivity(IntentUtils.getNotificationListenerSettingsIntent(context)) }
    )
}

@Composable
fun AlertCard(title: String, desc: String, btnText: String, icon: ImageVector, containerColor: Color, contentColor: Color, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc, 
                style = MaterialTheme.typography.bodySmall, 
                color = contentColor.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = contentColor, contentColor = containerColor),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(btnText, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}
