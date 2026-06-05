package com.example.model

import java.util.UUID

enum class PostStatus {
    AWAITING_MODERATION,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    HIDDEN
}

enum class PostType {
    NEWS,
    NOVEL,
    THOUGHT
}

data class User(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val profilePhotoUrl: String = "",
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList(),
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val totalIdLikes: Int = 0,
    val savedPosts: List<String> = emptyList(),
    val isAdmin: Boolean = false,
    val isBlocked: Boolean = false,
    val bio: String = "",
    val password: String = "",
    val fcmToken: String = ""
)

data class NewsPost(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val state: String = "",
    val district: String = "",
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: PostStatus = PostStatus.AWAITING_MODERATION,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val moderationNotes: String = ""
)

data class NovelPost(
    val id: String = UUID.randomUUID().toString(),
    val novelId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val author: String = "",
    val title: String = "",
    val content: String = "",
    val coverImageUrl: String = "",
    val part: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: PostStatus = PostStatus.AWAITING_MODERATION,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val moderationNotes: String = ""
)

data class ThoughtPost(
    val id: String = UUID.randomUUID().toString(),
    val thoughtId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val title: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: PostStatus = PostStatus.AWAITING_MODERATION,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val moderationNotes: String = ""
)

data class Comment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val postType: PostType = PostType.NEWS,
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class Like(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val postId: String = "",
    val postType: PostType = PostType.NEWS
)

data class Notification(
    val id: String = UUID.randomUUID().toString(),
    val notificationId: String = "",
    val recipientId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val senderPic: String = "",
    val type: String = "", // "LIKE", "COMMENT", "FOLLOW", "POST_APPROVED", "POST_REJECTED"
    val message: String = "",
    val postId: String = "",
    val postType: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class Report(
    val id: String = UUID.randomUUID().toString(),
    val reporterId: String = "",
    val reportedUserId: String = "",
    val postId: String = "",
    val postType: PostType = PostType.NEWS,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class AdSettings(
    val useTestAds: Boolean = true,
    val showNativeAds: Boolean = true,
    val newsAdFrequency: Int = 3, // After every 3 posts
    val novelAdFrequency: Int = 3,
    val thoughtsAdFrequency: Int = 3,
    val adsEnabled: Boolean = true,
    val nativeAdId: String = "ca-app-pub-3940256099942544/2247696110",
    val bannerAdId: String = "ca-app-pub-3940256099942544/9214589741"
)

// Explore items data models
data class GovtJob(val title: String, val department: String, val lastDate: String, val url: String)
data class ExamResult(val examName: String, val announcedDate: String, val status: String, val url: String)
data class DailyWeather(val temp: String, val condition: String, val humidity: String, val city: String)
data class GovtScheme(val title: String, val benefits: String, val eligible: String)
data class GoldRate(val rate22kPerGram: String, val rate24kPerGram: String, val trend: String) // "Up", "Down"
data class PetrolDieselRate(val city: String, val petrolRate: String, val dieselRate: String)
