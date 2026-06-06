@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyItemScope
import coil.compose.AsyncImage
import com.example.model.NewsPost
import com.example.model.NovelPost
import com.example.model.PostStatus
import com.example.model.PostType
import com.example.model.ThoughtPost
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.components.AdMobNativeWidget
import com.example.ui.components.AdMobBannerWidget
import com.example.viewmodel.AppViewModel
import com.example.ui.theme.ThemeManager

@Composable
fun LazyItemScope.AnimatedEntrance(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) {
            kotlinx.coroutines.delay(delayMillis.toLong())
        }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "entrance_alpha"
    )

    val translationY by animateDpAsState(
        targetValue = if (visible) 0.dp else 40.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "entrance_y"
    )

    Box(
        modifier = modifier
            .animateItem()
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translationY.toPx()
            }
    ) {
        content()
    }
}

@Composable
fun NewsFeedScreen(viewModel: AppViewModel) {
    val posts by viewModel.newsPosts.collectAsState()
    val adSettings by viewModel.adSettings.collectAsState()
    val context = LocalContext.current

    // Geographic filtering inputs
    var stateFilterInput by remember { mutableStateOf("") }
    var districtFilterInput by remember { mutableStateOf("") }

    val filteredPosts = posts.filter { post ->
        post.status == PostStatus.PUBLISHED &&
        (stateFilterInput.isEmpty() || post.state.contains(stateFilterInput, true)) &&
        (districtFilterInput.isEmpty() || post.district.contains(districtFilterInput, true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // District filter header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = stateFilterInput,
                onValueChange = { stateFilterInput = it },
                label = { Text("Filter State (రాష్ట్రం టెక్స్ట్)", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFF59E0B),
                    unfocusedBorderColor = Color(0xFF475569)
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                trailingIcon = {
                    if (stateFilterInput.isNotEmpty()) {
                        IconButton(onClick = { stateFilterInput = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            OutlinedTextField(
                value = districtFilterInput,
                onValueChange = { districtFilterInput = it },
                label = { Text("Filter District (జిల్లా)", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFF59E0B),
                    unfocusedBorderColor = Color(0xFF475569)
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                trailingIcon = {
                    if (districtFilterInput.isNotEmpty()) {
                        IconButton(onClick = { districtFilterInput = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )
        }

        if (filteredPosts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Announcement, contentDescription = "Empty", tint = Color(0xFF475569), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ఈ ప్రాంతంలో ఎటువంటి వార్తలు లేవు.", color = Color(0xFF94A3B8), fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(
                    items = filteredPosts,
                    key = { _, post -> post.id }
                ) { index, post ->
                    val delay = (index % 5) * 85
                    AnimatedEntrance(delayMillis = delay) {
                        NewsItemCard(post, viewModel) {
                            com.example.services.NotificationHelper.sharePostWithImageAndLink(
                                context = context,
                                title = post.title,
                                text = post.description.ifEmpty { post.content },
                                imageUrl = post.imageUrl
                            )
                        }
                    }

                    // Native Ads system
                    if (adSettings.showNativeAds && (index + 1) % adSettings.newsAdFrequency == 0) {
                        AdMobNativeWidget(adSettings, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun NewsItemCard(post: NewsPost, viewModel: AppViewModel, onShare: () -> Unit) {
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable { viewModel.activeReaderNews = post }
            .testTag("news_card_${post.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Creator / Follow row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = post.authorPhoto.ifEmpty { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde" },
                        contentDescription = "Author image",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(post.authorName.ifEmpty { "రిపోర్టర్" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("న్యూస్ రిలేటెడ్", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                }
                
                val currentUserId = viewModel.currentUser.collectAsState().value?.id
                if (currentUserId != null && post.authorId.isNotEmpty() && currentUserId != post.authorId) {
                    val isFollowing = viewModel.currentUser.collectAsState().value?.following?.contains(post.authorId) == true
                    Button(
                        onClick = { viewModel.toggleFollow(post.authorId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) Color(0xFF475569) else Color(0xFFF59E0B)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).testTag("follow_btn_${post.id}")
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            color = if (isFollowing) Color.White else Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Region tags
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF5252).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${post.state} - ${post.district}",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Content options button with dropdown menu
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color(0xFF94A3B8))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        val isAuthor = viewModel.currentUser.value?.id == post.authorId
                        if (isAuthor) {
                            DropdownMenuItem(
                                text = { Text(if (ThemeManager.currentLanguage == "TE") "మార్చు (Edit)" else "Edit Post", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFF59E0B)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.startEditingNews(post)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (ThemeManager.currentLanguage == "TE") "తొలగించు (Delete)" else "Delete Post", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    viewModel.deletePost(post.id, PostType.NEWS)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (ThemeManager.currentLanguage == "TE") "నివేదించు (Report)" else "Report Post", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = "Report", tint = Color.Red) },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            }
                        )
                    }
                }
            }

            // Image attachment
            AsyncImage(
                model = post.imageUrl,
                contentDescription = "News Cover Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )

            // Primary typography
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = post.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Serif
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = post.description,
                    fontSize = 14.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Divider(color = Color(0xFF334155))

                // Actions tray
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isLiked = viewModel.isLiked(post.id)
                    val isSaved = viewModel.isSaved(post.id)

                    // Likes button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.toggleLike(post.id, PostType.NEWS) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${post.likesCount}", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Comments button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.activeCommentsPostId = post.id
                                viewModel.activeCommentsPostType = PostType.NEWS
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Comment",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${post.commentsCount}", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Share button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShare() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Save button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.toggleSave(post.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (isSaved) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Post / రిపోర్ట్") },
            text = {
                Column {
                    Text("Does this post contain abusive language, spam or fake news info? Block creator or report content below:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text("Reason for reporting") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportPost(post.id, PostType.NEWS, reportReason)
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("REPORT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// --- NOVEL SECTION ---
@Composable
fun NovelFeedScreen(viewModel: AppViewModel) {
    val posts by viewModel.novelPosts.collectAsState()
    val adSettings by viewModel.adSettings.collectAsState()
    val context = LocalContext.current

    val publishedNovels = posts.filter { it.status == PostStatus.PUBLISHED }

    if (publishedNovels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("నవలలు ఏవీ అందుబాటులో లేవు.", color = Color(0xFF94A3B8), fontSize = 15.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = publishedNovels,
                key = { _, post -> post.id }
            ) { index, post ->
                val delay = (index % 5) * 85
                AnimatedEntrance(delayMillis = delay) {
                    NovelItemCard(post, viewModel, onReadClick = { viewModel.activeReaderNovel = post }) {
                        com.example.services.NotificationHelper.sharePostWithImageAndLink(
                            context = context,
                            title = "నవల: ${post.title}",
                            text = "రచయిత: ${post.authorName}\n\n${post.content}",
                            imageUrl = post.coverImageUrl
                        )
                    }
                }

                // Native Ads system
                if (adSettings.showNativeAds && (index + 1) % adSettings.novelAdFrequency == 0) {
                    AdMobNativeWidget(adSettings, viewModel)
                }
            }
        }
    }
}

@Composable
fun NovelItemCard(post: NovelPost, viewModel: AppViewModel, onReadClick: () -> Unit, onShare: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable { onReadClick() }
            .testTag("novel_card_${post.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Novel cover
            AsyncImage(
                model = post.coverImageUrl,
                contentDescription = "Novel Cover Image",
                modifier = Modifier
                    .size(90.dp, 130.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = post.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (post.part.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFF59E0B))
                            ) {
                                Text(
                                    text = post.part,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Add Part (+) Architecture (Only visible if logged-in user is the original author)
                        val currentUserId = viewModel.currentUser.value?.id
                        val isAuthor = currentUserId != null && post.authorId.isNotEmpty() && currentUserId == post.authorId
                        if (isAuthor) {
                            IconButton(
                                onClick = { viewModel.startAddingNovelPart(post) },
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFFF59E0B), shape = RoundedCornerShape(4.dp))
                                    .testTag("add_part_btn_${post.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Segment",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color(0xFF94A3B8))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            val isAuthor = viewModel.currentUser.value?.id == post.authorId
                            if (isAuthor) {
                                DropdownMenuItem(
                                    text = { Text(if (ThemeManager.currentLanguage == "TE") "మార్చు (Edit)" else "Edit Post", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFF59E0B)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.startEditingNovel(post)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (ThemeManager.currentLanguage == "TE") "తొలగించు (Delete)" else "Delete Post", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.deletePost(post.id, PostType.NOVEL)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (ThemeManager.currentLanguage == "TE") "నివేదించు (Report)" else "Report Post", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = "Report", tint = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    showReportDialog = true
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "రచయిత: ${post.authorName}",
                        fontSize = 13.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Medium
                    )
                    
                    val currentUserId = viewModel.currentUser.collectAsState().value?.id
                    if (currentUserId != null && post.authorId.isNotEmpty() && currentUserId != post.authorId) {
                        val isFollowing = viewModel.currentUser.collectAsState().value?.following?.contains(post.authorId) == true
                        Button(
                            onClick = { viewModel.toggleFollow(post.authorId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) Color(0xFF475569) else Color(0xFFF59E0B)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp).testTag("follow_btn_novel_${post.id}")
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                color = if (isFollowing) Color.White else Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = post.content,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Eye reading trigger
                    Button(
                        onClick = onReadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("READ / చదవండి", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isLiked = viewModel.isLiked(post.id)
                        val isSaved = viewModel.isSaved(post.id)

                        // Likes button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleLike(post.id, PostType.NOVEL) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) Color.Red else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${post.likesCount}", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Comments button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.activeCommentsPostId = post.id
                                    viewModel.activeCommentsPostType = PostType.NOVEL
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Comment,
                                contentDescription = "Comment",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${post.commentsCount}", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onShare() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Save button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleSave(post.id) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Save",
                                tint = if (isSaved) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Post / రిపోర్ట్") },
            text = {
                Column {
                    Text("Does this post contain abusive language, spam or fake news info? Block creator or report content below:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text("Reason for reporting") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportPost(post.id, PostType.NOVEL, reportReason)
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("REPORT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// --- THOUGHTS SECTION ---
@Composable
fun ThoughtsFeedScreen(viewModel: AppViewModel) {
    val posts by viewModel.thoughtPosts.collectAsState()
    val adSettings by viewModel.adSettings.collectAsState()
    val context = LocalContext.current

    val publishedThoughts = posts.filter { it.status == PostStatus.PUBLISHED }

    if (publishedThoughts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("అభిప్రాయాలు ఏవీ అందుబాటులో లేవు.", color = Color(0xFF94A3B8), fontSize = 15.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = publishedThoughts,
                key = { _, post -> post.id }
            ) { index, post ->
                val delay = (index % 5) * 85
                AnimatedEntrance(delayMillis = delay) {
                    ThoughtItemCard(post, viewModel) {
                        com.example.services.NotificationHelper.sharePostWithImageAndLink(
                            context = context,
                            title = post.title,
                            text = post.content,
                            imageUrl = post.imageUrl
                        )
                    }
                }

                // Native Ads system
                if (adSettings.showNativeAds && (index + 1) % adSettings.thoughtsAdFrequency == 0) {
                    AdMobNativeWidget(adSettings, viewModel)
                }
            }
        }
    }
}

@Composable
fun ThoughtItemCard(post: ThoughtPost, viewModel: AppViewModel, onShare: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable { viewModel.activeReaderThought = post }
            .testTag("thought_card_${post.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = post.authorPhoto,
                        contentDescription = "Author image",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(post.authorName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            
                            val currentUserId = viewModel.currentUser.collectAsState().value?.id
                            if (currentUserId != null && post.authorId.isNotEmpty() && currentUserId != post.authorId) {
                                val isFollowing = viewModel.currentUser.collectAsState().value?.following?.contains(post.authorId) == true
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isFollowing) "• Following" else "• Follow",
                                    color = if (isFollowing) Color(0xFF94A3B8) else Color(0xFFF59E0B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.toggleFollow(post.authorId) }
                                        .testTag("follow_lbl_thought_${post.id}")
                                )
                            }
                        }
                        Text("Thought Feed", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color(0xFF94A3B8))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        val isAuthor = viewModel.currentUser.value?.id == post.authorId
                        if (isAuthor) {
                            DropdownMenuItem(
                                text = { Text(if (ThemeManager.currentLanguage == "TE") "మార్చు (Edit)" else "Edit Post", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFF59E0B)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.startEditingThought(post)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (ThemeManager.currentLanguage == "TE") "తొలగించు (Delete)" else "Delete Post", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    viewModel.deletePost(post.id, PostType.THOUGHT)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (ThemeManager.currentLanguage == "TE") "నివేదించు (Report)" else "Report Post", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = "Report", tint = Color.Red) },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            Text(
                text = "✨ ${post.title}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B),
                fontFamily = FontFamily.Serif
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = post.content,
                fontSize = 14.sp,
                color = Color.White,
                lineHeight = 20.sp
            )

            if (post.imageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Thought image attachment",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFF334155))

            // Act icons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isLiked = viewModel.isLiked(post.id)
                val isSaved = viewModel.isSaved(post.id)

                // Likes button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.toggleLike(post.id, PostType.THOUGHT) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color.Red else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${post.likesCount}", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Comments button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.activeCommentsPostId = post.id
                            viewModel.activeCommentsPostType = PostType.THOUGHT
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Comment,
                        contentDescription = "Comment",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${post.commentsCount}", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Share button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShare() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Save button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.toggleSave(post.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (isSaved) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Post / రిపోర్ట్") },
            text = {
                Column {
                    Text("Does this post contain abusive language, spam or fake news info? Block creator or report content below:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text("Reason for reporting") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportPost(post.id, PostType.THOUGHT, reportReason)
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("REPORT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// --- NOVEL OVERLAY READER MODE ---
@Composable
fun NovelReaderOverlay(post: NovelPost, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var themeComfortMode by remember { mutableStateOf(0) } // 0: Parchment (Eye Safe), 1: Light, 2: Dark
    var fontSizeConfig by remember { mutableStateOf(16.sp) }

    val bgColor = when (themeComfortMode) {
        0 -> Color(0xFFFDF6E3) // Parchment
        1 -> Color.White
        else -> Color(0xFF0F172A) // Dark
    }

    val textColor = when (themeComfortMode) {
        0 -> Color(0xFF2E2E2E)
        1 -> Color.Black
        else -> Color.White
    }

    Scaffold(
        topBar = {
            OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(post.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Speed adjustments buttons
                    IconButton(onClick = { if (fontSizeConfig.value > 12) fontSizeConfig = (fontSizeConfig.value - 2).sp }) {
                        Text("A-", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    IconButton(onClick = { if (fontSizeConfig.value < 26) fontSizeConfig = (fontSizeConfig.value + 2).sp }) {
                        Text("A+", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    
                    // Comfortable colors toggler
                    IconButton(onClick = { themeComfortMode = (themeComfortMode + 1) % 3 }) {
                        Icon(
                            imageVector = when (themeComfortMode) {
                                0 -> Icons.Default.AutoStories
                                1 -> Icons.Default.WbSunny
                                else -> Icons.Default.DarkMode
                            },
                            contentDescription = "Comfort theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AdMobBannerWidget(
                adSettings = viewModel.adSettings.collectAsState().value,
                viewModel = viewModel
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(20.dp)
            ) {
            item {
                Text(
                    text = post.title,
                    fontSize = (fontSizeConfig.value + 4).sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "రచన: ${post.authorName}${if (post.part.isNotEmpty()) " • ${post.part}" else ""} • ప్రచురించబడింది",
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    
                    val currentUserId = viewModel.currentUser.collectAsState().value?.id
                    if (currentUserId != null && post.authorId.isNotEmpty() && currentUserId != post.authorId) {
                        val isFollowing = viewModel.currentUser.collectAsState().value?.following?.contains(post.authorId) == true
                        Button(
                            onClick = { viewModel.toggleFollow(post.authorId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) Color(0xFF334155) else Color(0xFFF59E0B)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("follow_btn_reader_novel_${post.id}")
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                color = if (isFollowing) Color.White else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // RELATED NOVEL PARTS (CHAPTERS NAVIGATOR)
                val allNovels by viewModel.novelPosts.collectAsState()
                val relatedParts = remember(post, allNovels) {
                    allNovels.filter {
                        (it.novelId == post.novelId && post.novelId.isNotEmpty()) ||
                        it.title.trim().equals(post.title.trim(), ignoreCase = true)
                    }.filter { it.status == PostStatus.PUBLISHED || it.authorId == post.authorId }
                     .sortedBy {
                         val num = try {
                             it.part.replace("[^0-9]".toRegex(), "").toInt()
                         } catch(e: Exception) {
                             999
                         }
                         num
                     }
                }

                if (relatedParts.size > 1) {
                    Text(
                        text = "అన్ని భాగాలు (All Parts/Chapters):",
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        relatedParts.forEach { partItem ->
                            val isCurrent = partItem.id == post.id
                            AssistChip(
                                onClick = {
                                    viewModel.activeReaderNovel = partItem
                                },
                                label = {
                                    Text(
                                        text = partItem.part.ifEmpty { "భాగం" },
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isCurrent) Color(0xFFF59E0B) else Color.Transparent,
                                    labelColor = if (isCurrent) Color.Black else textColor
                                ),
                                border = if (isCurrent) null else androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
                            )
                        }
                    }
                }

                Divider(color = textColor.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 20.dp))

                Text(
                    text = post.content,
                    fontSize = fontSizeConfig,
                    color = textColor,
                    lineHeight = (fontSizeConfig.value * 1.6f).sp,
                    fontFamily = FontFamily.Serif
                )

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = textColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Like and Comment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.activeCommentsPostId = post.id
                            viewModel.activeCommentsPostType = PostType.NOVEL
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Icon(Icons.Default.Comment, contentDescription = "Comments", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("వ్యాఖ్యానించండి (${post.commentsCount})", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    val isLiked = viewModel.isLiked(post.id)
                    IconButton(onClick = { viewModel.toggleLike(post.id, PostType.NOVEL) }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else textColor.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
}

// --- NEWS OVERLAY READER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsReaderOverlay(post: NewsPost, viewModel: AppViewModel, onDismiss: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("వార్తలు / Full News Read", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AdMobBannerWidget(
                adSettings = viewModel.adSettings.collectAsState().value,
                viewModel = viewModel
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                // Region badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${post.state} - ${post.district}",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = post.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = "Author", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("లేఖరి: ${post.authorName.ifEmpty { "రిలేటెడ్ రిపోర్టర్" }}", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                    
                    val currentUserId = viewModel.currentUser.collectAsState().value?.id
                    if (currentUserId != null && post.authorId.isNotEmpty() && currentUserId != post.authorId) {
                        val isFollowing = viewModel.currentUser.collectAsState().value?.following?.contains(post.authorId) == true
                        Button(
                            onClick = { viewModel.toggleFollow(post.authorId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) Color(0xFF334155) else Color(0xFFF59E0B)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("follow_btn_reader_news_${post.id}")
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                color = if (isFollowing) Color.White else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (post.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = "Cover Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = post.description,
                    fontSize = 15.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 24.sp,
                    fontFamily = FontFamily.Default
                )

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                // Comment trigger action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.activeCommentsPostId = post.id
                            viewModel.activeCommentsPostType = PostType.NEWS
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Icon(Icons.Default.Comment, contentDescription = "Comments", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("వ్యాఖ్యానించండి (${post.commentsCount})", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    val isLiked = viewModel.isLiked(post.id)
                    IconButton(onClick = { viewModel.toggleLike(post.id, PostType.NEWS) }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
}

// --- THOUGHT OVERLAY READER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtReaderOverlay(post: ThoughtPost, viewModel: AppViewModel, onDismiss: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("భావాలు / Thoughts reader", fontSize = 15.sp) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AdMobBannerWidget(
                adSettings = viewModel.adSettings.collectAsState().value,
                viewModel = viewModel
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Profile Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = post.authorPhoto,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(post.authorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("సూక్తులు / దినచర్య", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        val currentUserId = viewModel.currentUser.collectAsState().value?.id
                        if (currentUserId != null && post.authorId.isNotEmpty() && currentUserId != post.authorId) {
                            val isFollowing = viewModel.currentUser.collectAsState().value?.following?.contains(post.authorId) == true
                            Button(
                                onClick = { viewModel.toggleFollow(post.authorId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) Color(0xFF334155) else Color(0xFFF59E0B)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("follow_btn_reader_thought_${post.id}")
                            ) {
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    color = if (isFollowing) Color.White else Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "✨ ${post.title}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B),
                        fontFamily = FontFamily.Serif
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "“${post.content}”",
                        fontSize = 16.sp,
                        color = Color.White,
                        lineHeight = 26.sp,
                        fontFamily = FontFamily.Serif,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (post.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = post.imageUrl,
                            contentDescription = "Content Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isLiked = viewModel.isLiked(post.id)
                        Button(
                            onClick = { viewModel.toggleLike(post.id, PostType.THOUGHT) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLiked) Color.Red else Color(0xFF334155))
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Like", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${post.likesCount} ప్రశంసలు")
                        }

                        IconButton(onClick = {
                            viewModel.activeCommentsPostId = post.id
                            viewModel.activeCommentsPostType = PostType.THOUGHT
                        }) {
                            Icon(Icons.Default.Comment, contentDescription = "Comments", tint = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}
}

// --- COMMENTS SYSTEM DRAWER/SHEET ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(viewModel: AppViewModel, onDismiss: () -> Unit) {
    val postId = viewModel.activeCommentsPostId ?: return
    val comments = viewModel.getCommentsForPost(postId)
    var inputCommentText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        modifier = Modifier.testTag("comments_bottom_sheet")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp)
            ) {
                Text(
                    text = "కామెంట్లు (${comments.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )

                Divider(color = Color(0xFF334155))

                // Comments list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("మొదటి కామెంట్ రాయండి!", color = Color(0xFF94A3B8))
                            }
                        }
                    } else {
                        itemsIndexed(comments) { _, comment ->
                            val currentUserId = viewModel.currentUser.collectAsState().value?.id
                            val isLiked = currentUserId != null && comment.likedByUsers.contains(currentUserId)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f)) {
                                    AsyncImage(
                                        model = comment.authorPhotoUrl,
                                        contentDescription = "Author Avatar",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = comment.authorName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF59E0B)
                                        )
                                        Text(
                                            text = comment.text,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                
                                // Comment Like Button
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { viewModel.toggleLikeComment(comment.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Like Comment",
                                            tint = if (isLiked) Color.Red else Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (comment.likesCount > 0) {
                                        Text(
                                            text = "${comment.likesCount}",
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            Divider(color = Color(0xFF1E293B))
                        }
                    }
                }
            }

            // Input field pinned to bottom of the Box with Autocomplete mentions suggestion bar
            val allUsers by viewModel.allUsers.collectAsState()
            val lastWord = inputCommentText.substringAfterLast(' ', "")
            val isMentionPath = lastWord.startsWith("@") && lastWord.length > 1
            val mentionQuery = if (isMentionPath) lastWord.drop(1).lowercase() else ""
            val suggestedUsers = remember(mentionQuery, allUsers) {
                if (mentionQuery.isEmpty()) emptyList()
                else {
                    allUsers.filter {
                        it.name.lowercase().contains(mentionQuery) ||
                        it.id.lowercase().contains(mentionQuery)
                    }.take(5)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                if (isMentionPath && suggestedUsers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("తగిలించు (Mention):", fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                        suggestedUsers.forEach { suggestUser ->
                            AssistChip(
                                onClick = {
                                    val prefix = inputCommentText.substringBeforeLast("@")
                                    inputCommentText = "${prefix}@${suggestUser.name} "
                                },
                                label = {
                                    Text("@${suggestUser.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFF334155),
                                    labelColor = Color.White
                                ),
                                border = null
                            )
                        }
                    }
                    Divider(color = Color(0xFF334155))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputCommentText,
                        onValueChange = { inputCommentText = it },
                        placeholder = { Text("ఇక్కడ కామెంట్ రాయండి...", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input_field"),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputCommentText.trim().isNotEmpty()) {
                                viewModel.addComment(inputCommentText)
                                inputCommentText = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFF59E0B)),
                        modifier = Modifier.testTag("comment_send_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                    }
                }
            }
        }
    }
}
