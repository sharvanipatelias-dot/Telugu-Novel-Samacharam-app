package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.repository.AppRepository
import com.example.services.GeminiModerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    
    // Splash screen state
    var showSplash by mutableStateOf(true)
        private set

    // Connection indicator
    val connectionStatus = repository.connectionStatus

    // Current screen flow
    var currentScreen by mutableStateOf("SPLASH") // "SPLASH", "LOGIN", "REGISTER", "MAIN"
    var activeBottomTab by mutableStateOf("NEWS") // "NEWS", "NOVEL", "EXPLORE", "THOUGHTS", "PROFILE"

    // Authentication States
    var authEmailOrPhone by mutableStateOf("")
    var authPassword by mutableStateOf("")
    var registerName by mutableStateOf("")
    var registerBio by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var authError by mutableStateOf<String?>(null)
    var isAuthLoading by mutableStateOf(false)

    // Current user state flow
    val currentUser = repository.currentUser
    val allUsers = repository.usersList

    // Bottom sheets / Dialog overlays states
    var showPostDialog by mutableStateOf(false) // Toggle + Posting screen
    var showEditProfileDialog by mutableStateOf(false) // Edit Profile overlay screen
    var activeReaderNovel by mutableStateOf<NovelPost?>(null) // Novel reading mode
    var activeReaderNews by mutableStateOf<NewsPost?>(null) // News deep readers
    var activeReaderThought by mutableStateOf<ThoughtPost?>(null) // Thoughts deep readers
    var activeCommentsPostId by mutableStateOf<String?>(null) // Show comments list sheet
    var activeCommentsPostType by mutableStateOf<PostType>(PostType.NEWS)
    var activeWebViewUrl by mutableStateOf<String?>(null) // In-app web browser URL
    var activeWebViewTitle by mutableStateOf<String?>(null) // In-app web browser Title

    // User Coin wallet (Playstore Earning Policy context)
    private val prefs = application.getSharedPreferences("telugu_coins_prefs", android.content.Context.MODE_PRIVATE)
    var userCoins by mutableStateOf(prefs.getInt("user_coins_count", 150))
        private set

    fun earnCoins(amount: Int) {
        userCoins += amount
        prefs.edit().putInt("user_coins_count", userCoins).apply()
    }

    // POST DIALOG INPUT STATES
    var newsStateInput by mutableStateOf("")
    var newsDistrictInput by mutableStateOf("")
    var newsTitleInput by mutableStateOf("")
    var newsImgInput by mutableStateOf("")
    var newsDescInput by mutableStateOf("")

    var novelTitleInput by mutableStateOf("")
    var novelCoverInput by mutableStateOf("")
    var novelPartInput by mutableStateOf("Part 1")
    var novelContentInput by mutableStateOf("")

    var thoughtTitleInput by mutableStateOf("")
    var thoughtImgInput by mutableStateOf("")
    var thoughtContentInput by mutableStateOf("")

    var editingPostId by mutableStateOf<String?>(null)

    var postProgressMessage by mutableStateOf<String?>(null)
    var isPostUploading by mutableStateOf(false)

    // FEEDS STATE FLOWS (ONLY COMPLETED/PUBLISHED POSTS SHOWN TO NORMAL USERS)
    val newsPosts = repository.newsPosts
    val novelPosts = repository.novelPosts
    val thoughtPosts = repository.thoughtPosts
    val notifications = repository.notifications
    val adSettings = repository.adSettings
    val savedPostIds = repository.savedPostIds

    // User-specific personal streams to fix Profile filtering strictly from Firestore
    val currentUserNews = MutableStateFlow<List<NewsPost>>(emptyList())
    val currentUserNovels = MutableStateFlow<List<NovelPost>>(emptyList())
    val currentUserThoughts = MutableStateFlow<List<ThoughtPost>>(emptyList())

    var novelIdForNewPart: String? = null // For novel add part functionality

    // State & District filters for News feed
    var filterState by mutableStateOf("")
    var filterDistrict by mutableStateOf("")

    // Admin panel specific states
    var showAdminPanel by mutableStateOf(false)
    var adminActiveSubTab by mutableStateOf("QUEUE") // "DASHBOARD", "QUEUE", "USERS", "ADS"

    init {
        // Kickoff splash screen timing transition
        viewModelScope.launch {
            delay(2500)
            showSplash = false
            if (currentUser.value != null) {
                currentScreen = "MAIN"
            } else {
                currentScreen = "LOGIN"
            }
        }

        // Reactively load personal posts strictly from Firestore on user transitions
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    loadUserProfilePosts(user.id)
                } else {
                    currentUserNews.value = emptyList()
                    currentUserNovels.value = emptyList()
                    currentUserThoughts.value = emptyList()
                }
            }
        }
    }

    // --- AUTH ACTIONS ---
    fun login() {
        if (authEmailOrPhone.isEmpty() || authPassword.isEmpty()) {
            authError = "Please specify credentials."
            return
        }
        isAuthLoading = true
        authError = null
         
        viewModelScope.launch(Dispatchers.IO) {
            delay(800) // simulation delay for sleek look
            val result = repository.login(authEmailOrPhone, authPassword)
            withContext(Dispatchers.Main) {
                isAuthLoading = false
                if (result.isSuccess) {
                    currentScreen = "MAIN"
                    // Auto subscribe message
                    Log.d("AppViewModel", "Subscribed to global news-feed messaging notifications")
                } else {
                    authError = result.exceptionOrNull()?.message ?: "Login failed."
                }
            }
        }
    }

    fun loginWithGoogle(email: String, name: String, idToken: String?, photoUrl: String?) {
        isAuthLoading = true
        authError = null
        
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.loginWithGoogle(email, name, idToken, photoUrl)
            withContext(Dispatchers.Main) {
                isAuthLoading = false
                if (result.isSuccess) {
                    currentScreen = "MAIN"
                    Log.d("AppViewModel", "Google OAuth profile connected & logged in successfully.")
                } else {
                    authError = result.exceptionOrNull()?.message ?: "Google sign in failed."
                }
            }
        }
    }

    fun register() {
        if (registerName.isEmpty() || authEmailOrPhone.isEmpty() || authPassword.isEmpty()) {
            authError = "All fields are required."
            return
        }
        if (authPassword != confirmPassword) {
            authError = "Passwords do not match."
            return
        }
        if (authPassword.length < 6) {
            authError = "Password must be at least 6 characters."
            return
        }

        isAuthLoading = true
        authError = null

        viewModelScope.launch(Dispatchers.IO) {
            delay(800)
            val result = repository.register(registerName, authEmailOrPhone, registerBio, authPassword)
            withContext(Dispatchers.Main) {
                isAuthLoading = false
                if (result.isSuccess) {
                    currentScreen = "MAIN"
                } else {
                    authError = result.exceptionOrNull()?.message ?: "Registration failed."
                }
            }
        }
    }

    fun logout() {
        repository.logout()
        authEmailOrPhone = ""
        authPassword = ""
        registerName = ""
        confirmPassword = ""
        registerBio = ""
        currentScreen = "LOGIN"
        showAdminPanel = false
    }

    fun updateProfile(name: String, bio: String, photoUrl: String) {
        repository.updateProfile(name, bio, photoUrl)
    }

    // --- POSTING & GEMINI AI MODERATION ENGINE ---
    fun submitPost() {
        val user = currentUser.value ?: return
        isPostUploading = true
        
        viewModelScope.launch {
            try {
                val currentEditingId = editingPostId
                if (currentEditingId != null) {
                    if (activeBottomTab == "NEWS") {
                        repository.editNewsPost(
                            postId = currentEditingId,
                            state = newsStateInput.trim(),
                            district = newsDistrictInput.trim(),
                            title = newsTitleInput.trim(),
                            description = newsDescInput.trim(),
                            imageUrl = newsImgInput.trim()
                        )
                    } else if (activeBottomTab == "NOVEL") {
                        repository.editNovelPost(
                            postId = currentEditingId,
                            title = novelTitleInput.trim(),
                            content = novelContentInput.trim(),
                            coverImageUrl = novelCoverInput.trim(),
                            part = novelPartInput.trim()
                        )
                    } else if (activeBottomTab == "THOUGHTS") {
                        repository.editThoughtPost(
                            postId = currentEditingId,
                            title = thoughtTitleInput.trim(),
                            content = thoughtContentInput.trim(),
                            imageUrl = thoughtImgInput.trim()
                        )
                    }
                } else {
                    if (activeBottomTab == "NEWS") {
                        postProgressMessage = "Gemini AI Security & Quality check..."
                        val modResult = GeminiModerator.moderateContent(newsTitleInput, newsDescInput)
                        
                        postProgressMessage = "Processing news post..."
                        delay(1000)
                        
                        val status = if (modResult.isSafe) PostStatus.PUBLISHED else PostStatus.PENDING_REVIEW
                        repository.submitNewsPost(
                            state = newsStateInput.trim(),
                            district = newsDistrictInput.trim(),
                            title = newsTitleInput.trim(),
                            description = newsDescInput.trim(),
                            imageUrl = newsImgInput.trim(),
                            status = status,
                            moderationNotes = modResult.reason
                        )
                    } else if (activeBottomTab == "NOVEL") {
                        postProgressMessage = "Gemini AI Creative Content screening..."
                        val modResult = GeminiModerator.moderateContent(novelTitleInput, novelContentInput)
                        
                        postProgressMessage = "Processing novel chapter..."
                        delay(1000)
                        
                        val status = if (modResult.isSafe) PostStatus.PUBLISHED else PostStatus.PENDING_REVIEW
                        repository.submitNovelPost(
                            title = novelTitleInput.trim(),
                            content = novelContentInput.trim(),
                            coverImageUrl = novelCoverInput.trim(),
                            part = novelPartInput.trim(),
                            status = status,
                            moderationNotes = modResult.reason,
                            customNovelId = novelIdForNewPart
                        )
                    } else if (activeBottomTab == "THOUGHTS") {
                        postProgressMessage = "Gemini AI Thought safety verification..."
                        val modResult = GeminiModerator.moderateContent(thoughtTitleInput, thoughtContentInput)
                        
                        postProgressMessage = "Processing thoughts feed..."
                        delay(1000)
                        
                        val status = if (modResult.isSafe) PostStatus.PUBLISHED else PostStatus.PENDING_REVIEW
                        repository.submitThoughtPost(
                            title = thoughtTitleInput.trim(),
                            content = thoughtContentInput.trim(),
                            imageUrl = thoughtImgInput.trim(),
                            status = status,
                            moderationNotes = modResult.reason
                        )
                    }
                }

                // Reset inputs
                clearPostInputs()
                showPostDialog = false
            } catch (e: Exception) {
                Log.e("PostUpload", "Submittal crash handled", e)
            } finally {
                isPostUploading = false
                postProgressMessage = null
            }
        }
    }

    fun clearPostInputs() {
        newsStateInput = ""
        newsDistrictInput = ""
        newsTitleInput = ""
        newsImgInput = ""
        newsDescInput = ""
        
        novelTitleInput = ""
        novelCoverInput = ""
        novelPartInput = "Part 1"
        novelContentInput = ""
        
        thoughtTitleInput = ""
        thoughtImgInput = ""
        thoughtContentInput = ""

        editingPostId = null
        novelIdForNewPart = null
    }

    fun startAddingNovelPart(parentPost: NovelPost) {
        clearPostInputs()
        novelTitleInput = parentPost.title
        novelCoverInput = parentPost.coverImageUrl
        
        // Count parts dynamically
        val brotherParts = novelPosts.value.filter { it.novelId == parentPost.novelId || it.id == parentPost.novelId || it.id == parentPost.id }
        val nextPartNum = brotherParts.size + 1
        novelPartInput = "Part $nextPartNum"
        novelContentInput = ""
        
        novelIdForNewPart = if (parentPost.novelId.isNotEmpty()) parentPost.novelId else parentPost.id
        editingPostId = null
        activeBottomTab = "NOVEL"
        showPostDialog = true
    }

    fun loadUserProfilePosts(userId: String) {
        if (com.example.repository.FirebaseState.isInitialized) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // 1. Get user news filtered by "userId"
            db.collection("news")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        val posts = snapshot.documents.mapNotNull { doc ->
                            val raw = doc.toObject(NewsPost::class.java)
                            raw?.copy(
                                id = doc.id,
                                postId = doc.id,
                                content = raw.content.ifEmpty { raw.description },
                                description = raw.description.ifEmpty { raw.content }
                            )
                        }.sortedByDescending { it.timestamp }
                        currentUserNews.value = posts
                    } else {
                        // Fallback filter using "authorId"
                        db.collection("news")
                            .whereEqualTo("authorId", userId)
                            .addSnapshotListener { snap2, err2 ->
                                if (snap2 != null) {
                                    val posts = snap2.documents.mapNotNull { doc ->
                                        val raw = doc.toObject(NewsPost::class.java)
                                        raw?.copy(id = doc.id, postId = doc.id)
                                    }.sortedByDescending { it.timestamp }
                                    currentUserNews.value = posts
                                }
                            }
                    }
                }

            // 2. Get user novels filtered by "userId"
            db.collection("novels")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        val posts = snapshot.documents.mapNotNull { doc ->
                            val raw = doc.toObject(NovelPost::class.java)
                            raw?.copy(id = doc.id, novelId = doc.id)
                        }.sortedByDescending { it.timestamp }
                        currentUserNovels.value = posts
                    } else {
                        db.collection("novels")
                            .whereEqualTo("authorId", userId)
                            .addSnapshotListener { snap2, err2 ->
                                if (snap2 != null) {
                                    val posts = snap2.documents.mapNotNull { doc ->
                                        val raw = doc.toObject(NovelPost::class.java)
                                        raw?.copy(id = doc.id, novelId = doc.id)
                                    }.sortedByDescending { it.timestamp }
                                    currentUserNovels.value = posts
                                }
                            }
                    }
                }

            // 3. Get user thoughts filtered by "userId"
            db.collection("thoughts")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        val posts = snapshot.documents.mapNotNull { doc ->
                            val raw = doc.toObject(ThoughtPost::class.java)
                            raw?.copy(id = doc.id, thoughtId = doc.id)
                        }.sortedByDescending { it.timestamp }
                        currentUserThoughts.value = posts
                    } else {
                        db.collection("thoughts")
                            .whereEqualTo("authorId", userId)
                            .addSnapshotListener { snap2, err2 ->
                                if (snap2 != null) {
                                    val posts = snap2.documents.mapNotNull { doc ->
                                        val raw = doc.toObject(ThoughtPost::class.java)
                                        raw?.copy(id = doc.id, thoughtId = doc.id)
                                    }.sortedByDescending { it.timestamp }
                                    currentUserThoughts.value = posts
                                }
                            }
                    }
                }
        } else {
            // Local fallback filter query
            currentUserNews.value = newsPosts.value.filter { it.authorId == userId }
            currentUserNovels.value = novelPosts.value.filter { it.authorId == userId }
            currentUserThoughts.value = thoughtPosts.value.filter { it.authorId == userId }
        }
    }

    fun startEditingNews(post: NewsPost) {
        newsStateInput = post.state
        newsDistrictInput = post.district
        newsTitleInput = post.title
        newsImgInput = post.imageUrl
        newsDescInput = post.description
        editingPostId = post.id
        activeBottomTab = "NEWS"
        showPostDialog = true
    }

    fun startEditingNovel(post: NovelPost) {
        novelTitleInput = post.title
        novelCoverInput = post.coverImageUrl
        novelPartInput = post.part.ifEmpty { "Part 1" }
        novelContentInput = post.content
        editingPostId = post.id
        activeBottomTab = "NOVEL"
        showPostDialog = true
    }

    fun startEditingThought(post: ThoughtPost) {
        thoughtTitleInput = post.title
        thoughtImgInput = post.imageUrl
        thoughtContentInput = post.content
        editingPostId = post.id
        activeBottomTab = "THOUGHTS"
        showPostDialog = true
    }

    // --- SOCIAL USER JOURNEYS ---
    fun toggleLike(postId: String, postType: PostType) {
        repository.toggleLike(postId, postType)
    }

    fun isLiked(postId: String): Boolean {
        return repository.isLiked(postId)
    }

    fun isSaved(postId: String): Boolean {
        return repository.isSaved(postId)
    }

    fun toggleSave(postId: String) {
        repository.toggleSavePost(postId)
    }

    fun toggleFollow(userId: String) {
        repository.toggleFollow(userId)
    }

    fun reportPost(postId: String, postType: PostType, reason: String) {
        repository.reportPost(postId, postType, reason)
    }

    fun reportUser(reportedUserId: String, reason: String) {
        repository.reportUser(reportedUserId, reason)
    }

    fun blockUser(targetUserId: String) {
        repository.blockUser(targetUserId)
    }

    fun addComment(text: String) {
        val postId = activeCommentsPostId ?: return
        if (text.trim().isEmpty()) return
        repository.addComment(postId, activeCommentsPostType, text.trim())
    }

    fun getCommentsForPost(postId: String): List<Comment> {
        return repository.comments.value.filter { it.postId == postId }
    }

    fun dismissNotification(id: String) {
        repository.dismissNotification(id)
    }

    fun markNotificationsRead() {
        repository.markNotificationsRead()
    }

    // --- ADMIN SYSTEM CONTROLLERS ---
    fun approvePost(postId: String, postType: PostType) {
        repository.setPostStatus(postId, postType, PostStatus.PUBLISHED)
    }

    fun rejectPost(postId: String, postType: PostType) {
        repository.setPostStatus(postId, postType, PostStatus.REJECTED)
    }

    fun hidePost(postId: String, postType: PostType) {
        repository.setPostStatus(postId, postType, PostStatus.HIDDEN)
    }

    fun deletePost(postId: String, postType: PostType) {
        repository.deletePost(postId, postType)
    }

    fun adminDeletePost(postId: String, postType: PostType) {
        repository.deletePost(postId, postType)
    }

    fun saveAdManagementSettings(
        useTest: Boolean,
        showNative: Boolean,
        adsEnabled: Boolean,
        nativeAdId: String,
        bannerAdId: String
    ) {
        val current = adSettings.value
        repository.saveAdSettingsLocal(
            current.copy(
                useTestAds = useTest,
                showNativeAds = showNative,
                adsEnabled = adsEnabled,
                nativeAdId = nativeAdId,
                bannerAdId = bannerAdId
            )
        )
    }
}
