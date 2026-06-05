package com.example.repository

import android.content.Context
import android.util.Log
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

object FirebaseState {
    var isInitialized = false
        private set
    var connectionMessage = "Database system: Local Offline Simulation Engine"
        private set

    fun init(context: Context) {
        try {
            val app = FirebaseApp.getInstance()
            isInitialized = true
            connectionMessage = "Connected to Firebase Cloud Backend Engine: ${app.name}"
            Log.d("FirebaseState", "Firebase already initialized: $connectionMessage")
        } catch (e: Exception) {
            try {
                // Try initializing with native json assets if they exist
                val options = com.google.firebase.FirebaseOptions.fromResource(context)
                if (options != null) {
                    FirebaseApp.initializeApp(context, options)
                    isInitialized = true
                    connectionMessage = "Connected to Firebase Cloud Engine via Resource Config"
                    Log.d("FirebaseState", "Firebase initialized successfully: $connectionMessage")
                } else {
                    isInitialized = false
                    connectionMessage = "Local Simulated Storage Mode (Add google-services.json to activate Firebase)"
                    Log.i("FirebaseState", "No Firebase options found in resources. Operating in local mode.")
                }
            } catch (ex: Exception) {
                isInitialized = false
                connectionMessage = "Local Simulated Storage Mode (Add google-services.json to activate Firebase)"
                Log.w("FirebaseState", "Run with local offline state engine. Details: ${ex.message}")
            }
        }
    }
}

class AppRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _connectionStatus = MutableStateFlow(FirebaseState.connectionMessage)
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    // Moshi for local persistence serialization
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Active logged-in user
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Main feeds
    private val _newsPosts = MutableStateFlow<List<NewsPost>>(emptyList())
    val newsPosts: StateFlow<List<NewsPost>> = _newsPosts.asStateFlow()

    private val _novelPosts = MutableStateFlow<List<NovelPost>>(emptyList())
    val novelPosts: StateFlow<List<NovelPost>> = _novelPosts.asStateFlow()

    private val _thoughtPosts = MutableStateFlow<List<ThoughtPost>>(emptyList())
    val thoughtPosts: StateFlow<List<ThoughtPost>> = _thoughtPosts.asStateFlow()

    // Metadata & Social tables
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _likes = MutableStateFlow<List<Like>>(emptyList())
    val likes: StateFlow<List<Like>> = _likes.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val usersList: StateFlow<List<User>> = _users.asStateFlow()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _savedPostIds = MutableStateFlow<Set<String>>(emptySet()) // Post IDs saved by the current user
    val savedPostIds: StateFlow<Set<String>> = _savedPostIds.asStateFlow()

    private val _adSettings = MutableStateFlow(AdSettings())
    val adSettings: StateFlow<AdSettings> = _adSettings.asStateFlow()

    init {
        INSTANCE = this
        // Run database synchronization setup
        FirebaseState.init(context)
        _connectionStatus.value = FirebaseState.connectionMessage
        loadLocalData()
        syncWithFirebase()

        // Background collector to automatically synchronise local/server FCM token for active user profile
        scope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val sharedPrefs = context.getSharedPreferences("telugu_fcm_prefs", Context.MODE_PRIVATE)
                    val token = sharedPrefs.getString("fcm_token", null)
                    if (!token.isNullOrEmpty() && user.fcmToken != token) {
                        val updatedUser = user.copy(fcmToken = token)
                        _currentUser.value = updatedUser
                        _users.value = _users.value.map { if (it.id == user.id) updatedUser else it }
                        saveUsersLocal()
                        if (FirebaseState.isInitialized) {
                            try {
                                FirebaseFirestore.getInstance().collection("users").document(user.id).update("fcmToken", token)
                                Log.d("AppRepository", "FCM token background auto-synced to Firestore users collection successfully")
                            } catch (e: Exception) {
                                Log.e("AppRepository", "FCM background sync failed: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        // Restoring active user session from local preferences
        val loginPrefs = context.getSharedPreferences("telugu_auth_prefs", Context.MODE_PRIVATE)
        val lastUid = loginPrefs.getString("last_logged_in_uid", null)
        if (lastUid != null) {
            val foundUser = _users.value.find { it.id == lastUid }
            if (foundUser != null && !foundUser.isBlocked) {
                _currentUser.value = foundUser
                Log.d("AppRepository", "Session restored successfully from local prefs: ${foundUser.name}")
            }
        }

        // Restoring / syncing active user session via Firebase Auth
        if (FirebaseState.isInitialized) {
            try {
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    scope.launch {
                        try {
                            val doc = Tasks.await(FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).get())
                            val userObj = doc.toObject(User::class.java)
                            if (userObj != null && !userObj.isBlocked) {
                                _currentUser.value = userObj
                                loginPrefs.edit().putString("last_logged_in_uid", userObj.id).apply()
                                val updatedList = _users.value.filter { it.id != userObj.id } + userObj
                                _users.value = updatedList
                                saveUsersLocal()
                                Log.i("AppRepository", "Session successfully synced with Firebase Auth profile: ${userObj.name}")
                            }
                        } catch (e: Exception) {
                            Log.e("AppRepository", "Auto-auth Firebase session synchronization failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AppRepository", "Firebase auth checking inactive/failed on init: ${e.message}")
            }
        }
    }

    private fun loadLocalData() {
        try {
            // Load users list
            val usersFile = File(context.filesDir, "users.json")
            if (usersFile.exists()) {
                val listType = Types.newParameterizedType(List::class.java, User::class.java)
                val adapter = moshi.adapter<List<User>>(listType)
                _users.value = adapter.fromJson(usersFile.readText()) ?: emptyList()
            } else {
                // Seed basic admin and testing user
                val defaultAdmin = User(
                    id = "admin_user_seed",
                    name = "Sharvani Patel IAS",
                    email = "sharvanipatel.ias@gmail.com",
                    phoneNumber = "9490123456",
                    profilePhotoUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
                    isAdmin = true
                )
                val testUser = User(
                    id = "normal_user_seed",
                    name = "Pranoy Rao",
                    email = "pranoy@example.com",
                    phoneNumber = "9440123123",
                    profilePhotoUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d",
                    bio = "తెలుగు కవితలు మరియు వార్తల అభిమాని."
                )
                _users.value = listOf(defaultAdmin, testUser)
                saveUsersLocal()
            }

            // Load news posts
            val newsFile = File(context.filesDir, "news.json")
            if (newsFile.exists()) {
                val listType = Types.newParameterizedType(List::class.java, NewsPost::class.java)
                _newsPosts.value = moshi.adapter<List<NewsPost>>(listType).fromJson(newsFile.readText()) ?: emptyList()
            } else {
                seedInitialNews()
            }

            // Load novel posts
            val novelFile = File(context.filesDir, "novels.json")
            if (novelFile.exists()) {
                val listType = Types.newParameterizedType(List::class.java, NovelPost::class.java)
                _novelPosts.value = moshi.adapter<List<NovelPost>>(listType).fromJson(novelFile.readText()) ?: emptyList()
            } else {
                seedInitialNovels()
            }

            // Load thought posts
            val thoughtFile = File(context.filesDir, "thoughts.json")
            if (thoughtFile.exists()) {
                val listType = Types.newParameterizedType(List::class.java, ThoughtPost::class.java)
                _thoughtPosts.value = moshi.adapter<List<ThoughtPost>>(listType).fromJson(thoughtFile.readText()) ?: emptyList()
            } else {
                seedInitialThoughts()
            }

            // Load comments
            val commentsFile = File(context.filesDir, "comments.json")
            if (commentsFile.exists()) {
                val listType = Types.newParameterizedType(List::class.java, Comment::class.java)
                _comments.value = moshi.adapter<List<Comment>>(listType).fromJson(commentsFile.readText()) ?: emptyList()
            }

            // Load likes
            val likesFile = File(context.filesDir, "likes.json")
            if (likesFile.exists()) {
                val listType = Types.newParameterizedType(List::class.java, Like::class.java)
                _likes.value = moshi.adapter<List<Like>>(listType).fromJson(likesFile.readText()) ?: emptyList()
            }

            // Load notifications
            val notificationsFile = File(context.filesDir, "notifications.json")
            if (notificationsFile.exists()) {
                val listType = Types.newParameterizedType(List::class.java, Notification::class.java)
                _notifications.value = moshi.adapter<List<Notification>>(listType).fromJson(notificationsFile.readText()) ?: emptyList()
            }

            // Load saved posts
            val savedFile = File(context.filesDir, "saved_posts.json")
            if (savedFile.exists()) {
                val listType = Types.newParameterizedType(Set::class.java, String::class.java)
                _savedPostIds.value = moshi.adapter<Set<String>>(listType).fromJson(savedFile.readText()) ?: emptySet()
            }

            // Load ad settings
            val adsFile = File(context.filesDir, "ad_settings.json")
            if (adsFile.exists()) {
                val adapter = moshi.adapter(AdSettings::class.java)
                _adSettings.value = adapter.fromJson(adsFile.readText()) ?: AdSettings()
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Error loading local storage files", e)
        }
    }

    private fun writeToUserPosts(userId: String, postId: String, post: Any) {
        if (FirebaseState.isInitialized && userId.isNotEmpty()) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("user_posts")
                    .document(postId)
                    .set(post)
            } catch (e: Exception) {
                Log.e("AppRepository", "Error writing to user_posts subcollection: ${e.message}")
            }
        }
    }

    private fun syncWithFirebase() {
        if (!FirebaseState.isInitialized) return
        val db = FirebaseFirestore.getInstance()

        // 1. Listen to "users" collection real-time
        db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of users failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val firestoreUsers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.copy(id = doc.id, userId = doc.id)
                }
                if (firestoreUsers.isNotEmpty()) {
                    _users.value = firestoreUsers
                    saveUsersLocal()

                    _currentUser.value?.id?.let { currentId ->
                        firestoreUsers.find { it.id == currentId }?.let { updatedMe ->
                            if (updatedMe != _currentUser.value) {
                                _currentUser.value = updatedMe
                                val loginPrefs = context.getSharedPreferences("telugu_auth_prefs", Context.MODE_PRIVATE)
                                loginPrefs.edit().putString("last_logged_in_uid", updatedMe.id).apply()
                            }
                        }
                    }
                }
            }
        }

        // 2. Listen to "news" collection real-time
        db.collection("news").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of news failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val posts = snapshot.documents.mapNotNull { doc ->
                    val raw = doc.toObject(NewsPost::class.java)
                    raw?.copy(
                        id = doc.id,
                        postId = doc.id,
                        content = raw.content.ifEmpty { raw.description },
                        description = raw.description.ifEmpty { raw.content }
                    )
                }.sortedByDescending { it.timestamp }
                _newsPosts.value = posts
                saveNewsLocal()
            }
        }

        // 3. Listen to "novels" collection real-time
        db.collection("novels").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of novels failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val posts = snapshot.documents.mapNotNull { doc ->
                    val raw = doc.toObject(NovelPost::class.java)
                    raw?.copy(
                        id = doc.id,
                        novelId = doc.id,
                        author = raw.author.ifEmpty { raw.authorName }
                    )
                }.sortedByDescending { it.timestamp }
                _novelPosts.value = posts
                saveNovelsLocal()
            }
        }

        // 4. Listen to "thoughts" collection real-time
        db.collection("thoughts").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of thoughts failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val posts = snapshot.documents.mapNotNull { doc ->
                    val raw = doc.toObject(ThoughtPost::class.java)
                    raw?.copy(
                        id = doc.id,
                        thoughtId = doc.id
                    )
                }.sortedByDescending { it.timestamp }
                _thoughtPosts.value = posts
                saveThoughtsLocal()
            }
        }

        // 5. Listen to "notifications" collection real-time
        db.collection("notifications").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of notifications failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val raw = doc.toObject(Notification::class.java)
                    raw?.copy(
                        id = doc.id,
                        notificationId = doc.id,
                        receiverId = raw.receiverId.ifEmpty { raw.recipientId },
                        recipientId = raw.recipientId.ifEmpty { raw.receiverId },
                        senderPic = raw.senderPic.ifEmpty { raw.senderPhotoUrl },
                        senderPhotoUrl = raw.senderPhotoUrl.ifEmpty { raw.senderPic }
                    )
                }.sortedByDescending { it.timestamp }
                _notifications.value = list
                saveNotificationsLocal()
            }
        }

        // 6. Listen to "comments" collection real-time
        db.collection("comments").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of comments failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.copy(id = doc.id)
                }
                _comments.value = list
                saveCommentsLocal()
            }
        }

        // 7. Listen to "likes" collection real-time
        db.collection("likes").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of likes failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Like::class.java)?.copy(id = doc.id)
                }
                _likes.value = list
                saveLikesLocal()
            }
        }

        // 8. Listen to "admin_settings" config real-time
        db.collection("admin_settings").document("ads_config").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AppRepository", "Real-time sync of admin_settings failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val settings = snapshot.toObject(AdSettings::class.java)
                if (settings != null) {
                    _adSettings.value = settings
                    // Save locally to avoid loading lag next startup
                    try {
                        val file = File(context.filesDir, "ad_settings.json")
                        val adapter = moshi.adapter(AdSettings::class.java)
                        file.writeText(adapter.toJson(settings))
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Error cache saving settings in background", e)
                    }
                }
            }
        }
    }

    // --- LOCAL WRITING PERSISTENCE HELPERS ---
    private fun saveUsersLocal() = saveLocal("users.json", _users.value, User::class.java)
    private fun saveNewsLocal() = saveLocal("news.json", _newsPosts.value, NewsPost::class.java)
    private fun saveNovelsLocal() = saveLocal("novels.json", _novelPosts.value, NovelPost::class.java)
    private fun saveThoughtsLocal() = saveLocal("thoughts.json", _thoughtPosts.value, ThoughtPost::class.java)
    private fun saveCommentsLocal() = saveLocal("comments.json", _comments.value, Comment::class.java)
    private fun saveLikesLocal() = saveLocal("likes.json", _likes.value, Like::class.java)
    private fun saveNotificationsLocal() = saveLocal("notifications.json", _notifications.value, Notification::class.java)
    private fun saveSavedLocal() = saveLocalSet("saved_posts.json", _savedPostIds.value)
    
    fun saveAdSettingsLocal(settings: AdSettings) {
        _adSettings.value = settings
        try {
            val file = File(context.filesDir, "ad_settings.json")
            val adapter = moshi.adapter(AdSettings::class.java)
            file.writeText(adapter.toJson(settings))
        } catch (e: Exception) {
            Log.e("AppRepository", "Error saving ad settings locally", e)
        }
        if (FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("admin_settings")
                    .document("ads_config")
                    .set(settings)
            } catch (e: Exception) {
                Log.e("AppRepository", "Error saving ad settings to Firestore", e)
            }
        }
    }

    private fun <T> saveLocal(fileName: String, list: List<T>, clazz: Class<T>) {
        try {
            val file = File(context.filesDir, fileName)
            val listType = Types.newParameterizedType(List::class.java, clazz)
            val adapter = moshi.adapter<List<T>>(listType)
            file.writeText(adapter.toJson(list))
        } catch (e: Exception) {
            Log.e("AppRepository", "Error writing $fileName", e)
        }
    }

    private fun saveLocalSet(fileName: String, set: Set<String>) {
        try {
            val file = File(context.filesDir, fileName)
            val listType = Types.newParameterizedType(Set::class.java, String::class.java)
            val adapter = moshi.adapter<Set<String>>(listType)
            file.writeText(adapter.toJson(set))
        } catch (e: Exception) {
            Log.e("AppRepository", "Error writing $fileName", e)
        }
    }

    // --- AUTH FLOWS ---
    fun login(emailOrPhone: String, password: String): Result<User> {
        val loginPrefs = context.getSharedPreferences("telugu_auth_prefs", Context.MODE_PRIVATE)
        
        // Authenticate with Firebase if connected
        if (FirebaseState.isInitialized) {
            try {
                // If it contains "@", it's an email - standard email auth
                // If it is just a username, we will support signing in using standard email formatting or local lookup
                val emailToAuth = if (emailOrPhone.contains("@")) {
                    emailOrPhone
                } else {
                    // Try to map phone number or username to email first by checking the user list
                    _users.value.find { it.phoneNumber == emailOrPhone || it.name.equals(emailOrPhone, true) }?.email ?: ""
                }
                
                if (emailToAuth.isNotEmpty()) {
                    val authResult = Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(emailToAuth, password))
                    val firebaseUser = authResult.user
                    if (firebaseUser != null) {
                        // Successfully logged in via FirebaseAuth! Fetch profile from Firestore
                        val doc = Tasks.await(FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).get())
                        val loadedUser = doc.toObject(User::class.java)
                        if (loadedUser != null) {
                            if (loadedUser.isBlocked) {
                                return Result.failure(Exception("Your account has been blocked by administrators."))
                            }
                            _currentUser.value = loadedUser
                            // Save to local cache
                            val updatedUsersList = _users.value.filter { it.id != loadedUser.id } + loadedUser
                            _users.value = updatedUsersList
                            saveUsersLocal()
                            loginPrefs.edit().putString("last_logged_in_uid", loadedUser.id).apply()
                            return Result.success(loadedUser)
                        } else {
                            // If they exist in Auth but not in Firestore, create a default user profile
                            val newUser = User(
                                id = firebaseUser.uid,
                                name = firebaseUser.email?.substringBefore("@") ?: "User_${firebaseUser.uid.take(4)}",
                                email = firebaseUser.email ?: "",
                                profilePhotoUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
                                password = password,
                                isAdmin = firebaseUser.email?.lowercase()?.trim() == "sharvanipatel.ias@gmail.com"
                            )
                            Tasks.await(FirebaseFirestore.getInstance().collection("users").document(newUser.id).set(newUser))
                            _currentUser.value = newUser
                            val updatedUsersList = _users.value.filter { it.id != newUser.id } + newUser
                            _users.value = updatedUsersList
                            saveUsersLocal()
                            loginPrefs.edit().putString("last_logged_in_uid", newUser.id).apply()
                            return Result.success(newUser)
                        }
                    }
                } else if (!emailOrPhone.contains("@")) {
                    // Fallback to local authentication for offline or manual simulated phone accounts
                    val localUser = _users.value.find { it.phoneNumber == emailOrPhone }
                    if (localUser != null) {
                        if (localUser.isBlocked) {
                            return Result.failure(Exception("Your account has been blocked by administrators."))
                        }
                        val expectedPassword = if (localUser.password.isNotEmpty()) localUser.password else "123456"
                        if (expectedPassword == password) {
                            _currentUser.value = localUser
                            loginPrefs.edit().putString("last_logged_in_uid", localUser.id).apply()
                            return Result.success(localUser)
                        } else {
                            return Result.failure(Exception("Incorrect password. Please try again / తప్పు పాస్‌వర్డ్."))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AppRepository", "Firebase auth failed standard request", e)
                return Result.failure(Exception(e.localizedMessage ?: "Incorrect password or email. Please register or try again."))
            }
        }

        val foundUser = _users.value.find { 
            (it.email.equals(emailOrPhone, true) || it.phoneNumber == emailOrPhone)
        }
        
        return if (foundUser != null) {
            if (foundUser.isBlocked) {
                Result.failure(Exception("Your account has been blocked by administrators."))
            } else {
                // Determine expected password for credentials verification
                val expectedPassword = if (foundUser.password.isNotEmpty()) {
                    foundUser.password
                } else {
                    // Seed / Fallback passwords for built-in accounts
                    if (foundUser.email == "sharvanipatel.ias@gmail.com") "admin123"
                    else if (foundUser.email == "pranoy@example.com") "pranoy123"
                    else "123456"
                }

                if (expectedPassword == password) {
                    _currentUser.value = foundUser
                    loginPrefs.edit().putString("last_logged_in_uid", foundUser.id).apply()
                    Result.success(foundUser)
                } else {
                    Result.failure(Exception("Incorrect password. Please try again / తప్పు పాస్‌వర్డ్."))
                }
            }
        } else {
            Result.failure(Exception("No registered account found with this email/phone number. Please click Sign Up to register first / దయచేసి ముందుగా రిజిస్టర్ చేసుకోండి!"))
        }
    }

    fun loginWithGoogle(email: String, name: String, idToken: String?, photoUrl: String?): Result<User> {
        val loginPrefs = context.getSharedPreferences("telugu_auth_prefs", Context.MODE_PRIVATE)
        
        var finalId = java.util.UUID.randomUUID().toString()
        var finalPhotoUrl = photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde"

        if (FirebaseState.isInitialized && !idToken.isNullOrEmpty()) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = Tasks.await(FirebaseAuth.getInstance().signInWithCredential(credential))
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    finalId = firebaseUser.uid
                    finalPhotoUrl = firebaseUser.photoUrl?.toString() ?: finalPhotoUrl
                    
                    // Fetch profile from Firestore
                    val doc = Tasks.await(FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).get())
                    val loadedUser = doc.toObject(User::class.java)
                    if (loadedUser != null) {
                        if (loadedUser.isBlocked) {
                            return Result.failure(Exception("Your account has been blocked by administrators."))
                        }
                        _currentUser.value = loadedUser
                        val updatedUsersList = _users.value.filter { it.id != loadedUser.id } + loadedUser
                        _users.value = updatedUsersList
                        saveUsersLocal()
                        loginPrefs.edit().putString("last_logged_in_uid", loadedUser.id).apply()
                        return Result.success(loadedUser)
                    }
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Firebase Google credential sign in failed", e)
                return Result.failure(Exception(e.localizedMessage ?: "Google Auth sign in failed."))
            }
        }

        // If not found in live FirebaseFirestore, look up in local or create new
        val foundUser = _users.value.find { it.email.equals(email, true) || (FirebaseState.isInitialized && it.id == finalId) }
        return if (foundUser != null) {
            if (foundUser.isBlocked) {
                Result.failure(Exception("Your account has been blocked by administrators."))
            } else {
                _currentUser.value = foundUser
                loginPrefs.edit().putString("last_logged_in_uid", foundUser.id).apply()
                Result.success(foundUser)
            }
        } else {
            val newUser = User(
                id = finalId,
                userId = finalId,
                name = name,
                email = email,
                phoneNumber = "",
                profilePhotoUrl = finalPhotoUrl,
                isAdmin = email.lowercase().trim() == "sharvanipatel.ias@gmail.com",
                password = "" // connected via Google
            )
            val newList = _users.value + newUser
            _users.value = newList
            saveUsersLocal()
            _currentUser.value = newUser
            loginPrefs.edit().putString("last_logged_in_uid", newUser.id).apply()
            
            if (FirebaseState.isInitialized) {
                try {
                    Tasks.await(FirebaseFirestore.getInstance().collection("users").document(newUser.id).set(newUser))
                } catch (e: Exception) {
                    Log.e("FirebaseRegister", "Firestore write failure", e)
                }
            }
            Result.success(newUser)
        }
    }

    fun register(name: String, emailOrPhone: String, bio: String, password: String): Result<User> {
        val loginPrefs = context.getSharedPreferences("telugu_auth_prefs", Context.MODE_PRIVATE)
        val email = if (emailOrPhone.contains("@")) emailOrPhone else ""
        val phone = if (!emailOrPhone.contains("@")) emailOrPhone else ""

        // Validate uniqueness
        val exists = _users.value.any { 
            (email.isNotEmpty() && it.email.equals(email, true)) || (phone.isNotEmpty() && it.phoneNumber == phone)
        }
        if (exists) {
            return Result.failure(Exception("User with this email/phone number already registered / ఈ ఈమెయిల్ లేదా ఫోన్ నంబర్‌తో ప్రొఫైల్ ఇప్పటికే ఉంది."))
        }

        val isAdmin = email.lowercase().trim() == "sharvanipatel.ias@gmail.com"
        var generatedId = UUID.randomUUID().toString()

        if (FirebaseState.isInitialized && email.isNotEmpty()) {
            try {
                // Register via Firebase Authentication (using email + password)
                val authResult = Tasks.await(FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password))
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    generatedId = firebaseUser.uid
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Firebase auth manual registration failure", e)
                return Result.failure(Exception("Firebase registration failed: ${e.localizedMessage}"))
            }
        }

        val newUser = User(
            id = generatedId,
            name = name,
            email = email,
            phoneNumber = phone,
            profilePhotoUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
            bio = bio,
            isAdmin = isAdmin,
            password = password
        )

        val updatedUsers = _users.value + newUser
        _users.value = updatedUsers
        saveUsersLocal()

        _currentUser.value = newUser
        loginPrefs.edit().putString("last_logged_in_uid", newUser.id).apply()

        // Push to Firestore if cloud active
        if (FirebaseState.isInitialized) {
            try {
                Tasks.await(FirebaseFirestore.getInstance().collection("users").document(newUser.id).set(newUser))
            } catch (e: Exception) {
                Log.e("FirebaseRegister", "Firestore write failure", e)
            }
        }

        return Result.success(newUser)
    }

    fun updateProfile(name: String, bio: String, photoUrl: String): Boolean {
        val user = _currentUser.value ?: return false
        val updatedUser = user.copy(name = name, bio = bio, profilePhotoUrl = photoUrl)
        
        _currentUser.value = updatedUser
        
        val newList = _users.value.map { if (it.id == user.id) updatedUser else it }
        _users.value = newList
        saveUsersLocal()

        // Sync with Firestore
        if (FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance().collection("users").document(user.id).set(updatedUser)
            } catch (e: Exception) {
                Log.e("FirebaseUpdateUser", "Firestore write failure", e)
            }
        }
        return true
    }

    fun logout() {
        val loginPrefs = context.getSharedPreferences("telugu_auth_prefs", Context.MODE_PRIVATE)
        loginPrefs.edit().remove("last_logged_in_uid").apply()
        
        if (FirebaseState.isInitialized) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.e("FirebaseLogout", "Auth sign out failed", e)
            }
        }
        _currentUser.value = null
    }

    // --- SECTIONS POSTING ACTIONS ---
    fun submitNewsPost(state: String, district: String, title: String, description: String, imageUrl: String, status: PostStatus, moderationNotes: String = "") {
        val user = _currentUser.value ?: return
        val id = UUID.randomUUID().toString()
        val post = NewsPost(
            id = id,
            postId = id,
            authorId = user.id,
            authorName = user.name,
            authorPhoto = user.profilePhotoUrl,
            state = state,
            district = district,
            title = title,
            description = description,
            content = description,
            imageUrl = imageUrl.ifEmpty { "https://images.unsplash.com/photo-1504711434969-e33886168f5c" },
            status = status,
            moderationNotes = moderationNotes
        )
        _newsPosts.value = listOf(post) + _newsPosts.value
        saveNewsLocal()

        if (status == PostStatus.PENDING_REVIEW) {
            addNotification(
                recipientId = user.id,
                senderName = "AI Moderator",
                senderPhotoUrl = "🤖",
                type = "POST_PENDING",
                message = "Your news post is pending review under State $state / $district. AI safety verdict: $moderationNotes",
                postId = post.id,
                postType = "NEWS"
            )
        } else if (status == PostStatus.HIDDEN) {
            addNotification(
                recipientId = user.id,
                senderName = "AI Moderator",
                senderPhotoUrl = "🤖",
                type = "POST_REJECTED",
                message = "Your news post was auto-moderated and hidden. AI feedback: $moderationNotes",
                postId = post.id,
                postType = "NEWS"
            )
        }

        if (FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance().collection("news").document(post.id).set(post)
                writeToUserPosts(user.id, post.id, post)
            } catch (e: Exception) {
                Log.e("FirebaseNews", "Firestore post failure", e)
            }
        }
    }

    fun submitNovelPost(title: String, content: String, coverImageUrl: String, part: String, status: PostStatus, moderationNotes: String = "", customNovelId: String? = null) {
        val user = _currentUser.value ?: return
        val id = UUID.randomUUID().toString()
        val post = NovelPost(
            id = id,
            novelId = customNovelId ?: id,
            authorId = user.id,
            authorName = user.name,
            authorPhoto = user.profilePhotoUrl,
            author = user.name,
            title = title,
            content = content,
            coverImageUrl = coverImageUrl.ifEmpty { "https://images.unsplash.com/photo-1543002588-bfa74002ed7e" },
            part = part,
            status = status,
            moderationNotes = moderationNotes
        )
        _novelPosts.value = listOf(post) + _novelPosts.value
        saveNovelsLocal()

        if (status == PostStatus.PENDING_REVIEW) {
            addNotification(
                recipientId = user.id,
                senderName = "AI Moderator",
                senderPhotoUrl = "🤖",
                type = "POST_PENDING",
                message = "Novel draft '${post.title}' verification: $moderationNotes",
                postId = post.id,
                postType = "NOVEL"
            )
        } else if (status == PostStatus.HIDDEN) {
            addNotification(
                recipientId = user.id,
                senderName = "AI Moderator",
                senderPhotoUrl = "🤖",
                type = "POST_REJECTED",
                message = "Novel draft '${post.title}' was hidden. AI feedback: $moderationNotes",
                postId = post.id,
                postType = "NOVEL"
            )
        }

        if (FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance().collection("novels").document(post.id).set(post)
                writeToUserPosts(user.id, post.id, post)
            } catch (e: Exception) {
                Log.e("FirebaseNovel", "Firestore post failure", e)
            }
        }
    }

    fun submitThoughtPost(title: String, content: String, imageUrl: String, status: PostStatus, moderationNotes: String = "") {
        val user = _currentUser.value ?: return
        val id = UUID.randomUUID().toString()
        val post = ThoughtPost(
            id = id,
            thoughtId = id,
            authorId = user.id,
            authorName = user.name,
            authorPhoto = user.profilePhotoUrl,
            title = title,
            content = content,
            imageUrl = imageUrl.ifEmpty { "https://images.unsplash.com/photo-1499750310107-5fef28a66643" },
            status = status,
            moderationNotes = moderationNotes
        )
        _thoughtPosts.value = listOf(post) + _thoughtPosts.value
        saveThoughtsLocal()

        if (status == PostStatus.PENDING_REVIEW) {
            addNotification(
                recipientId = user.id,
                senderName = "AI Moderator",
                senderPhotoUrl = "🤖",
                type = "POST_PENDING",
                message = "Your thought is pending review. AI feedback: $moderationNotes",
                postId = post.id,
                postType = "THOUGHT"
            )
        } else if (status == PostStatus.HIDDEN) {
            addNotification(
                recipientId = user.id,
                senderName = "AI Moderator",
                senderPhotoUrl = "🤖",
                type = "POST_REJECTED",
                message = "Your thought draft was automatically hidden as unsafe. AI feedback: $moderationNotes",
                postId = post.id,
                postType = "THOUGHT"
            )
        }

        if (FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance().collection("thoughts").document(post.id).set(post)
                writeToUserPosts(user.id, post.id, post)
            } catch (e: Exception) {
                Log.e("FirebaseThought", "Firestore post failure", e)
            }
        }
    }

    fun editNewsPost(postId: String, state: String, district: String, title: String, description: String, imageUrl: String) {
        _newsPosts.value = _newsPosts.value.map {
            if (it.id == postId) {
                val updated = it.copy(
                    state = state,
                    district = district,
                    title = title,
                    description = description,
                    content = description,
                    imageUrl = imageUrl.ifEmpty { "https://images.unsplash.com/photo-1504711434969-e33886168f5c" }
                )
                if (FirebaseState.isInitialized) {
                    try {
                        FirebaseFirestore.getInstance().collection("news").document(postId).set(updated)
                        writeToUserPosts(updated.authorId, postId, updated)
                    } catch (e: Exception) {
                        Log.e("FirebaseNews", "Firestore edit failure", e)
                    }
                }
                updated
            } else {
                it
            }
        }
        saveNewsLocal()
    }

    fun editNovelPost(postId: String, title: String, content: String, coverImageUrl: String, part: String) {
        _novelPosts.value = _novelPosts.value.map {
            if (it.id == postId) {
                val updated = it.copy(
                    title = title,
                    content = content,
                    coverImageUrl = coverImageUrl.ifEmpty { "https://images.unsplash.com/photo-1543002588-bfa74002ed7e" },
                    part = part,
                    author = it.author.ifEmpty { it.authorName }
                )
                if (FirebaseState.isInitialized) {
                    try {
                        FirebaseFirestore.getInstance().collection("novels").document(postId).set(updated)
                        writeToUserPosts(updated.authorId, postId, updated)
                    } catch (e: Exception) {
                        Log.e("FirebaseNovel", "Firestore edit failure", e)
                    }
                }
                updated
            } else {
                it
            }
        }
        saveNovelsLocal()
    }

    fun editThoughtPost(postId: String, title: String, content: String, imageUrl: String) {
        _thoughtPosts.value = _thoughtPosts.value.map {
            if (it.id == postId) {
                val updated = it.copy(
                    title = title,
                    content = content,
                    imageUrl = imageUrl.ifEmpty { "https://images.unsplash.com/photo-1499750310107-5fef28a66643" }
                )
                if (FirebaseState.isInitialized) {
                    try {
                        FirebaseFirestore.getInstance().collection("thoughts").document(postId).set(updated)
                        writeToUserPosts(updated.authorId, postId, updated)
                    } catch (e: Exception) {
                        Log.e("FirebaseThought", "Firestore edit failure", e)
                    }
                }
                updated
            } else {
                it
            }
        }
        saveThoughtsLocal()
    }

    // --- COMMUNITY ACTIVITIES & SOCIAL INTERACTIONS ---
    fun toggleLike(postId: String, postType: PostType) {
        val user = _currentUser.value ?: return
        val existingLike = _likes.value.find { it.userId == user.id && it.postId == postId }
        val diff = if (existingLike != null) -1 else 1

        val creatorId = getPostCreatorId(postId, postType)

        if (existingLike != null) {
            // Unlike post
            _likes.value = _likes.value.filter { it.id != existingLike.id }
            saveLikesLocal()
            updateLikesCount(postId, postType, -1)

            if (FirebaseState.isInitialized) {
                try {
                    FirebaseFirestore.getInstance().collection("likes").document(existingLike.id).delete()
                } catch (e: Exception) {
                    Log.e("FirebaseLike", "Error deleting like doc", e)
                }
            }
        } else {
            // Like post
            val newLike = Like(userId = user.id, postId = postId, postType = postType)
            _likes.value = _likes.value + newLike
            saveLikesLocal()
            updateLikesCount(postId, postType, 1)

            if (FirebaseState.isInitialized) {
                try {
                    FirebaseFirestore.getInstance().collection("likes").document(newLike.id).set(newLike)
                } catch (e: Exception) {
                    Log.e("FirebaseLike", "Error creating like doc", e)
                }
            }

            // Dynamic notify post creator
            val postTitle = getPostTitle(postId, postType)
            if (creatorId != null && creatorId != user.id) {
                addNotification(
                    recipientId = creatorId,
                    senderName = user.name,
                    senderPhotoUrl = user.profilePhotoUrl,
                    type = "LIKE",
                    message = "${user.name} kotha like chesaru mee post paina: \"$postTitle\"",
                    postId = postId,
                    postType = postType.name
                )
            }
        }

        // Increment creator's totalIdLikes
        if (creatorId != null) {
            val targetCreator = _users.value.find { it.id == creatorId }
            if (targetCreator != null) {
                val updatedLikes = (targetCreator.totalIdLikes + diff).coerceAtLeast(0)
                val updatedCreator = targetCreator.copy(totalIdLikes = updatedLikes)
                _users.value = _users.value.map { if (it.id == creatorId) updatedCreator else it }
                saveUsersLocal()
                if (creatorId == user.id) {
                    _currentUser.value = updatedCreator
                }
                if (FirebaseState.isInitialized) {
                    try {
                        FirebaseFirestore.getInstance().collection("users").document(creatorId).update("totalIdLikes", updatedLikes)
                    } catch (e: Exception) {
                        Log.e("FirebaseLike", "Error updating creator's totalIdLikes: ${e.message}")
                    }
                }
            }
        }
    }

    fun isLiked(postId: String): Boolean {
        val user = _currentUser.value ?: return false
        return _likes.value.any { it.userId == user.id && it.postId == postId }
    }

    fun addComment(postId: String, postType: PostType, text: String) {
        val user = _currentUser.value ?: return
        val comment = Comment(
            postId = postId,
            postType = postType,
            authorId = user.id,
            authorName = user.name,
            authorPhotoUrl = user.profilePhotoUrl,
            text = text
        )

        _comments.value = _comments.value + comment
        saveCommentsLocal()
        updateCommentsCount(postId, postType, 1)

        if (FirebaseState.isInitialized) {
            try {
                val db = FirebaseFirestore.getInstance()
                // 1. Write to global comments collection (important for live snapshots)
                db.collection("comments").document(comment.id).set(comment)
                
                // 2. Write to nested sub-collection under specific post type
                val parentCollection = when (postType) {
                    PostType.NEWS -> "news"
                    PostType.NOVEL -> "novels"
                    PostType.THOUGHT -> "thoughts"
                }
                db.collection(parentCollection).document(postId).collection("comments").document(comment.id).set(comment)
            } catch (e: Exception) {
                Log.e("FirebaseComment", "Error creating comment doc in collections", e)
            }
        }

        // Notification logic
        val creatorId = getPostCreatorId(postId, postType)
        val postTitle = getPostTitle(postId, postType)
        if (creatorId != null && creatorId != user.id) {
            addNotification(
                recipientId = creatorId,
                senderName = user.name,
                senderPhotoUrl = user.profilePhotoUrl,
                type = "COMMENT",
                message = "${user.name} mee post \"$postTitle\" paina comment chesaru: \"$text\"",
                postId = postId,
                postType = postType.name
            )
        }
    }

    fun toggleSavePost(postId: String) {
        val currentSaves = _savedPostIds.value.toMutableSet()
        if (currentSaves.contains(postId)) {
            currentSaves.remove(postId)
        } else {
            currentSaves.add(postId)
        }
        _savedPostIds.value = currentSaves
        saveSavedLocal()

        // Sync to firebase user doc
        val user = _currentUser.value ?: return
        val list = currentSaves.toList()
        val updatedUser = user.copy(savedPosts = list)
        _currentUser.value = updatedUser
        
        // Also update usersList
        _users.value = _users.value.map { if (it.id == user.id) updatedUser else it }
        saveUsersLocal()

        if (FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance().collection("users").document(user.id).update("savedPosts", list)
            } catch (e: Exception) {
                Log.e("FirebaseSavePost", "Error updating savedPosts: ${e.message}")
            }
        }
    }

    fun isSaved(postId: String): Boolean {
        return _savedPostIds.value.contains(postId)
    }

    // Follow and unfollow users
    fun toggleFollow(targetUserId: String) {
        val current = _currentUser.value ?: return
        if (current.id == targetUserId) return // Cant follow yourself
        
        val targetUser = _users.value.find { it.id == targetUserId } ?: return
        val isFollowing = current.following.contains(targetUserId)

        val updatedCurrentUser: User
        val updatedTargetUser: User

        if (isFollowing) {
            // Unfollow
            val currentFollowing = current.following.filter { it != targetUserId }
            val targetFollowers = targetUser.followers.filter { it != current.id }
            updatedCurrentUser = current.copy(
                following = currentFollowing,
                followingCount = currentFollowing.size
            )
            updatedTargetUser = targetUser.copy(
                followers = targetFollowers,
                followersCount = targetFollowers.size
            )
        } else {
            // Follow
            val currentFollowing = current.following + targetUserId
            val targetFollowers = targetUser.followers + current.id
            updatedCurrentUser = current.copy(
                following = currentFollowing,
                followingCount = currentFollowing.size
            )
            updatedTargetUser = targetUser.copy(
                followers = targetFollowers,
                followersCount = targetFollowers.size
            )

            // Notify follow
            addNotification(
                recipientId = targetUserId,
                senderName = current.name,
                senderPhotoUrl = current.profilePhotoUrl,
                type = "FOLLOW",
                message = "${current.name} Mimmalni follow avvadam modalu pettaru!",
                postId = "",
                postType = ""
            )
        }

        _currentUser.value = updatedCurrentUser
        val newList = _users.value.map { 
            when (it.id) {
                current.id -> updatedCurrentUser
                targetUserId -> updatedTargetUser
                else -> it
            }
        }
        _users.value = newList
        saveUsersLocal()

        if (FirebaseState.isInitialized) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.runTransaction { transaction ->
                    val curRef = db.collection("users").document(current.id)
                    val tarRef = db.collection("users").document(targetUserId)
                    transaction.set(curRef, updatedCurrentUser)
                    transaction.set(tarRef, updatedTargetUser)
                    null
                }.addOnFailureListener { e ->
                    Log.e("FirebaseFollowSync", "Follow sync transaction failed", e)
                }
            } catch (e: Exception) {
                Log.e("FirebaseFollowSync", "Follow sync Firestore transaction failure", e)
            }
        }
    }

    // Report User or Post
    fun reportPost(postId: String, postType: PostType, reasoning: String) {
        val user = _currentUser.value ?: return
        Log.i("AppRepository", "Post reported: $postId by ${user.id} -> $reasoning")
        // Just logs and decreases visibility / sends to review in simulation. 
        // We can flag news/novel/thoughts as PENDING_REVIEW if we want to report:
        when (postType) {
            PostType.NEWS -> {
                _newsPosts.value = _newsPosts.value.map {
                    if (it.id == postId) it.copy(status = PostStatus.PENDING_REVIEW, moderationNotes = "Reported: $reasoning") else it
                }
                saveNewsLocal()
            }
            PostType.NOVEL -> {
                _novelPosts.value = _novelPosts.value.map {
                    if (it.id == postId) it.copy(status = PostStatus.PENDING_REVIEW, moderationNotes = "Reported: $reasoning") else it
                }
                saveNovelsLocal()
            }
            PostType.THOUGHT -> {
                _thoughtPosts.value = _thoughtPosts.value.map {
                    if (it.id == postId) it.copy(status = PostStatus.PENDING_REVIEW, moderationNotes = "Reported: $reasoning") else it
                }
                saveThoughtsLocal()
            }
        }
    }

    fun reportUser(reportedUserId: String, reason: String) {
        val reporter = _currentUser.value ?: return
        Log.i("AppRepository", "User reported: $reportedUserId by reporter ${reporter.id} for: $reason")
    }

    fun blockUser(targetUserId: String) {
        // Blocks user in local configuration by flagging, only admins can block or toggle standard user block
        val target = _users.value.find { it.id == targetUserId } ?: return
        val updated = target.copy(isBlocked = !target.isBlocked)
        
        val newList = _users.value.map { if (it.id == targetUserId) updated else it }
        _users.value = newList
        saveUsersLocal()
    }

    // --- ADMIN ACTIONS ONLY ---
    fun setPostStatus(postId: String, postType: PostType, newStatus: PostStatus) {
        val authorId = getPostCreatorId(postId, postType) ?: ""
        val postTitle = getPostTitle(postId, postType)
        
        when (postType) {
            PostType.NEWS -> {
                _newsPosts.value = _newsPosts.value.map {
                    if (it.id == postId) {
                        it.copy(status = newStatus, moderationNotes = "Admin update: $newStatus")
                    } else it
                }
                saveNewsLocal()
                syncPostToFirestore(postId, PostType.NEWS)
            }
            PostType.NOVEL -> {
                _novelPosts.value = _novelPosts.value.map {
                    if (it.id == postId) {
                        it.copy(status = newStatus, moderationNotes = "Admin update: $newStatus")
                    } else it
                }
                saveNovelsLocal()
                syncPostToFirestore(postId, PostType.NOVEL)
            }
            PostType.THOUGHT -> {
                _thoughtPosts.value = _thoughtPosts.value.map {
                    if (it.id == postId) {
                        it.copy(status = newStatus, moderationNotes = "Admin update: $newStatus")
                    } else it
                }
                saveThoughtsLocal()
                syncPostToFirestore(postId, PostType.THOUGHT)
            }
        }

        // Notify user about post moderation outcome
        if (authorId.isNotEmpty()) {
            val message = when (newStatus) {
                PostStatus.PUBLISHED -> "Mee post \"$postTitle\" Admin dwara prachurinchabadindi."
                PostStatus.REJECTED -> "Mee post \"$postTitle\" Admin dwara tiraskarinchabadindi."
                PostStatus.HIDDEN -> "Mee post \"$postTitle\" policies ullanghana valla tholaginchabadindi."
                else -> ""
            }
            if (message.isNotEmpty()) {
                addNotification(
                    recipientId = authorId,
                    senderName = "Admin Team",
                    senderPhotoUrl = "👑",
                    type = if (newStatus == PostStatus.PUBLISHED) "POST_APPROVED" else "POST_REJECTED",
                    message = message,
                    postId = postId,
                    postType = postType.name
                )
            }
        }
    }

    fun deletePost(postId: String, postType: PostType) {
        val creatorId = getPostCreatorId(postId, postType)
        when (postType) {
            PostType.NEWS -> {
                _newsPosts.value = _newsPosts.value.filter { it.id != postId }
                saveNewsLocal()
                if (FirebaseState.isInitialized) {
                    try {
                        FirebaseFirestore.getInstance().collection("news").document(postId).delete()
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Error deleting news: ${e.message}")
                    }
                }
            }
            PostType.NOVEL -> {
                _novelPosts.value = _novelPosts.value.filter { it.id != postId }
                saveNovelsLocal()
                if (FirebaseState.isInitialized) {
                    try {
                        FirebaseFirestore.getInstance().collection("novels").document(postId).delete()
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Error deleting novel: ${e.message}")
                    }
                }
            }
            PostType.THOUGHT -> {
                _thoughtPosts.value = _thoughtPosts.value.filter { it.id != postId }
                saveThoughtsLocal()
                if (FirebaseState.isInitialized) {
                    try {
                        FirebaseFirestore.getInstance().collection("thoughts").document(postId).delete()
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Error deleting thought: ${e.message}")
                    }
                }
            }
        }
        if (creatorId != null && FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(creatorId)
                    .collection("user_posts")
                    .document(postId)
                    .delete()
            } catch (e: Exception) {
                Log.e("AppRepository", "Error deleting subcollection post: ${e.message}")
            }
        }
    }

    fun dismissNotification(id: String) {
        _notifications.value = _notifications.value.filter { it.id != id }
        saveNotificationsLocal()
    }

    fun markNotificationsRead() {
        val user = _currentUser.value ?: return
        _notifications.value = _notifications.value.map {
            if (it.recipientId == user.id) it.copy(isRead = true) else it
        }
        saveNotificationsLocal()
    }

    fun markAllNotificationsAsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        saveNotificationsLocal()
        if (FirebaseState.isInitialized) {
            scope.launch {
                try {
                    val db = FirebaseFirestore.getInstance()
                    _notifications.value.forEach { notif ->
                        db.collection("notifications").document(notif.id).update("isRead", true)
                    }
                    Log.d("AppRepository", "Successfully synced all read notifications to Firestore.")
                } catch (e: Exception) {
                    Log.e("AppRepository", "Failed sync of bulk read notifications to Firestore: ${e.message}")
                }
            }
        }
    }

    // Helper functions
    private fun addNotification(recipientId: String, senderName: String, senderPhotoUrl: String, type: String, message: String, postId: String, postType: String) {
        // 1. Self Action Filter: Block self-notifications (actorUserId == targetUserId)
        val currentUserId = _currentUser.value?.id ?: ""
        if (currentUserId.isNotEmpty() && currentUserId == recipientId) {
            Log.d("AppRepository", "Self-Action Filter: Blocked self notification for user ID $currentUserId")
            return
        }

        // 2. Deduplication check: Silently update the timestamp of the existing matching action notification
        val existingNotif = _notifications.value.find {
            it.recipientId == recipientId && it.type == type && it.postId == postId && it.senderName == senderName
        }
        if (existingNotif != null) {
            val updatedNotif = existingNotif.copy(timestamp = System.currentTimeMillis())
            _notifications.value = _notifications.value.map { if (it.id == existingNotif.id) updatedNotif else it }
            saveNotificationsLocal()
            if (FirebaseState.isInitialized) {
                try {
                    FirebaseFirestore.getInstance().collection("notifications").document(updatedNotif.id).set(updatedNotif)
                } catch (e: Exception) {
                    Log.e("FirebaseNotification", "Deduplication update failure: ${e.message}")
                }
            }
            Log.d("AppRepository", "Deduplication mechanism engaged: Updated notification timestamp silently.")
            return
        }

        val finalId = UUID.randomUUID().toString()
        val notif = Notification(
            id = finalId,
            notificationId = finalId,
            recipientId = recipientId,
            receiverId = recipientId,
            senderName = senderName,
            senderPhotoUrl = senderPhotoUrl,
            senderPic = senderPhotoUrl,
            type = type,
            message = message,
            postId = postId,
            postType = postType
        )
        _notifications.value = listOf(notif) + _notifications.value
        saveNotificationsLocal()

        if (FirebaseState.isInitialized) {
            try {
                FirebaseFirestore.getInstance().collection("notifications").document(notif.id).set(notif)
            } catch (e: Exception) {
                Log.e("FirebaseNotification", "Error creating notification doc: ${e.message}")
            }
        }

        // 3. Only critical system updates should trigger high-priority push alerts; general engagement items sync silently in-app
        val isCriticalSystemUpdate = type == "POST_APPROVED" || 
                                     type == "POST_REJECTED" || 
                                     type == "POST_PENDING" || 
                                     type.contains("SYSTEM", ignoreCase = true) || 
                                     senderName.equals("AI Moderator", ignoreCase = true) || 
                                     senderName.equals("Admin Team", ignoreCase = true)

        if (isCriticalSystemUpdate) {
            try {
                com.example.services.NotificationHelper.showPushNotification(
                    context = context,
                    title = "$senderName (${type.lowercase().replace("_", " ")})",
                    message = message,
                    postId = postId.ifEmpty { null },
                    postType = postType.ifEmpty { null }
                )
            } catch (e: Exception) {
                Log.e("AppRepository", "Failed to dispatch push notification UI: ${e.message}")
            }
        } else {
            Log.d("AppRepository", "Silent engagement notification only synced in-app: $type")
        }
    }

    private fun updateLikesCount(postId: String, postType: PostType, diff: Int) {
        when (postType) {
            PostType.NEWS -> {
                _newsPosts.value = _newsPosts.value.map {
                    if (it.id == postId) it.copy(likesCount = (it.likesCount + diff).coerceAtLeast(0)) else it
                }
                saveNewsLocal()
            }
            PostType.NOVEL -> {
                _novelPosts.value = _novelPosts.value.map {
                    if (it.id == postId) it.copy(likesCount = (it.likesCount + diff).coerceAtLeast(0)) else it
                }
                saveNovelsLocal()
            }
            PostType.THOUGHT -> {
                _thoughtPosts.value = _thoughtPosts.value.map {
                    if (it.id == postId) it.copy(likesCount = (it.likesCount + diff).coerceAtLeast(0)) else it
                }
                saveThoughtsLocal()
            }
        }
        syncPostToFirestore(postId, postType)
    }

    private fun updateCommentsCount(postId: String, postType: PostType, diff: Int) {
        when (postType) {
            PostType.NEWS -> {
                _newsPosts.value = _newsPosts.value.map {
                    if (it.id == postId) it.copy(commentsCount = (it.commentsCount + diff).coerceAtLeast(0)) else it
                }
                saveNewsLocal()
            }
            PostType.NOVEL -> {
                _novelPosts.value = _novelPosts.value.map {
                    if (it.id == postId) it.copy(commentsCount = (it.commentsCount + diff).coerceAtLeast(0)) else it
                }
                saveNovelsLocal()
            }
            PostType.THOUGHT -> {
                _thoughtPosts.value = _thoughtPosts.value.map {
                    if (it.id == postId) it.copy(commentsCount = (it.commentsCount + diff).coerceAtLeast(0)) else it
                }
                saveThoughtsLocal()
            }
        }
        syncPostToFirestore(postId, postType)
    }

    private fun syncPostToFirestore(postId: String, postType: PostType) {
        if (FirebaseState.isInitialized) {
            try {
                val db = FirebaseFirestore.getInstance()
                when (postType) {
                    PostType.NEWS -> {
                        _newsPosts.value.find { it.id == postId }?.let {
                            db.collection("news").document(postId).set(it)
                            writeToUserPosts(it.authorId, postId, it)
                        }
                    }
                    PostType.NOVEL -> {
                        _novelPosts.value.find { it.id == postId }?.let {
                            db.collection("novels").document(postId).set(it)
                            writeToUserPosts(it.authorId, postId, it)
                        }
                    }
                    PostType.THOUGHT -> {
                        _thoughtPosts.value.find { it.id == postId }?.let {
                            db.collection("thoughts").document(postId).set(it)
                            writeToUserPosts(it.authorId, postId, it)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Error uploading updated post: ${e.message}")
            }
        }
    }

    private fun getPostCreatorId(postId: String, postType: PostType): String? {
        return when (postType) {
            PostType.NEWS -> _newsPosts.value.find { it.id == postId }?.authorId
            PostType.NOVEL -> _novelPosts.value.find { it.id == postId }?.authorId
            PostType.THOUGHT -> _thoughtPosts.value.find { it.id == postId }?.authorId
        }
    }

    private fun getPostTitle(postId: String, postType: PostType): String {
        return when (postType) {
            PostType.NEWS -> _newsPosts.value.find { it.id == postId }?.title ?: "News Post"
            PostType.NOVEL -> _novelPosts.value.find { it.id == postId }?.title ?: "Novel"
            PostType.THOUGHT -> _thoughtPosts.value.find { it.id == postId }?.title ?: "Thought"
        }
    }

    // Seed Data News
    private fun seedInitialNews() {
        _newsPosts.value = listOf(
            NewsPost(
                authorName = "Telangana Samacharam Team",
                authorPhoto = "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
                state = "Telangana",
                district = "Hyderabad",
                title = "ఐటీ రంగంలో దూసుకుపోతున్న హైదరాబాద్: సరికొత్త రికార్డులు!",
                description = "హైదరాబాద్ నగరం ఐటీ విభాగంలో దేశంలోనే ముందంజలో నిలుస్తోంది. కొత్త స్టార్టప్‌లు మరియు బహుళజాతి కంపెనీల స్థాపనతో వేలాది మంది యువతీ యువకులకు ఉపాధి అవకాశాలు లభిస్తున్నాయి. ఈ ఏడాది రికార్డు స్థాయిలో విదేశీ పెట్టుబడులు తరలివచ్చినట్టు నివేదికలు స్పష్టం చేస్తున్నాయి.",
                imageUrl = "https://images.unsplash.com/photo-1519125323398-675f0ddb6308",
                status = PostStatus.PUBLISHED,
                likesCount = 145,
                commentsCount = 12
            ),
            NewsPost(
                authorName = "Andhra News Network",
                authorPhoto = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d",
                state = "Andhra Pradesh",
                district = "Visakhapatnam",
                title = "విశాఖపట్నం సముద్ర తీరంలో పర్యాటక రంగ అభివృద్ధి ప్రణాళికలు",
                description = "ఆంధ్రప్రదేశ్ ప్రభుత్వం విశాఖ బీచ్ రోడ్డును అంతర్జాతీయ స్థాయి పర్యాటక కేంద్రంగా మార్చేందుకు భారీ నిధులు కేటాయించింది. వాటర్ స్పోర్ట్స్, అధునాతన హోటళ్లు మరియు పర్యావరణ అనుకూల రిసార్టుల ఏర్పాటుకు త్వరలోనే పనులు ప్రారంభం కానున్నాయి.",
                imageUrl = "https://images.unsplash.com/photo-1506973035872-a4ec16b8e8d9",
                status = PostStatus.PUBLISHED,
                likesCount = 98,
                commentsCount = 6
            )
        )
        saveNewsLocal()
    }

    private fun seedInitialNovels() {
        _novelPosts.value = listOf(
            NovelPost(
                authorName = "శ్రీనివాస రావు",
                authorPhoto = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e",
                title = "అనంత కాల చక్రం - భాగం 1",
                content = """
                    ఆ రోజు రాత్రి వర్షం కురుస్తోంది. పెద్దగా వీస్తున్న గాలికి చెట్ల కొమ్మలు ఊగుతున్నాయి. ప్రసాద్ తన గదిలో కూర్చుని పాత తాళపత్ర గ్రంథాలను పరిశీలిస్తున్నాడు. 
                    అందులో రాసి ఉన్న విషయాలు అతని మదిని తొలుస్తున్నాయి. దాదాపు ఐదు వందల సంవత్సరాల క్రితం రాయబడిన ఒక రహస్యం ఇప్పుడు అతని కళ్ళ ముందుకు వచ్చింది.
                    
                    "ఈ తావళం ఎవరైతే దక్కించుకుంటారో, వారు కాల ప్రయాణం చేయగలరు," అని రాసి ఉంది. ప్రసాద్ నమ్మలేకపోయాడు. కానీ, గ్రంథంలో వర్ణించిన ఆ కాల యంత్ర ముద్ర సరిగ్గా అతని చేతిలో ఉన్న బంగారు నాణెం ముద్రతో సరిపోలింది!
                    
                    ఆలోచనలలో మునిగిపోయిన అతనికి ఒక్కసారిగా కాలింగ్ బెల్ శబ్దం వినిపించింది. ప్రసాద్ ఉలిక్కిపడ్డాడు. ఇంత అర్ధరాత్రి తన ఇంటికి ఎవరు వచ్చి ఉంటారు? 
                    అతను మెల్లగా తలుపు దగ్గరకు వెళ్ళాడు...
                """.trimIndent(),
                coverImageUrl = "https://images.unsplash.com/photo-1544947950-fa07a98d237f",
                part = "Part 1 / భాగం 1",
                status = PostStatus.PUBLISHED,
                likesCount = 220,
                commentsCount = 18
            ),
            NovelPost(
                authorName = "సుజాత శేఖర్",
                authorPhoto = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
                title = "నీలి మేఘాల నీడలో",
                content = """
                    పల్లెటూరి అందాలు ఎప్పుడూ మనసుకు ప్రశాంతతను ఇస్తాయి. గోదావరి గట్టు పక్కన ఉన్న ఆ చిన్న గ్రామంలో లావణ్య తన అమ్మమ్మ ఇంట్లో సెలవులను గడుపుతోంది. 
                    ఉదయాన్నే పక్షుల కిలకిలారావాలు, కమ్మని పల్లెటూరి వంటల వాసన, పొలాలలో రైతుల పాటలు ఆమె జీవితానికి కొత్త ఉత్సాహాన్ని తెచ్చాయి.
                    
                    కానీ, ఆ ప్రశాంతత ఎక్కువ రోజులు నిలవలేదు. పల్లెటూరు చెరువును కబ్జా చేయాలని ఒక పెద్ద నగరానికి చెందిన కాంట్రాక్టర్ వచ్చాడు. ఊరి జనాన్ని ఒప్పించి ఆ చెరువును ఎండగట్టి షాపింగ్ మాల్ కట్టాలన్నది అతని ప్లాన్.
                    
                    `లావణ్య ఊరి యువకులను కూడగట్టి ఆ చెరువును, గ్రామ ప్రకృతిని ఎలా కాపాడుకుందో ఈ ప్రణయ భరిత నవల కథాంశం...
                """.trimIndent(),
                coverImageUrl = "https://images.unsplash.com/photo-1512820790803-83ca734da794",
                part = "Part 1 / భాగం 1",
                status = PostStatus.PUBLISHED,
                likesCount = 184,
                commentsCount = 14
            )
        )
        saveNovelsLocal()
    }

    private fun seedInitialThoughts() {
        _thoughtPosts.value = listOf(
            ThoughtPost(
                authorName = "ఆత్మకూరు విజయ్",
                authorPhoto = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e",
                title = "విజయం వైపు తొలి అడుగు",
                content = "మనం గొప్ప కార్యాలు సాధించలేకపోవచ్చు, కానీ చేసే చిన్న పనులను కూడా గొప్ప ప్రేమతో, అంకితభావంతో చేయడం వల్ల అద్భుతాలు సృష్టించవచ్చు. విజయానికి ఏకైక మార్గం నిరంతరం శ్రమించడం మరియు వైఫల్యాల నుండి నేర్చుకోవడం.",
                imageUrl = "https://images.unsplash.com/photo-1506126613408-eca07ce68773",
                status = PostStatus.PUBLISHED,
                likesCount = 312,
                commentsCount = 25
            ),
            ThoughtPost(
                authorName = "పల్లవి కిరణ్",
                authorPhoto = "https://images.unsplash.com/photo-1494790108377-be9c29b29330",
                title = "మాతృభాషా మధురిమ",
                content = "మన ఆలోచనలను అత్యంత స్వచ్ఛంగా వ్యక్తపరచడానికి అవసరమైన అమృత బిందువు మన అమ్మభాష. భాషను కాపాడుకోవడం అంటే మన సంస్కృతిని, తరాల వారసత్వాన్ని కాపాడుకోవడమే. మన మాతృభాష తెలుగుని గౌరవిద్దాం, ప్రేమిద్దాం.",
                imageUrl = "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8",
                status = PostStatus.PUBLISHED,
                likesCount = 425,
                commentsCount = 38
            )
        )
        saveThoughtsLocal()
    }

    fun updateFCMTokenInFirestore(token: String) {
        val current = _currentUser.value
        if (current != null) {
            val updatedUser = current.copy(fcmToken = token)
            _currentUser.value = updatedUser
            _users.value = _users.value.map { if (it.id == current.id) updatedUser else it }
            saveUsersLocal()
            
            if (FirebaseState.isInitialized) {
                scope.launch {
                    try {
                        FirebaseFirestore.getInstance().collection("users").document(current.id).update("fcmToken", token)
                        Log.d("AppRepository", "FCM token updated in Firestore for user: ${current.name}")
                    } catch (e: Exception) {
                        try {
                            FirebaseFirestore.getInstance().collection("users").document(current.id).set(updatedUser)
                        } catch (ex: Exception) {
                            Log.e("AppRepository", "FCM update set style failed: ${ex.message}")
                        }
                    }
                }
            }
        } else {
            val sharedPrefs = context.getSharedPreferences("telugu_fcm_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("fcm_token", token).apply()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AppRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
