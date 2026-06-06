package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdSettings
import com.example.viewmodel.AppViewModel

@Composable
fun AdMobNativeWidget(adSettings: AdSettings, viewModel: AppViewModel? = null) {
    if (!adSettings.adsEnabled) return

    val context = LocalContext.current
    val resolvedNativeId = if (adSettings.useTestAds || adSettings.nativeAdId.trim().isEmpty()) {
        "ca-app-pub-3940256099942544/2247696110"
    } else {
        adSettings.nativeAdId.trim()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Ad indicator bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF59E0B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (adSettings.useTestAds) "Sponsored Ad (Test Mode)" else "Premium Sponsor Partner",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: $resolvedNativeId",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Ad Info",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ad banner layout representation - highly realistic Native Ad block
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ad icon representation
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Ad Icon",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ఇప్పుడే డౌన్‌లోడ్ చేసుకోండి!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "తెలుగు వార్తలు మరియు నవలలు ఒకే చోట ఆస్వాదించండి. ఉత్తమ రీడింగ్ ఎక్స్‌పీరియన్స్ కోసం క్లిక్ చేయండి.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ad action button
            Button(
                onClick = {
                    if (viewModel != null) {
                        viewModel.earnCoins(20)
                        Toast.makeText(
                            context,
                            "Sponsor Reward: +20 Coins added to wallet! / +20 నాణేలు లభించాయి!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Sponsor Ad Visited Successfully!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), // Emerald Green Ad Call to Action
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Text(
                    text = if (adSettings.useTestAds) "VISIT SPONSOR (TEST CLICK)" else "INSTALL / DOWNLOAD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun AdMobBannerWidget(adSettings: AdSettings, viewModel: AppViewModel? = null) {
    if (!adSettings.adsEnabled) return

    val context = LocalContext.current
    val resolvedBannerId = if (adSettings.useTestAds || adSettings.bannerAdId.trim().isEmpty()) {
        "ca-app-pub-3940256099942544/9214589741"
    } else {
        adSettings.bannerAdId.trim()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF59E0B))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AD BANNER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "స్పాన్సర్డ్ యాడ్: అత్యుత్తమ ఆఫర్లు!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: $resolvedBannerId",
                        fontSize = 8.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (viewModel != null) {
                        viewModel.earnCoins(10)
                        Toast.makeText(context, "Sponsor Reward: +10 Coins Added! / +10 నాణేలు లభించాయి!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Banner Ad Visited!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("OPEN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
