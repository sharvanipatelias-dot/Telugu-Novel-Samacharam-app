package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.viewmodel.AppViewModel
import com.example.ui.components.AdMobNativeWidget
import kotlinx.coroutines.withContext

@Composable
fun ExploreScreen(viewModel: AppViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showWeatherForecastDialog by remember { mutableStateOf(false) }
    var showGoldRateCitiesDialog by remember { mutableStateOf(false) }
    
    var selectedWeatherCity by remember { mutableStateOf("Hyderabad") }
    var weatherForecastList by remember { mutableStateOf<List<ForecastDay>>(emptyList()) }
    var isLoadingWeather by remember { mutableStateOf(false) }
    
    val goldRatesByCity = remember {
        listOf(
            CityGoldRate("Hyderabad", "रू. 6,850", "रू. 7,470"),
            CityGoldRate("Vijayawada", "रू. 6,860", "रू. 7,485"),
            CityGoldRate("Visakhapatnam", "रू. 6,845", "रू. 7,465"),
            CityGoldRate("Tirupati", "रू. 6,870", "रू. 7,490"),
            CityGoldRate("Warangal", "रू. 6,855", "रू. 7,475")
        )
    }

    LaunchedEffect(selectedWeatherCity, showWeatherForecastDialog) {
        if (showWeatherForecastDialog) {
            isLoadingWeather = true
            val (lat, lon) = when (selectedWeatherCity) {
                "Hyderabad" -> "17.3850" to "78.4867"
                "Vijayawada" -> "16.5062" to "80.6480"
                "Visakhapatnam" -> "17.6868" to "83.2185"
                "Tirupati" -> "13.6284" to "79.4192"
                "Amaravati" -> "16.5744" to "80.3736"
                else -> "17.3850" to "78.4867"
            }
            
            try {
                val daysList = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=Asia%2FKolkata"
                    val connection = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    val times = extractJsonArray(response, "time")
                    val tempMaxs = extractJsonArray(response, "temperature_2m_max")
                    val tempMins = extractJsonArray(response, "temperature_2m_min")
                    val codes = extractJsonArray(response, "weathercode")
                    
                    val list = mutableListOf<ForecastDay>()
                    for (i in 0 until minOf(times.size, tempMaxs.size, tempMins.size, codes.size, 5)) {
                        val codeVal = codes[i].trim().toIntOrNull() ?: 0
                        list.add(
                            ForecastDay(
                                date = times[i].replace("\"", ""),
                                maxTemp = "${tempMaxs[i]}°C",
                                minTemp = "${tempMins[i]}°C",
                                condition = getWeatherConditionByCode(codeVal)
                            )
                        )
                    }
                    list
                }
                
                if (daysList.isNotEmpty()) {
                    weatherForecastList = daysList
                } else {
                    weatherForecastList = getFallbackForecast(selectedWeatherCity)
                }
            } catch (e: Exception) {
                weatherForecastList = getFallbackForecast(selectedWeatherCity)
            } finally {
                isLoadingWeather = false
            }
        }
    }
    
    // Seed Explore Data
    val jobs = remember {
        listOf(
            GovtJob("APPSC Group 1 Notification 2026", "Andhra Pradesh PSC", "2026-07-15", "https://psc.ap.gov.in"),
            GovtJob("Police Constable Recruitment", "TSPRB Telangana", "2026-06-30", "https://tslprb.co.in"),
            GovtJob("Junior Assistant & Stenographer", "High Court of Telangana", "2026-06-25", "https://tshc.gov.in")
        )
    }

    val examResults = remember {
        listOf(
            ExamResult("Inter 2nd Year Results 2026", "2026-06-03", "ANNOUNCED / విడుదలయ్యాయి", "https://results.cgg.gov.in"),
            ExamResult("AP SSC Board Results 2026", "2026-05-28", "ANNOUNCED", "https://bse.ap.gov.in"),
            ExamResult("TS EAMCET Engineering Results", "2026-06-12", "AWAITING / త్వరలో", "https://eamcet.tsche.ac.in")
        )
    }

    val weather = remember {
        DailyWeather("38°C", "Sunny / ఎండగా ఉంది", "45%", "Amaravati & Hyderabad")
    }

    val schemes = remember {
        listOf(
            GovtScheme("రైతు బంధు / రైతు భరోసా", "రూ. 15,000 సంవత్సరానికి నేరుగా బ్యాంకు ఖాతాలోకి", "మొత్తం భూమి ఉన్న రైతులందరికీ వర్తిస్తుంది"),
            GovtScheme("కల్యాణ లక్ష్మీ / షాదీ ముబారక్", "రూ. 1,00,116 వివాహ సహాయం", "పేద కుటుంబాలకు చెందిన ఆడపిల్లలకు"),
            GovtScheme("ఉచిత విద్యుత్ పథకం (గృహ జ్యోతి)", "200 యూనిట్ల వరకు ఉచిత గృహ విద్యుత్", "తెల్ల రేషన్ కార్డు కలిగి ఉన్న లబ్ధిదారులకు")
        )
    }

    val goldRate = remember {
        GoldRate("రూ. 6,850", "రూ. 7,470", "Up")
    }

    val fuelRates = remember {
        listOf(
            PetrolDieselRate("Hyderabad", "రూ. 109.66", "రూ. 97.82"),
            PetrolDieselRate("Vijayawada", "రూ. 111.20", "రూ. 99.15"),
            PetrolDieselRate("Visakhapatnam", "రూ. 110.15", "రూ. 98.24")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("explore_screen_root")
    ) {
        // Quick insights scroll view
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Weather and Gold Rates quick metrics dual card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weather Widget Card
                Card(
                    modifier = Modifier.weight(1f).clickable { showWeatherForecastDialog = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, contentDescription = "Weather", tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WEATHER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(weather.temp, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(weather.condition, fontSize = 13.sp, color = Color(0xFF94A3B8))
                        Text("City: ${weather.city}", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                // Gold Tracker Card
                Card(
                    modifier = Modifier.weight(1f).clickable { showGoldRateCitiesDialog = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = "Gold Rate", tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GOLD RATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(goldRate.rate24kPerGram, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
                        Text("24K (Per Gram)", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Default.TrendingUp, contentDescription = "Trend", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ధరలు పెరుగుతున్నాయి", fontSize = 11.sp, color = Color(0xFF10B981))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Government Schemes Sections
            ExploreSectionHeader("ప్రభుత్వ పథకాలు (Government Schemes)", Icons.Default.CardMembership)
            schemes.forEach { scheme ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            viewModel.activeWebViewUrl = "https://www.india.gov.in/my-government/schemes"
                            viewModel.activeWebViewTitle = scheme.title
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(scheme.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        Text("ప్రయోజనాలు: ${scheme.benefits}", fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                        Text("అర్హత: ${scheme.eligible}", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Anveshana inside also adsens active added. Auto switch public app ern adds run
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Wifi, contentDescription = "AdSense Active", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Anveshana AdSense Engine Active • Auto Earning Mode", 
                    color = Color(0xFF10B981), 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold
                )
            }
            AdMobNativeWidget(adSettings = AdSettings(showNativeAds = true, useTestAds = false), viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // Government Jobs list
            ExploreSectionHeader("ప్రభుత్వ ఉద్యోగాలు (Govt Jobs)", Icons.Default.Work)
            jobs.forEach { job ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            viewModel.activeWebViewUrl = job.url
                            viewModel.activeWebViewTitle = job.title
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(job.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(job.department, fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Text("Last Date: ${job.lastDate}", fontSize = 11.sp, color = Color(0xFFEF4444))
                        }
                        IconButton(onClick = {
                            viewModel.activeWebViewUrl = job.url
                            viewModel.activeWebViewTitle = job.title
                        }) {
                            Icon(Icons.Default.Launch, contentDescription = "Apply", tint = Color(0xFFF59E0B))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Exam results tracker
            ExploreSectionHeader("పరీక్షా ఫలితాలు (Exam Results)", Icons.Default.School)
            examResults.forEach { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            viewModel.activeWebViewUrl = result.url
                            viewModel.activeWebViewTitle = result.examName
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(result.examName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Date Announced: ${result.announcedDate}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(result.status, color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = {
                            viewModel.activeWebViewUrl = result.url
                            viewModel.activeWebViewTitle = result.examName
                        }) {
                            Icon(Icons.Default.Launch, contentDescription = "Check", tint = Color(0xFFF59E0B))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Petrol / Diesel rate structure
            ExploreSectionHeader("ఇంధన ధరలు (Petrol/Diesel Rates)", Icons.Default.LocalGasStation)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    fuelRates.forEach { rate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(rate.city, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row {
                                Text("Petrol: ", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(rate.petrolRate, color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Diesel: ", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(rate.dieselRate, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Divider(color = Color(0xFF334155))
                    }
                }
            }
        }
    }

    // Interactive Dialogs for live reports
    if (showWeatherForecastDialog) {
        AlertDialog(
            onDismissRequest = { showWeatherForecastDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = "Weather", tint = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("లైవ్ వాతావరణం (Live Weather)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select City / నగరం ఎంచుకోండి:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val cities = listOf("Hyderabad", "Vijayawada", "Visakhapatnam", "Tirupati", "Amaravati")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cities.forEach { city ->
                            Button(
                                onClick = { selectedWeatherCity = city },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedWeatherCity == city) Color(0xFFF59E0B) else Color(0xFF334155),
                                    contentColor = if (selectedWeatherCity == city) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(city.take(5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("5-Day Day-to-Day Forecast ($selectedWeatherCity):", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoadingWeather) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFF59E0B))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            weatherForecastList.forEach { f ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF334155), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(f.date, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(f.condition, color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    Row {
                                        Text(f.maxTemp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(f.minTemp, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherForecastDialog = false }) {
                    Text("OK / మూసివేయి", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showGoldRateCitiesDialog) {
        AlertDialog(
            onDismissRequest = { showGoldRateCitiesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = "Gold Rate", tint = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("బంగారం ధరలు (City Gold Prices)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Current Live Rates across major city hubs:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        goldRatesByCity.forEach { cityRate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cityRate.city, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("24K: ${cityRate.rate24k}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("22K: ${cityRate.rate22k}", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoldRateCitiesDialog = false }) {
                    Text("OK", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

// Dialog helper structures
data class ForecastDay(val date: String, val maxTemp: String, val minTemp: String, val condition: String)
data class CityGoldRate(val city: String, val rate22k: String, val rate24k: String)

fun extractJsonArray(json: String, key: String): List<String> {
    try {
        val searchStr = "\"$key\":["
        val startIdx = json.indexOf(searchStr)
        if (startIdx == -1) return emptyList()
        val contentStart = startIdx + searchStr.length
        val endIdx = json.indexOf("]", contentStart)
        if (endIdx == -1) return emptyList()
        val arrayContent = json.substring(contentStart, endIdx)
        return arrayContent.split(",").map { it.replace("\"", "").trim() }
    } catch (e: Exception) {
        return emptyList()
    }
}

fun getWeatherConditionByCode(code: Int): String {
    return when (code) {
        0 -> "Sunny / స్పష్టంగా ఉంది"
        1, 2, 3 -> "Partly Cloudy / పాక్షికంగా మేఘావృతం"
        45, 48 -> "Foggy / పొగమంచు"
        51, 53, 55 -> "Drizzle / చినుకులు"
        61, 63, 65 -> "Rain / వర్షం"
        71, 73, 75 -> "Snow / మంచు వర్షం"
        80, 81, 82 -> "Showers / కుండపోత వర్షం"
        95, 96, 99 -> "Thunderstorm / ఉరుములతో కూడిన వర్షం"
        else -> "Clear / అనుకూల వాతావరణం"
    }
}

fun getFallbackForecast(city: String): List<ForecastDay> {
    val base = when (city) {
        "Hyderabad" -> 35
        "Vijayawada" -> 38
        "Visakhapatnam" -> 33
        "Tirupati" -> 37
        "Amaravati" -> 39
        else -> 35
    }
    return listOf(
        ForecastDay("Today", "${base}°C", "${base - 10}°C", "Sunny / ప్రకాశవంతంగా ఉంది"),
        ForecastDay("Tomorrow", "${base + 1}°C", "${base - 9}°C", "Partly Cloudy / కొద్దిగా మేఘావృతం"),
        ForecastDay("Day after", "${base - 1}°C", "${base - 11}°C", "Rain / వర్షం పడే అవకాశం"),
        ForecastDay("Friday", "${base}°C", "${base - 10}°C", "Thunderstorm / ఉరుములు"),
        ForecastDay("Saturday", "${base - 2}°C", "${base - 12}°C", "Windy / గాలులతో కూడిన వాతావరణం")
    )
}

@Composable
fun ExploreSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
