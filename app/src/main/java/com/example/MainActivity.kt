package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.PostStatus
import com.example.model.PostType
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeManager
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup FCM Push Notification high-fidelity systems
        android.util.Log.d("MainActivity", "Initializing Firebase Cloud Messaging and notification managers")
        com.example.services.NotificationHelper.createNotificationChannel(applicationContext)

        // Request POST_NOTIFICATIONS permission for Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val launcher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                android.util.Log.d("MainActivity", "POST_NOTIFICATIONS permission granted: $isGranted")
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Fetch current active FCM registration token
        if (com.example.repository.FirebaseState.isInitialized) {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        android.util.Log.w("MainActivity", "Fetching FCM registration token failed: ", task.exception)
                        return@addOnCompleteListener
                    }
                    val token = task.result
                    android.util.Log.d("MainActivity", "Fetched FCM token successfully: $token")
                    
                    // Cache the token cleanly
                    val sharedPrefs = getSharedPreferences("telugu_fcm_prefs", MODE_PRIVATE)
                    sharedPrefs.edit().putString("fcm_token", token).apply()
                    
                    // Save dynamically to primary backend repository
                    val repository = com.example.repository.AppRepository.getInstance(applicationContext)
                    repository.updateFCMTokenInFirestore(token)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "FCM setup error: ${e.message}")
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                if (viewModel.showSplash) {
                    SplashScreen()
                } else {
                    AppMainNavigation(viewModel)
                }
            }
        }

        // Handle path clicked through system notification launcher
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val postId = intent?.getStringExtra("postId")
        val postType = intent?.getStringExtra("postType")
        if (!postId.isNullOrEmpty() && !postType.isNullOrEmpty()) {
            android.util.Log.d("MainActivity", "Push notification target match: PostId='$postId', PostType='$postType'")
            try {
                val repository = com.example.repository.AppRepository.getInstance(applicationContext)
                when (postType) {
                    "NEWS" -> {
                        repository.newsPosts.value.find { it.id == postId }?.let {
                            viewModel.activeReaderNews = it
                        }
                    }
                    "NOVEL" -> {
                        repository.novelPosts.value.find { it.id == postId }?.let {
                            viewModel.activeReaderNovel = it
                        }
                    }
                    "THOUGHT" -> {
                        repository.thoughtPosts.value.find { it.id == postId }?.let {
                            viewModel.activeReaderThought = it
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to deep link via FCM: ${e.message}")
            }
        }
    }
}

@Composable
fun AppMainNavigation(viewModel: AppViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        when (viewModel.currentScreen) {
            "LOGIN" -> LoginScreen(viewModel, onNavigateToRegister = { viewModel.currentScreen = "REGISTER" })
            "REGISTER" -> RegisterScreen(viewModel, onNavigateToLogin = { viewModel.currentScreen = "LOGIN" })
            "MAIN" -> HomeScreen(viewModel)
            else -> LoginScreen(viewModel, onNavigateToRegister = {})
        }
    }
}

// Helper function to persistently copy any picked gallery photo to internal storage, avoiding loss of content on session logout
fun copyUriToInternalStorage(context: android.content.Context, uri: Uri, prefix: String): Uri? {
    try {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(uri) ?: return null
        val extension = resolver.getType(uri)?.split("/")?.lastOrNull() ?: "jpg"
        val file = java.io.File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.$extension")
        java.io.FileOutputStream(file).use { out ->
            inputStream.use { input ->
                input.copyTo(out)
            }
        }
        return Uri.fromFile(file)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error persistent image copy: ${e.message}", e)
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val applicationContext = context.applicationContext

    // Notification list overlay sheet state
    var showNotifSheet by remember { mutableStateOf(false) }

    // Drawer info sheet controllers
    var activeInfoDocTitle by remember { mutableStateOf<String?>(null) }
    var activeInfoDocText by remember { mutableStateOf<String?>(null) }

    // Android Gallery launcher selections
    val newsGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val persistentUri = copyUriToInternalStorage(applicationContext, uri, "news")
            viewModel.newsImgInput = persistentUri?.toString() ?: uri.toString()
        }
    }

    val novelGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val persistentUri = copyUriToInternalStorage(applicationContext, uri, "novel")
            viewModel.novelCoverInput = persistentUri?.toString() ?: uri.toString()
        }
    }

    val thoughtGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val persistentUri = copyUriToInternalStorage(applicationContext, uri, "thought")
            viewModel.thoughtImgInput = persistentUri?.toString() ?: uri.toString()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1E293B),
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                // Drawer header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFB300), Color(0xFFF59E0B))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        AsyncImage(
                            model = currentUser?.profilePhotoUrl ?: "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(currentUser?.name ?: "User Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(
                            text = if (currentUser?.isAdmin == true) "Super Admin Level" else "Registered Member",
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. Admin Panel (only if currentUser email is sharvanipatel.ias@gmail.com)
                    if (currentUser?.email?.lowercase()?.trim() == "sharvanipatel.ias@gmail.com") {
                        item {
                            NavigationDrawerItem(
                                label = { Text("Admin Dashboard / నిర్వాహణ", fontWeight = FontWeight.Bold) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    viewModel.showAdminPanel = true
                                },
                                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin", tint = Color(0xFFFFD700)) },
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    // 2. Profile Editing
                    item {
                        NavigationDrawerItem(
                            label = { Text("Edit Profile / ప్రొఫైల్ సవరణ", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                viewModel.activeBottomTab = "PROFILE"
                                viewModel.showEditProfileDialog = true
                            },
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = Color(0xFF38BDF8)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_edit_profile_button")
                        )
                    }

                    // 3. Earn Coins Playstore Policy
                    item {
                        NavigationDrawerItem(
                            label = { Text("Earn Coins / నాణేలు ఆర్జించండి (${viewModel.userCoins} c)", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                activeInfoDocTitle = "Playstore Earning Policy (కాయిన్ పాలసీ)"
                                activeInfoDocText = "మేము గూగుల్ ప్లేస్టోర్ పాలసీలను నూటికి నూరు శాతం గౌరవిస్తాము. ఈ యాప్ లో మీరు ప్రకటనలను వీక్షించడం ద్వారా లేదా ప్రాయోజిత క్లిక్లు విజయవంతం చేయడం ద్వారా సంపాదించే ప్రతి 100 కాయిన్స్ ప్లేస్టోర్ గిఫ్ట్ వోచర్లుగా బదిలీ చేసుకోవచ్చు."
                            },
                            icon = { Icon(Icons.Default.MonetizationOn, contentDescription = "Coins", tint = Color(0xFFFFD700)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // 4. Change Language Component
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("language_switcher_card"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Icon(Icons.Default.Translate, contentDescription = "Language", tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (ThemeManager.currentLanguage == "TE") "యాప్ భాష / Choose Language" else "App Language / యాప్ భాష",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.LightGray
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Telugu Option
                                    val isTe = ThemeManager.currentLanguage == "TE"
                                    Button(
                                        onClick = { ThemeManager.currentLanguage = "TE" },
                                        modifier = Modifier.weight(1f).height(38.dp).testTag("lang_te_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isTe) Color(0xFF34D399) else Color(0xFF1E293B),
                                            contentColor = if (isTe) Color.Black else Color.White
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("తెలుగు (TE)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // English Option
                                    val isEn = ThemeManager.currentLanguage == "EN"
                                    Button(
                                        onClick = { ThemeManager.currentLanguage = "EN" },
                                        modifier = Modifier.weight(1f).height(38.dp).testTag("lang_en_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isEn) Color(0xFF34D399) else Color(0xFF1E293B),
                                            contentColor = if (isEn) Color.Black else Color.White
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("English (EN)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // 5. Themes
                    item {
                        NavigationDrawerItem(
                            label = { Text("Theme / థీమ్: ${if (ThemeManager.isDarkTheme) "Dark / రాత్రి" else "Light / పగలు"}", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                ThemeManager.isDarkTheme = !ThemeManager.isDarkTheme
                            },
                            icon = { Icon(Icons.Default.Palette, contentDescription = "Theme", tint = Color(0xFFF472B6)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // 6. Privacy Policy
                    item {
                        NavigationDrawerItem(
                            label = { Text(if (ThemeManager.currentLanguage == "TE") "గోప్యతా విధానం / Privacy Policy" else "Privacy Policy / గోప్యతా విధానం", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                activeInfoDocTitle = if (ThemeManager.currentLanguage == "TE") "గోప్యతా విధానం / Privacy Policy" else "Privacy Policy"
                                activeInfoDocText = "Privacy Policy https://share.google/w5Gxs5FSNiYNOuCoV\n\nTelugu Novel & Samacharam యాప్ తన వినియోగదారుల వ్యక్తిగత సమాచార భద్రతకు అత్యంత ప్రాధాన్యత ఇస్తుంది. గూగుల్ ప్లేస్టోర్ నిబంధనలకు కట్టుబడి మీ సమాచారం సురక్షితంగా ఉంచబడుతుంది."
                            },
                            icon = { Icon(Icons.Default.Security, contentDescription = "Privacy", tint = Color(0xFF818CF8)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // 7. Terms & Conditions
                    item {
                        NavigationDrawerItem(
                            label = { Text(if (ThemeManager.currentLanguage == "TE") "నిబంధనలు & షరతులు / Terms" else "Terms & Conditions / షరతులు", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                activeInfoDocTitle = if (ThemeManager.currentLanguage == "TE") "షరతులు & నిబంధనలు / Terms & Conditions" else "Terms & Conditions"
                                activeInfoDocText = """
- By using this app, you agree to these Terms & Conditions.
- Do not post fake news, abusive, hateful, illegal, or inappropriate content.
- All posts may be reviewed by AI moderation and Admin before publication.
- Content that violates the rules may be removed without notice.
- User accounts may be suspended or permanently banned for repeated violations.
- Personal information is kept private and secure.
- The app may display advertisements.
- These Terms & Conditions may be updated at any time.

By using Telugu Novel & Samacharam, you agree to follow these Terms & Conditions.
                                """.trimIndent()
                            },
                            icon = { Icon(Icons.Default.Assignment, contentDescription = "Terms", tint = Color(0xFFFBBF24)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // 8. About App
                    item {
                        NavigationDrawerItem(
                            label = { Text("About App / యాప్ గురించి", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                activeInfoDocTitle = "About App (యాప్ గురించి)"
                                activeInfoDocText = "తెలుగు నవలలు, తాజా వార్తా సేకరిణి మరియు స్ఫూర్తిదాయక భావాల ఏకీకృత వేదిక. ఇక్కడ అందరూ తమ రచనలను స్వయంగా ప్రచురించవచ్చు, తోటి పాఠకులతో పంచుకోవచ్చు. స్పాన్సర్ ప్రకటనల ద్వారా కాయిన్స్ ఆర్జించవచ్చు. యాప్ ఆటో అప్డేట్ సదుపాయం యాక్టివ్ లో కలదు."
                            },
                            icon = { Icon(Icons.Default.Info, contentDescription = "About", tint = Color(0xFF22D3EE)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // 9. Quiet logout
                    item {
                        NavigationDrawerItem(
                            label = { Text("Logout / లాగ్అవుట్", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                viewModel.logout()
                            },
                            icon = { Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color(0xFFFF5252)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        if (viewModel.showAdminPanel) {
            AdminPanelScreen(viewModel, onBack = { viewModel.showAdminPanel = false })
        } else if (viewModel.activeWebViewUrl != null) {
            val webUrl = viewModel.activeWebViewUrl ?: ""
            val webTitle = viewModel.activeWebViewTitle ?: "Web Details"
            val adSettings by viewModel.adSettings.collectAsState()
            com.example.ui.components.InAppBrowser(
                url = webUrl,
                title = webTitle,
                adSettings = adSettings,
                viewModel = viewModel,
                onClose = {
                    viewModel.activeWebViewUrl = null
                    viewModel.activeWebViewTitle = null
                }
            )
        } else {
            Scaffold(
                topBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Main Top App Bar
                        TopAppBar(
                            title = {
                                Text(
                                    text = if (ThemeManager.currentLanguage == "TE") "నోవెల్ & సమాచారం" else "Telugu Novel & News",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Serif,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            navigationIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    // Three Line Menu (☰)
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Three Line Menu",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                    // Profile icon on the left -> Click shows Profile Page
                                    IconButton(
                                        onClick = { viewModel.activeBottomTab = "PROFILE" },
                                        modifier = Modifier.testTag("top_bar_profile_icon_click")
                                    ) {
                                        AsyncImage(
                                            model = currentUser?.profilePhotoUrl ?: "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
                                            contentDescription = "Profile Page Shortcut",
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, Color(0xFFF59E0B), CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            },
                            actions = {
                                val unreadCount = notifications.count { !it.isRead }
                                // Beautiful, colorful Posting Icon in the AppBar with spacer for little space!
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                        .clickable { viewModel.showPostDialog = true }
                                        .testTag("top_bar_posting_icon"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add, 
                                        contentDescription = "Post / ప్రచురించండి", 
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(6.dp)) // Spacing between buttons

                                Box(modifier = Modifier.wrapContentSize().padding(end = 4.dp)) {
                                    IconButton(onClick = {
                                        showNotifSheet = true
                                        viewModel.markNotificationsRead()
                                    }) {
                                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
                                    }
                                    if (unreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(16.dp)
                                                .background(Color.Red, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("$unreadCount", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF1E293B),
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )


                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(0xFF1E293B),
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = viewModel.activeBottomTab == "NEWS",
                            onClick = { viewModel.activeBottomTab = "NEWS" },
                            icon = { Text("📑", fontSize = 21.sp) },
                            label = { Text(if (ThemeManager.currentLanguage == "TE") "వార్తలు" else "News", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFF59E0B),
                                unselectedIconColor = Color.LightGray,
                                selectedTextColor = Color(0xFFF59E0B),
                                indicatorColor = Color(0xFF334155)
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.activeBottomTab == "NOVEL",
                            onClick = { viewModel.activeBottomTab = "NOVEL" },
                            icon = { Text("📖", fontSize = 21.sp) },
                            label = { Text(if (ThemeManager.currentLanguage == "TE") "నవలలు" else "Novel", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFF59E0B),
                                unselectedIconColor = Color.LightGray,
                                selectedTextColor = Color(0xFFF59E0B),
                                indicatorColor = Color(0xFF334155)
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.activeBottomTab == "EXPLORE",
                            onClick = { viewModel.activeBottomTab = "EXPLORE" },
                            icon = { Text("🔍", fontSize = 21.sp) },
                            label = { Text(if (ThemeManager.currentLanguage == "TE") "అన్వేషణ" else "Explore", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFF59E0B),
                                unselectedIconColor = Color.LightGray,
                                selectedTextColor = Color(0xFFF59E0B),
                                indicatorColor = Color(0xFF334155)
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.activeBottomTab == "THOUGHTS",
                            onClick = { viewModel.activeBottomTab = "THOUGHTS" },
                            icon = { Text("🤔", fontSize = 21.sp) },
                            label = { Text(if (ThemeManager.currentLanguage == "TE") "భావాలు" else "Thoughts", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFF59E0B),
                                unselectedIconColor = Color.LightGray,
                                selectedTextColor = Color(0xFFF59E0B),
                                indicatorColor = Color(0xFF334155)
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.activeBottomTab == "PROFILE",
                            onClick = { viewModel.activeBottomTab = "PROFILE" },
                            icon = { Text("👤", fontSize = 21.sp) },
                            label = { Text(if (ThemeManager.currentLanguage == "TE") "ప్రొఫైల్" else "Profile", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFF59E0B),
                                unselectedIconColor = Color.LightGray,
                                selectedTextColor = Color(0xFFF59E0B),
                                indicatorColor = Color(0xFF334155)
                            )
                        )
                    }
                },
                containerColor = Color(0xFF0F172A)
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    when (viewModel.activeBottomTab) {
                        "NEWS" -> NewsFeedScreen(viewModel)
                        "NOVEL" -> NovelFeedScreen(viewModel)
                        "EXPLORE" -> ExploreScreen(viewModel)
                        "THOUGHTS" -> ThoughtsFeedScreen(viewModel)
                        "PROFILE" -> ProfileTabScreen(viewModel)
                    }

                    // Admin Dashboard floating entry button (Only visible if the active user email is sharvanipatel.ias@gmail.com)
                    if (currentUser?.email?.lowercase()?.trim() == "sharvanipatel.ias@gmail.com") {
                        FloatingActionButton(
                            onClick = { viewModel.showAdminPanel = true },
                            containerColor = Color(0xFFFFD700),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .testTag("admin_floating_trigger_button")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin Panel", tint = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Novel Reading overlay flow
    viewModel.activeReaderNovel?.let { activeReadingNovel ->
        com.example.ui.screens.NovelReaderOverlay(post = activeReadingNovel, viewModel = viewModel, onDismiss = { viewModel.activeReaderNovel = null })
    }

    viewModel.activeReaderNews?.let { activeReadingNews ->
        com.example.ui.screens.NewsReaderOverlay(post = activeReadingNews, viewModel = viewModel, onDismiss = { viewModel.activeReaderNews = null })
    }

    viewModel.activeReaderThought?.let { activeReadingThought ->
        com.example.ui.screens.ThoughtReaderOverlay(post = activeReadingThought, viewModel = viewModel, onDismiss = { viewModel.activeReaderThought = null })
    }

    // Modal Notifications list bottom sheet
    if (showNotifSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotifSheet = false },
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Text(
                    text = "నోటిఫికేషన్లు (${notifications.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
                Divider(color = Color(0xFF334155))

                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("కొత్త నోటిఫికేషన్లు లేవు.", color = Color(0xFF94A3B8))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        items(notifications) { notif ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF334155), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(notif.senderPhotoUrl.ifEmpty { "🔔" }, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(notif.message, color = Color.White, fontSize = 13.sp)
                                        Text("Telugu Novel Team", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                }
                                IconButton(onClick = { viewModel.dismissNotification(notif.id) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Dismiss", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }
                            }
                            Divider(color = Color(0xFF1E293B))
                        }
                    }
                }
            }
        }
    }

    // Modal Comments overlay sheet
    viewModel.activeCommentsPostId?.let {
        CommentsSheet(viewModel = viewModel, onDismiss = { viewModel.activeCommentsPostId = null })
    }

    // ADD POST DIALOGS OVERLAYS (Full Screen premium editor with min 15-20 lines)
    if (viewModel.showPostDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.showPostDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (viewModel.activeBottomTab == "NOVEL") Color(0xFFFAF6EE) else Color(0xFF0F172A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (viewModel.activeBottomTab == "NOVEL") Color(0xFFFAF6EE) else Color(0xFF0F172A))
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Modern Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.showPostDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Writer",
                                tint = if (viewModel.activeBottomTab == "NOVEL") Color(0xFF3B2314) else Color.White
                            )
                        }
                        
                        val isEditing = viewModel.editingPostId != null
                        Text(
                            text = if (isEditing) {
                                if (viewModel.activeBottomTab == "NOVEL") "✍️ EDIT NOVEL / సవరించండి" else "📝 EDIT EDITOR / సవరించండి"
                            } else {
                                if (viewModel.activeBottomTab == "NOVEL") "✍️ NOVEL WRITING SPACE" else "📝 SAMACHAARAM EDITOR"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (viewModel.activeBottomTab == "NOVEL") Color(0xFF5C4033) else Color(0xFFF59E0B)
                        )
                        
                        Button(
                            onClick = { viewModel.submitPost() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.activeBottomTab == "NOVEL") Color(0xFF5C4033) else Color(0xFFF59E0B)
                            ),
                            enabled = !viewModel.isPostUploading,
                            modifier = Modifier.testTag("submit_post_btn")
                        ) {
                            Text(
                                if (isEditing) "UPDATE / మార్చు" else "PUBLISH", 
                                color = if (viewModel.activeBottomTab == "NOVEL") Color.White else Color.Black, 
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    if (viewModel.isPostUploading) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x22F59E0B))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFF59E0B))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = viewModel.postProgressMessage ?: "Auto Moderating via Gemini AI...",
                                    fontSize = 12.sp,
                                    color = if (viewModel.activeBottomTab == "NOVEL") Color(0xFF5C4033) else Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    if (viewModel.activeBottomTab == "NEWS") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Region Specifications / ప్రైవేట్ ప్రాంత సమాచారం", color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = viewModel.newsStateInput,
                                        onValueChange = { viewModel.newsStateInput = it },
                                        label = { Text("State (రాష్ట్రం)") },
                                        modifier = Modifier.weight(1f).testTag("news_state_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFFF59E0B),
                                            unfocusedBorderColor = Color(0xFF475569),
                                            focusedLabelColor = Color(0xFFF59E0B)
                                        )
                                    )
                                    OutlinedTextField(
                                        value = viewModel.newsDistrictInput,
                                        onValueChange = { viewModel.newsDistrictInput = it },
                                        label = { Text("District (జిల్లా)") },
                                        modifier = Modifier.weight(1f).testTag("news_district_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFFF59E0B),
                                            unfocusedBorderColor = Color(0xFF475569),
                                            focusedLabelColor = Color(0xFFF59E0B)
                                        )
                                    )
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            value = viewModel.newsTitleInput,
                            onValueChange = { viewModel.newsTitleInput = it },
                            label = { Text("News Title (వార్త శీర్షిక)") },
                            modifier = Modifier.fillMaxWidth().testTag("news_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFFF59E0B)
                            )
                        )
                        
                        OutlinedTextField(
                            value = viewModel.newsImgInput,
                            onValueChange = { viewModel.newsImgInput = it },
                            label = { Text("News Image URL (వార్త చిత్రం లింక్)") },
                            modifier = Modifier.fillMaxWidth().testTag("news_image_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFFF59E0B)
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { newsGalleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F59E0B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color(0xFFF59E0B))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Photo / పిక్ ఎంచుకోండి", color = Color(0xFFF59E0B), fontSize = 11.sp)
                            }
                            if (viewModel.newsImgInput.isNotEmpty()) {
                                AsyncImage(
                                    model = viewModel.newsImgInput,
                                    contentDescription = "Selected path preview",
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFF59E0B)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("NEWS CONTENT (వార్తా కథనం - కనీసం 15 లైన్లు నిండుగా)", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = viewModel.newsDescInput,
                            onValueChange = { viewModel.newsDescInput = it },
                            placeholder = { Text("పరిసర ప్రాంతాల వార్తా విశేషాలను ఇక్కడ సవివరంగా రాయండి...", color = Color.Gray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("news_desc_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFFF59E0B)
                            ),
                            minLines = 15,
                            maxLines = Int.MAX_VALUE
                        )
                    } else if (viewModel.activeBottomTab == "NOVEL") {
                        // Novel Editor Theme: Classic Parchment Style
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF3E7D3), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "📖 FULL CHAPTER WRITER (నవ్య నవలల గూడు)\nYour text is processed locally and formatted into sequential book content columns. Auto-saving enabled.",
                                fontSize = 11.sp,
                                color = Color(0xFF4A2F13),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        OutlinedTextField(
                            value = viewModel.novelTitleInput,
                            onValueChange = { viewModel.novelTitleInput = it },
                            label = { Text("Novel Title (నవల శీర్షిక)", color = Color(0xFF5C4033)) },
                            modifier = Modifier.fillMaxWidth().testTag("novel_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedBorderColor = Color(0xFF5C4033),
                                unfocusedBorderColor = Color(0xFFC5B39A),
                                focusedLabelColor = Color(0xFF5C4033)
                            )
                        )

                        // Part Selection/Input Layout
                        Text(
                            text = "Select Novel Part / అధ్యాయం భాగం ఎంచుకోండి:",
                            color = Color(0xFF5C4033),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presetParts = listOf("Part 1", "Part 2", "Part 3", "Part 4")
                            presetParts.forEach { preset ->
                                val isSelected = viewModel.novelPartInput == preset
                                Button(
                                    onClick = { viewModel.novelPartInput = preset },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF5C4033) else Color(0x1F5C4033)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White else Color(0xFF5C4033),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = viewModel.novelPartInput,
                            onValueChange = { viewModel.novelPartInput = it },
                            label = { Text("Or Custom Part Identifier / లేదా ఇతర భాగం (e.g. Part 1)", color = Color(0xFF5C4033)) },
                            modifier = Modifier.fillMaxWidth().testTag("novel_part_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedBorderColor = Color(0xFF5C4033),
                                unfocusedBorderColor = Color(0xFFC5B39A),
                                focusedLabelColor = Color(0xFF5C4033)
                            )
                        )
                        
                        OutlinedTextField(
                            value = viewModel.novelCoverInput,
                            onValueChange = { viewModel.novelCoverInput = it },
                            label = { Text("Novel Cover Image (నవల ముఖచిత్రం)", color = Color(0xFF5C4033)) },
                            modifier = Modifier.fillMaxWidth().testTag("novel_cover_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedBorderColor = Color(0xFF5C4033),
                                unfocusedBorderColor = Color(0xFFC5B39A),
                                focusedLabelColor = Color(0xFF5C4033)
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { novelGalleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x335C4033)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color(0xFF5C4033))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Book Cover / నవల ముఖచిత్రం పిక్", color = Color(0xFF5C4033), fontSize = 11.sp)
                            }
                            if (viewModel.novelCoverInput.isNotEmpty()) {
                                AsyncImage(
                                    model = viewModel.novelCoverInput,
                                    contentDescription = "Cover Image Preview",
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF5C4033)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("BOOK CHAPTER STORY (కథాంశం - కనీసం 20 లైన్లు)", color = Color(0xFF5C4033), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${viewModel.novelContentInput.length} chars", color = Color(0xFF5C4033).copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                        
                        OutlinedTextField(
                            value = viewModel.novelContentInput,
                            onValueChange = { viewModel.novelContentInput = it },
                            placeholder = { Text("ఈ నవల మొదటి అధ్యాయాన్ని ఇక్కడ ప్రశాంతంగా, విశాలంగా కుదించి రాయండి...", color = Color.Gray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("novel_content_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedBorderColor = Color(0xFF5C4033),
                                unfocusedBorderColor = Color(0xFFC5B39A)
                            ),
                            minLines = 8,
                            maxLines = Int.MAX_VALUE
                        )
                    } else if (viewModel.activeBottomTab == "THOUGHTS") {
                        OutlinedTextField(
                            value = viewModel.thoughtTitleInput,
                            onValueChange = { viewModel.thoughtTitleInput = it },
                            label = { Text("Thought Title (భావన శీర్షిక)") },
                            modifier = Modifier.fillMaxWidth().testTag("thought_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFFF59E0B)
                            )
                        )
                        
                        OutlinedTextField(
                            value = viewModel.thoughtImgInput,
                            onValueChange = { viewModel.thoughtImgInput = it },
                            label = { Text("Optional Concept Image URL (చిత్రం లింక్)") },
                            modifier = Modifier.fillMaxWidth().testTag("thought_image_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFFF59E0B)
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { thoughtGalleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F59E0B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color(0xFFF59E0B))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Photo / పిక్ ఎంచుకోండి", color = Color(0xFFF59E0B), fontSize = 11.sp)
                            }
                            if (viewModel.thoughtImgInput.isNotEmpty()) {
                                AsyncImage(
                                    model = viewModel.thoughtImgInput,
                                    contentDescription = "Selected Path preview",
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFF59E0B)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("THOUGHT CONTENT (భావాలు/సూక్తులు - కనీసం 15 లైన్లు)", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = viewModel.thoughtContentInput,
                            onValueChange = { viewModel.thoughtContentInput = it },
                            placeholder = { Text("మీ మనసులోని ప్రేరేపిత భావాలను, జీవిత సత్యాలను ఇక్కడ అందంగా రాయండి...", color = Color.Gray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("thought_content_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFFF59E0B)
                            ),
                            minLines = 8,
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
        }
    }

    // Render legal documents
    if (activeInfoDocTitle != null && activeInfoDocText != null) {
        DocumentOverlay(title = activeInfoDocTitle!!, textDoc = activeInfoDocText!!) {
            activeInfoDocTitle = null
            activeInfoDocText = null
        }
    }
}
