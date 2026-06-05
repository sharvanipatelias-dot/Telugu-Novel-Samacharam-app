package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.ThemeManager
import com.example.viewmodel.AppViewModel

@Composable
fun ProfileTabScreen(viewModel: AppViewModel) {
    val user by viewModel.currentUser.collectAsState()
    val allNews by viewModel.newsPosts.collectAsState()
    val allNovels by viewModel.novelPosts.collectAsState()
    val allThoughts by viewModel.thoughtPosts.collectAsState()
    val savedPostIds by viewModel.savedPostIds.collectAsState()

    // Filtered lists for My posts fed strictly from reactive Firestore collection queries
    val myNews by viewModel.currentUserNews.collectAsState()
    val myNovels by viewModel.currentUserNovels.collectAsState()
    val myThoughts by viewModel.currentUserThoughts.collectAsState()

    // Filtered lists for Saved posts
    val savedNews = allNews.filter { savedPostIds.contains(it.id) }
    val savedNovels = allNovels.filter { savedPostIds.contains(it.id) }
    val savedThoughts = allThoughts.filter { savedPostIds.contains(it.id) }

    var selectedProfileGroupIndex by remember { mutableStateOf(0) } // 0 for My Posts, 1 for Saved Posts
    var selectedProfileCategoryIndex by remember { mutableStateOf(0) } // 0 for News, 1 for Novels, 2 for Thoughts

    // Image picker from Gallery for Edit Profile
    val context = LocalContext.current
    var cropTargetUri by remember { mutableStateOf<String?>(null) }
    var isCropModeActive by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            cropTargetUri = uri.toString()
            isCropModeActive = true
        }
    }

    if (isCropModeActive && cropTargetUri != null) {
        // Render the high fidelity interactive crop workspace
        CropImageWidget(
            imageUri = cropTargetUri!!,
            onCropApplied = { croppedResultUrl ->
                // Enforce absolute persistence of local profile photos across sessions
                val persistentUri = try {
                    val uri = Uri.parse(croppedResultUrl)
                    val resolver = context.contentResolver
                    val inputStream = resolver.openInputStream(uri)
                    if (inputStream != null) {
                        val file = java.io.File(context.filesDir, "profile_avatar_${System.currentTimeMillis()}.jpg")
                        java.io.FileOutputStream(file).use { out ->
                            inputStream.use { input ->
                                input.copyTo(out)
                            }
                        }
                        Uri.fromFile(file).toString()
                    } else croppedResultUrl
                } catch (e: Exception) {
                    croppedResultUrl
                }

                viewModel.updateProfile(
                    name = user?.name ?: "",
                    bio = user?.bio ?: "",
                    photoUrl = persistentUri
                )
                isCropModeActive = false
                cropTargetUri = null
                viewModel.showEditProfileDialog = true // return to edit screen
            },
            onCancel = {
                isCropModeActive = false
                cropTargetUri = null
                viewModel.showEditProfileDialog = true // return to edit screen
            }
        )
    } else {
        // Main Scrollable Profile Layout
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("profile_screen_root")
                .background(Color(0xFF0F172A)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: User Card presentation
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Avatar
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = user?.profilePhotoUrl ?: "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFFF59E0B), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            if (user?.isAdmin == true) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFFFFD700), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = "Admin Indicator", tint = Color.Black, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // User Name (Only user name and photo displays in public profile!)
                        Text(
                            text = user?.name ?: "Telugu Writer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        if (user?.isAdmin == true) {
                            Text(
                                text = "🏆 SUPER ADMIN LEVEL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Beautifully styled Bio with a prominent layout and a 🖊️ emoji
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "🖊️ ",
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = user?.bio?.ifEmpty { "రచయిత - తెలుగు భాషాభిమాని." } ?: "రచయిత - తెలుగు భాషాభిమాని.",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Social count analytics badges with specific followers, following, and likes icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStatItem(
                                label = if (ThemeManager.currentLanguage == "TE") "ఫాలోవర్స్" else "Followers",
                                valStr = "${user?.followers?.size ?: 0}",
                                icon = Icons.Default.People
                            )
                            ProfileStatItem(
                                label = if (ThemeManager.currentLanguage == "TE") "ఫాలోయింగ్" else "Following",
                                valStr = "${user?.following?.size ?: 0}",
                                icon = Icons.Default.Person
                            )
                            val totalLikesCount = myNews.sumOf { it.likesCount } + myNovels.sumOf { it.likesCount } + myThoughts.sumOf { it.likesCount }
                            ProfileStatItem(
                                label = if (ThemeManager.currentLanguage == "TE") "మొత్తం ఇష్టాలు" else "Total Likes",
                                valStr = "$totalLikesCount",
                                icon = Icons.Default.Favorite
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trigger Edit Profile (Direct shortcut click option)
                        Button(
                            onClick = { viewModel.showEditProfileDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_shortcut_btn")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Edit Profile Info", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile / స్వీయ ప్రొఫైల్ సవరణ", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            // FCM Configuration & Engagement Simulation Deck
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "FCM Notification Controller",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (ThemeManager.currentLanguage == "TE") "నోటిఫికేషన్లు & ఎంగేజ్‌మెంట్ హబ్" else "FCM Push Notifications & Hub",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (ThemeManager.currentLanguage == "TE") 
                                "కొత్త కంటెంట్ అప్‌లోడ్‌లు, మీ పోస్ట్‌లపై లైక్‌లు మరియు కామెంట్‌ల కోసం నేరుగా నోటిఫికేషన్‌లను పొందడానికి పుష్ సర్వీస్‌లను కాన్ఫిగర్ చేయండి."
                            else 
                                "Configure cloud push notifications to receive real-time updates for new novel content uploads, and likes or comments on your public feed.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Retrieve / display registration token
                        val fcmPrefs = context.getSharedPreferences("telugu_fcm_prefs", android.content.Context.MODE_PRIVATE)
                        var fcmToken by remember { 
                            mutableStateOf(
                                fcmPrefs.getString("fcm_token", "").orEmpty().ifEmpty { 
                                    // Generate a stable simulated token if running locally off-grid
                                    "fcm_sim_token_" + (user?.id?.take(5) ?: "guest") + "_" + java.util.UUID.randomUUID().toString().take(12)
                                }
                            )
                        }

                        // Save fcm token to preferences if generated simulated token
                        LaunchedEffect(fcmToken) {
                            if (fcmPrefs.getString("fcm_token", "").isNullOrEmpty()) {
                                fcmPrefs.edit().putString("fcm_token", fcmToken).apply()
                            }
                        }

                        // Preferences switches
                        var pushEnabled by remember { mutableStateOf(fcmPrefs.getBoolean("push_enabled", true)) }
                        var engagementEnabled by remember { mutableStateOf(fcmPrefs.getBoolean("engagement_enabled", true)) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Content Uploads Push / అప్‌లోడ్ అలర్ట్స్", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                            Switch(
                                checked = pushEnabled,
                                onCheckedChange = { 
                                    pushEnabled = it
                                    fcmPrefs.edit().putBoolean("push_enabled", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF59E0B))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Engagement Alerts / లైక్స్ & కామెంట్స్", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                            Switch(
                                checked = engagementEnabled,
                                onCheckedChange = { 
                                    engagementEnabled = it
                                    fcmPrefs.edit().putBoolean("engagement_enabled", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF59E0B))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Visual Token Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("fcm_token", fcmToken)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "FCM Token copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("FCM REGISTRATION TOKEN (TAP TO COPY)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(fcmToken, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFCBD5E1))
                                }
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy FCM Token", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (ThemeManager.currentLanguage == "TE") "లైవ్ ఎంగేజ్‌మెంట్ సిమ్యులేటర్ (పుష్ నోటిఫికేషన్ టెస్ట్)" else "Live Engagement Notification Simulator",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Simulator for Likes engagement
                            Button(
                                onClick = {
                                    if (engagementEnabled) {
                                        com.example.services.NotificationHelper.showPushNotification(
                                            context = context,
                                            title = "Likes Engagement Update! 👍",
                                            message = "${user?.name ?: "Guest Reader"} loved and liked your recent publications feed. Keep creating!",
                                            postId = "news_sample_like",
                                            postType = "NEWS"
                                        )
                                    } else {
                                        android.widget.Toast.makeText(context, "Please enable Engagement Alerts in switches", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Simulate Like", fontSize = 11.sp, color = Color.White)
                            }

                            // Simulator for Comments engagement
                            Button(
                                onClick = {
                                    if (engagementEnabled) {
                                        com.example.services.NotificationHelper.showPushNotification(
                                            context = context,
                                            title = "New Community Comment! 💬",
                                            message = "Venkat commented: \"చాలా బాగుంది! అద్భుతమైన కంటెంట్. మరిన్ని అప్‌డేట్స్ ఆశిస్తున్నాము!\"",
                                            postId = "sample_comment_alert",
                                            postType = "NEWS"
                                        )
                                    } else {
                                        android.widget.Toast.makeText(context, "Please enable Engagement Alerts in switches", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Simulate Comment", fontSize = 11.sp, color = Color.White)
                            }

                            // Simulator for Uploads releases
                            Button(
                                onClick = {
                                    if (pushEnabled) {
                                        com.example.services.NotificationHelper.showPushNotification(
                                            context = context,
                                            title = "News Content Upload Alert! 📰",
                                            message = "New live updates uploaded regarding Andhra State development schemes. Open to read now.",
                                            postId = "news_sample_upload",
                                            postType = "NEWS"
                                        )
                                    } else {
                                        android.widget.Toast.makeText(context, "Please enable Content Uploads push option", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Simulate Upload", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Primary Group Switcher (Tier 1: My Posts / Saved Posts)
            item {
                TabRow(
                    selectedTabIndex = selectedProfileGroupIndex,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFFF59E0B),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedProfileGroupIndex == 0,
                        onClick = { selectedProfileGroupIndex = 0 },
                        modifier = Modifier.testTag("group_tab_my_posts")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "My Posts",
                                tint = if (selectedProfileGroupIndex == 0) Color(0xFFF59E0B) else Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (ThemeManager.currentLanguage == "TE") "నా పోస్ట్‌లు" else "My Posts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selectedProfileGroupIndex == 0) Color(0xFFF59E0B) else Color.LightGray
                            )
                        }
                    }
                    Tab(
                        selected = selectedProfileGroupIndex == 1,
                        onClick = { selectedProfileGroupIndex = 1 },
                        modifier = Modifier.testTag("group_tab_saved_posts")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Saved",
                                tint = if (selectedProfileGroupIndex == 1) Color(0xFFF59E0B) else Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (ThemeManager.currentLanguage == "TE") "భద్రపరిచినవి" else "Saved Posts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selectedProfileGroupIndex == 1) Color(0xFFF59E0B) else Color.LightGray
                            )
                        }
                    }
                }
            }

            // Category Selector (Tier 2: News / Novels / Thoughts)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categories = listOf(
                        Triple(if (ThemeManager.currentLanguage == "TE") "వార్తలు" else "News", Icons.Default.Article, "📰"),
                        Triple(if (ThemeManager.currentLanguage == "TE") "నవలలు" else "Novels", Icons.Default.Book, "📚"),
                        Triple(if (ThemeManager.currentLanguage == "TE") "భావాలు" else "Thoughts", Icons.Default.Create, "💭")
                    )
                    
                    categories.forEachIndexed { index, (label, icon, emoji) ->
                        val isSelected = selectedProfileCategoryIndex == index
                        val containerColor = if (isSelected) Color(0xFFF59E0B) else Color(0xFF1E293B)
                        val contentColor = if (isSelected) Color.Black else Color.LightGray
                        
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("category_subtab_$index")
                                .clickable { selectedProfileCategoryIndex = index },
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }

            // Collection layout container title
            item {
                val groupLabel = if (selectedProfileGroupIndex == 0) {
                    if (ThemeManager.currentLanguage == "TE") "నా" else "My"
                } else {
                    if (ThemeManager.currentLanguage == "TE") "భద్రపరిచిన" else "Saved"
                }
                val categoryLabel = when (selectedProfileCategoryIndex) {
                    0 -> if (ThemeManager.currentLanguage == "TE") "వార్తలు" else "News"
                    1 -> if (ThemeManager.currentLanguage == "TE") "నవలలు" else "Novels"
                    else -> if (ThemeManager.currentLanguage == "TE") "భావాలు" else "Thoughts"
                }
                Text(
                    text = "📂 $groupLabel $categoryLabel:",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = Color(0xFFF59E0B)
                )
            }

            // Switch according to selected group and category index
            when (selectedProfileGroupIndex to selectedProfileCategoryIndex) {
                0 to 0 -> { // My News
                    if (myNews.isEmpty()) {
                        item { EmptyStateMessage("మీరు ఇప్పటివరకు ఏ సమాచారాన్ని పోస్ట్ చేయలేదు.") }
                    } else {
                        items(myNews) { item ->
                            ProfileNewsCardItem(post = item, viewModel = viewModel)
                        }
                    }
                }
                0 to 1 -> { // My Novels
                    if (myNovels.isEmpty()) {
                        item { EmptyStateMessage("మీరు ఏ నవల ధారావాహికలను పోస్ట్ చేయలేదు.") }
                    } else {
                        items(myNovels) { item ->
                            ProfileNovelCardItem(post = item, viewModel = viewModel)
                        }
                    }
                }
                0 to 2 -> { // My Thoughts
                    if (myThoughts.isEmpty()) {
                        item { EmptyStateMessage("మీ ఆలోచనలను లేదా జ్ఞాపకాలను పంచుకోండి.") }
                    } else {
                        items(myThoughts) { item ->
                            ProfileThoughtCardItem(post = item, viewModel = viewModel)
                        }
                    }
                }
                1 to 0 -> { // Saved News
                    if (savedNews.isEmpty()) {
                        item { EmptyStateMessage("భద్రపరుచుకున్న వార్తా కథనాలు ఏవీ లేవు.") }
                    } else {
                        items(savedNews) { item ->
                            ProfileNewsCardItem(post = item, viewModel = viewModel)
                        }
                    }
                }
                1 to 1 -> { // Saved Novels
                    if (savedNovels.isEmpty()) {
                        item { EmptyStateMessage("సేవ్ చేసుకున్న నవలలేవీ ప్రస్తుతానికి లేవు.") }
                    } else {
                        items(savedNovels) { item ->
                            ProfileNovelCardItem(post = item, viewModel = viewModel)
                        }
                    }
                }
                1 to 2 -> { // Saved Thoughts
                    if (savedThoughts.isEmpty()) {
                        item { EmptyStateMessage("మీరు ఏ భావాలను సేవ్ చేయలేదు.") }
                    } else {
                        items(savedThoughts) { item ->
                            ProfileThoughtCardItem(post = item, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Interactive Edit Profile dialog
    if (viewModel.showEditProfileDialog) {
        var editName by remember { mutableStateOf(user?.name ?: "") }
        var editBio by remember { mutableStateOf(user?.bio ?: "") }
        var editPhotoUrl by remember { mutableStateOf(user?.profilePhotoUrl ?: "") }

        val presetAvatars = listOf(
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb", // Cyber Girl
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d", // Scholar Man
            "https://images.unsplash.com/photo-1544005313-94ddf0286df2", // Modern Art
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d"  // Vintage Vibe
        )

        AlertDialog(
            onDismissRequest = { viewModel.showEditProfileDialog = false },
            title = {
                Text(
                    text = "Edit Profile / ఖాతా సవరణ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFFF59E0B)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name (పేరు)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_profile_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = Color(0xFFF59E0B),
                            focusedLabelColor = Color(0xFFF59E0B)
                        )
                    )

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Intro Bio (పరిచయం)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_profile_bio_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = Color(0xFFF59E0B),
                            focusedLabelColor = Color(0xFFF59E0B)
                        )
                    )

                    OutlinedTextField(
                        value = editPhotoUrl,
                        onValueChange = { editPhotoUrl = it },
                        label = { Text("Custom Profile Image URL") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_profile_imageurl_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = Color(0xFFF59E0B),
                            focusedLabelColor = Color(0xFFF59E0B)
                        )
                    )

                    // Curated High-End Preset Avatars
                    Text("Select Premium Avatar Profile:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetAvatars.forEach { url ->
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(CircleShape)
                                    .border(if (editPhotoUrl == url) 2.dp else 1.dp, if (editPhotoUrl == url) Color(0xFFF59E0B) else Color.Transparent, CircleShape)
                                    .clickable { editPhotoUrl = url }
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "avatar selection",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Gallery Custom Photo Cropper triggering button
                    Button(
                        onClick = {
                            viewModel.showEditProfileDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth().testTag("gallery_image_cropper_trigger")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery selection", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select From Gallery & Crop", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // Simulated Cropping Option for Preset Avatars
                    if (editPhotoUrl.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.showEditProfileDialog = false
                                cropTargetUri = editPhotoUrl
                                isCropModeActive = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            modifier = Modifier.fillMaxWidth().testTag("preset_crop_trigger")
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = "Crop Selection", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Interactive Crop Selected Avatar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(editName, editBio, editPhotoUrl)
                        viewModel.showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("SAVE CHANGES", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showEditProfileDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// Interactive Crop image canvas
@Composable
fun CropImageWidget(
    imageUri: String,
    onCropApplied: (String) -> Unit,
    onCancel: () -> Unit
) {
    var zoomScale by remember { mutableStateOf(1.2f) }
    var rotationAngle by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Text("Circular Crop Tool / సైజ్ మార్పు", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            TextButton(
                onClick = { onCropApplied(imageUri) },
                modifier = Modifier.testTag("apply_crop_button")
            ) {
                Text("APPLY / ఓకే", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // Circular Box Crop area mimicking actual circular frame preview
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .border(3.dp, Color(0xFFF59E0B), CircleShape)
                .background(Color.Black)
                .testTag("interactive_crop_viewfinder"),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Crop target window",
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        rotationZ = rotationAngle,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Crop
            )

            // Overlaid guidelines representing composition grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeColor = Color(0x66F59E0B)
                // circular frame
                drawCircle(color = strokeColor, radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                // rule-of-thirds grid
                drawLine(color = strokeColor, start = androidx.compose.ui.geometry.Offset(0f, size.height/3), end = androidx.compose.ui.geometry.Offset(size.width, size.height/3), strokeWidth = 1.5f)
                drawLine(color = strokeColor, start = androidx.compose.ui.geometry.Offset(0f, size.height*2/3), end = androidx.compose.ui.geometry.Offset(size.width, size.height*2/3), strokeWidth = 1.5f)
                drawLine(color = strokeColor, start = androidx.compose.ui.geometry.Offset(size.width/3, 0f), end = androidx.compose.ui.geometry.Offset(size.width/3, size.height), strokeWidth = 1.5f)
                drawLine(color = strokeColor, start = androidx.compose.ui.geometry.Offset(size.width*2/3, 0f), end = androidx.compose.ui.geometry.Offset(size.width*2/3, size.height), strokeWidth = 1.5f)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Scale Adjustment slider control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom Slider", tint = Color.LightGray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ZOOM", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it },
                        valueRange = 1f..4f,
                        modifier = Modifier.weight(1f).testTag("crop_scale_slider"),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B), activeTrackColor = Color(0xFFF59E0B))
                    )
                }

                // Rotations triggers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { rotationAngle = (rotationAngle - 90f) % 360f },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Default.RotateLeft, contentDescription = "Rotate CCW", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate Left", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate CW", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate Right", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Touch and slider to adjust the circular outline cut. Crop automatically uploads into profile database.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// News card specific to Profile page lists satisfying requirement: "News posts must display State and District tags."
@Composable
fun ProfileNewsCardItem(post: NewsPost, viewModel: AppViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Region tags displayed prominently
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High contrast badge satisfying State and District tags displaying
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = "location icon", tint = Color(0xFFEF4444), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${post.state} | ${post.district}",
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Content moderator safety status tag
                val statusColor = when (post.status) {
                    PostStatus.PUBLISHED -> Color(0xFF10B981)
                    PostStatus.PENDING_REVIEW -> Color(0xFFFFB300)
                    else -> Color(0xFFEF4444)
                }
                Text(post.status.name, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Top) {
                // Left hand image thumbnail
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "news photo",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Gray),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = post.description,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "By ${post.authorName}",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.toggleLike(post.id, PostType.NEWS) }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (viewModel.isLiked(post.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (viewModel.isLiked(post.id)) Color.Red else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text("${post.likesCount}", color = Color.LightGray, fontSize = 11.sp)
                    }
                    IconButton(onClick = { viewModel.toggleSave(post.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (viewModel.isSaved(post.id)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (viewModel.isSaved(post.id)) Color(0xFFF59E0B) else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Novel item card in Profile tab section clickable to read
@Composable
fun ProfileNovelCardItem(post: NovelPost, viewModel: AppViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { viewModel.activeReaderNovel = post }, // Clicking opens full story layout reader
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = post.coverImageUrl,
                contentDescription = "cover pic",
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(post.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Author: ${post.authorName}", color = Color(0xFFFFB300), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(post.content, color = Color.LightGray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Column(horizontalAlignment = Alignment.End) {
                val statusColor = when (post.status) {
                    PostStatus.PUBLISHED -> Color(0xFF10B981)
                    PostStatus.PENDING_REVIEW -> Color(0xFFFFB300)
                    else -> Color(0xFFEF4444)
                }
                Text(post.status.name, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(14.dp))
                IconButton(onClick = { viewModel.toggleSave(post.id) }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (viewModel.isSaved(post.id)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save Novel",
                        tint = if (viewModel.isSaved(post.id)) Color(0xFFF59E0B) else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Thought item card in Profile tab section
@Composable
fun ProfileThoughtCardItem(post: ThoughtPost, viewModel: AppViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(post.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                val statusColor = when (post.status) {
                    PostStatus.PUBLISHED -> Color(0xFF10B981)
                    PostStatus.PENDING_REVIEW -> Color(0xFFFFB300)
                    else -> Color(0xFFEF4444)
                }
                Text(post.status.name, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(post.content, color = Color.LightGray, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shared by ${post.authorName}", fontSize = 10.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { viewModel.toggleLike(post.id, PostType.THOUGHT) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (viewModel.isLiked(post.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like thought",
                            tint = if (viewModel.isLiked(post.id)) Color.Red else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.toggleSave(post.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (viewModel.isSaved(post.id)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save thought",
                            tint = if (viewModel.isSaved(post.id)) Color(0xFFF59E0B) else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStatItem(label: String, valStr: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF334155).copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(valStr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.LightGray.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DocumentOverlay(title: String, textDoc: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hasUrl = textDoc.contains("http")
    val url = if (hasUrl) {
        val startIndex = textDoc.indexOf("http")
        val spaceIndex = textDoc.indexOf(' ', startIndex)
        val newlineIndex = textDoc.indexOf('\n', startIndex)
        val endIndex = when {
            spaceIndex != -1 && newlineIndex != -1 -> minOf(spaceIndex, newlineIndex)
            spaceIndex != -1 -> spaceIndex
            newlineIndex != -1 -> newlineIndex
            else -> textDoc.length
        }
        textDoc.substring(startIndex, endIndex).trim()
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(textDoc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!url.isNullOrEmpty()) {
                    TextButton(onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Handler for action view failure
                        }
                    }) {
                        Text("OPEN LINK", fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("CLOSE", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                }
            }
        }
    )
}
