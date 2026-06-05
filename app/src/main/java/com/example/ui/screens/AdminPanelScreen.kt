@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.viewmodel.AppViewModel

@Composable
fun AdminPanelScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val currentUser by viewModel.currentUser.collectAsState()
    
    // Strict admin access check
    if (currentUser?.email?.lowercase()?.trim() != "sharvanipatel.ias@gmail.com") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.GppBad, contentDescription = "Security Alert", tint = Color.Red, modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("ACCESS DENIED / ప్రవేశం నిరాకరించబడినది", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text("This administrative console is restricted exclusively to authorized security personnel (sharvanipatel.ias@gmail.com).", color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))) {
                    Text("Return to Secure Workspace", color = Color.Black)
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("🛡️ ADMIN CONTROL SYSTEM", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B), titleContentColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E293B)) {
                NavigationBarItem(
                    selected = viewModel.adminActiveSubTab == "DASHBOARD",
                    onClick = { viewModel.adminActiveSubTab = "DASHBOARD" },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Telemetry", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFF59E0B), unselectedIconColor = Color.LightGray)
                )
                NavigationBarItem(
                    selected = viewModel.adminActiveSubTab == "QUEUE",
                    onClick = { viewModel.adminActiveSubTab = "QUEUE" },
                    icon = { Icon(Icons.Default.Rule, contentDescription = "Queue") },
                    label = { Text("Moderation", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFF59E0B), unselectedIconColor = Color.LightGray)
                )
                NavigationBarItem(
                    selected = viewModel.adminActiveSubTab == "USERS",
                    onClick = { viewModel.adminActiveSubTab = "USERS" },
                    icon = { Icon(Icons.Default.People, contentDescription = "Users") },
                    label = { Text("Users", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFF59E0B), unselectedIconColor = Color.LightGray)
                )
                NavigationBarItem(
                    selected = viewModel.adminActiveSubTab == "ADS",
                    onClick = { viewModel.adminActiveSubTab = "ADS" },
                    icon = { Icon(Icons.Default.AdsClick, contentDescription = "Ads") },
                    label = { Text("Ad Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFF59E0B), unselectedIconColor = Color.LightGray)
                )
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (viewModel.adminActiveSubTab) {
                "DASHBOARD" -> AdminDashboardTab(viewModel)
                "QUEUE" -> AdminModerationQueueTab(viewModel)
                "USERS" -> AdminUserManagementTab(viewModel)
                "ADS" -> AdminAdManagementTab(viewModel)
            }
        }
    }
}

// 1. DASHBOARD TAB
@Composable
fun AdminDashboardTab(viewModel: AppViewModel) {
    val newsList by viewModel.newsPosts.collectAsState()
    val novelsList by viewModel.novelPosts.collectAsState()
    val thoughtsList by viewModel.thoughtPosts.collectAsState()
    val usersList by viewModel.allUsers.collectAsState()

    val pendingNews = newsList.count { it.status == PostStatus.PENDING_REVIEW || it.status == PostStatus.AWAITING_MODERATION }
    val pendingNovels = novelsList.count { it.status == PostStatus.PENDING_REVIEW || it.status == PostStatus.AWAITING_MODERATION }
    val pendingThoughts = thoughtsList.count { it.status == PostStatus.PENDING_REVIEW || it.status == PostStatus.AWAITING_MODERATION }
    val blockedUsers = usersList.count { it.isBlocked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("SYSTEM TELEMETRY SUMMARY", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(bottom = 14.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryMetricCard("Total Users", "${usersList.size}", Icons.Default.SupervisedUserCircle, Color(0xFF3B82F6), modifier = Modifier.weight(1f))
            TelemetryMetricCard("Blocked Accounts", "$blockedUsers", Icons.Default.Block, Color(0xFFEF4444), modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryMetricCard("News Articles", "${newsList.size}", Icons.Default.Article, Color(0xFFF59E0B), modifier = Modifier.weight(1f))
            TelemetryMetricCard("Novel Chapters", "${novelsList.size}", Icons.Default.Book, Color(0xFF10B981), modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Moderation highlights Alert Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🚨 AWAITING APPROVAL DECISIONS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("News pending approval:")
                    Text("$pendingNews posts", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Novels pending check:")
                    Text("$pendingNovels drafts", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Thoughts pending verification:")
                    Text("$pendingThoughts entries", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                }
            }
        }
    }
}

@Composable
fun TelemetryMetricCard(title: String, scoreStr: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(scoreStr, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(title, fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
        }
    }
}

// 2. MODERATION QUEUE
@Composable
fun AdminModerationQueueTab(viewModel: AppViewModel) {
    val newsList by viewModel.newsPosts.collectAsState()
    val novelsList by viewModel.novelPosts.collectAsState()
    val thoughtsList by viewModel.thoughtPosts.collectAsState()

    // Aggregate posts that are NOT Published yet (or flagged as Hiding / Review required)
    val reviewItems = remember(newsList, novelsList, thoughtsList) {
        val list = mutableListOf<ModerationItem>()
        newsList.forEach { if (it.status != PostStatus.PUBLISHED) list.add(ModerationItem.NewsItem(it)) }
        novelsList.forEach { if (it.status != PostStatus.PUBLISHED) list.add(ModerationItem.NovelItem(it)) }
        thoughtsList.forEach { if (it.status != PostStatus.PUBLISHED) list.add(ModerationItem.ThoughtItem(it)) }
        list.sortByDescending { it.timestamp }
        list
    }

    if (reviewItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Clean queue", tint = Color(0xFF10B981), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("మోడరేషన్ క్యూ ఖాళీగా ఉంది!", color = Color(0xFF94A3B8), fontSize = 15.sp)
                Text("All posts have been reviewed.", color = Color(0xFF64748B), fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            items(reviewItems) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF334155))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(item.typeLabel, color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Safety Flag: " + (if (item.status == PostStatus.HIDDEN) "❌ BLOCKED" else "⚠️ PENDING"),
                                color = if (item.status == PostStatus.HIDDEN) Color.Red else Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(item.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Text(item.content, fontSize = 13.sp, color = Color(0xFFCBD5E1), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 4.dp))
                        Text("Author: ${item.authorName}", fontSize = 11.sp, color = Color(0xFF94A3B8))

                        if (item.moderationNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "AI Moderation Note: ${item.moderationNotes}",
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFF334155))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Approve Button
                            Button(
                                onClick = { viewModel.approvePost(item.id, item.postType) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("APPROVE", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            // Reject Button
                            Button(
                                onClick = { viewModel.rejectPost(item.id, item.postType) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("REJECT", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            // Delete Action
                            IconButton(onClick = { viewModel.adminDeletePost(item.id, item.postType) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class ModerationItem {
    abstract val id: String
    abstract val title: String
    abstract val content: String
    abstract val authorName: String
    abstract val timestamp: Long
    abstract val status: PostStatus
    abstract val typeLabel: String
    abstract val postType: PostType
    abstract val moderationNotes: String

    class NewsItem(post: NewsPost) : ModerationItem() {
        override val id = post.id
        override val title = post.title
        override val content = post.description
        override val authorName = post.authorName
        override val timestamp = post.timestamp
        override val status = post.status
        override val typeLabel = "NEWS"
        override val postType = PostType.NEWS
        override val moderationNotes = post.moderationNotes
    }

    class NovelItem(post: NovelPost) : ModerationItem() {
        override val id = post.id
        override val title = post.title
        override val content = post.content
        override val authorName = post.authorName
        override val timestamp = post.timestamp
        override val status = post.status
        override val typeLabel = "NOVEL"
        override val postType = PostType.NOVEL
        override val moderationNotes = post.moderationNotes
    }

    class ThoughtItem(post: ThoughtPost) : ModerationItem() {
        override val id = post.id
        override val title = post.title
        override val content = post.content
        override val authorName = post.authorName
        override val timestamp = post.timestamp
        override val status = post.status
        override val typeLabel = "THOUGHT"
        override val postType = PostType.THOUGHT
        override val moderationNotes = post.moderationNotes
    }
}

// 3. USER MANAGEMENT
@Composable
fun AdminUserManagementTab(viewModel: AppViewModel) {
    val usersList by viewModel.allUsers.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("MANAGE USERS / ఖాతాల నియంత్రణ", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(usersList) { u ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(u.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Email: ${u.email.ifEmpty { "None Linked" }}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text("Phone: ${u.phoneNumber.ifEmpty { "None Linked" }}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        // Block toggler
                        Button(
                            onClick = { viewModel.blockUser(u.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (u.isBlocked) Color(0xFF10B981) else Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (u.isBlocked) "UNBLOCK" else "BLOCK", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 4. AD MANAGEMENT
@Composable
fun AdminAdManagementTab(viewModel: AppViewModel) {
    val activeAdSettings by viewModel.adSettings.collectAsState()
    
    var testModeConfig by remember { mutableStateOf(activeAdSettings.useTestAds) }
    var nativeModeConfig by remember { mutableStateOf(activeAdSettings.showNativeAds) }
    var adsEnabledConfig by remember { mutableStateOf(activeAdSettings.adsEnabled) }
    var nativeAdIdConfig by remember { mutableStateOf(activeAdSettings.nativeAdId) }
    var bannerAdIdConfig by remember { mutableStateOf(activeAdSettings.bannerAdId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("GOOGLE ADMOB SYSTEM UTILITY", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(bottom = 16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 1. Global Ads State
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Global Ads Enabled (ads_enabled)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text("Turn OFF to instantly disable all advertisement blocks across all feeds.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = adsEnabledConfig, onCheckedChange = { adsEnabledConfig = it }, modifier = Modifier.testTag("ads_enabled_switch"))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Switch to Production Ads config
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Sandbox AdMob Test Units", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text("When ON, AdMob test banners and test native layouts are used to prevent invalid click violations.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = testModeConfig, onCheckedChange = { testModeConfig = it }, modifier = Modifier.testTag("test_ads_switch"))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                // 3. Show Native Ads In Feeds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Native Ads In Feeds", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text("Display inline AdMob sponsored assets inside regular post scroll lists.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = nativeModeConfig, onCheckedChange = { nativeModeConfig = it }, modifier = Modifier.testTag("show_native_switch"))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(24.dp))

                // 4. Production IDs Config
                Text("PRODUCTION AD UNIT CONFIGURATION", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B), fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

                Text("Production Banner Ad ID", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = bannerAdIdConfig,
                    onValueChange = { bannerAdIdConfig = it },
                    placeholder = { Text("ca-app-pub-...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("banner_ad_id_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Production Native Ad ID", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = nativeAdIdConfig,
                    onValueChange = { nativeAdIdConfig = it },
                    placeholder = { Text("ca-app-pub-...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("native_ad_id_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.saveAdManagementSettings(
                            useTest = testModeConfig,
                            showNative = nativeModeConfig,
                            adsEnabled = adsEnabledConfig,
                            nativeAdId = nativeAdIdConfig.trim(),
                            bannerAdId = bannerAdIdConfig.trim()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth().testTag("save_ad_settings_btn")
                ) {
                    Text("SAVE AD SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
