package com.example.nammarailubuddy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

data class Station(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class TrainInfo(
    val trainName: String,
    val currentStation: String,
    val destinationStation: String,
    val platformNumber: Int,
    val confirmedBy: Int,
    val arrivalStatus: String,
    val coachPosition: CoachPosition
)

data class CoachPosition(
    val general: String,
    val ladies: String,
    val reserved: String
)

private val stations = listOf(
    Station("Mandya", 12.5218, 76.8951),
    Station("Mysuru", 12.2958, 76.6394),
    Station("Ramanagara", 12.7216, 77.2812),
    Station("Bengaluru", 12.9784, 77.6408)
)

private val trainNames = listOf(
    "Chamundi Passenger 123",
    "Mysuru Local 456",
    "Mandya MEMU 789"
)

private val RailGreen = Color(0xFF0E6B4F)
private val RailDeep = Color(0xFF09382D)
private val RailCream = Color(0xFFF8F6EF)
private val RailAmber = Color(0xFFFFB703)
private val RailBlue = Color(0xFF1D4E89)
private val RailCoral = Color(0xFFE85D75)
private val RailInk = Color(0xFF17211E)
private val RailMist = Color(0xFFE9F1EE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NammaRailuBuddyApp()
        }
    }
}

@Composable
fun NammaRailuBuddyApp() {
    val context = LocalContext.current
    var alarmMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDestinationName by rememberSaveable { mutableStateOf(stations[1].name) }
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        alarmMessage = if (granted) {
            checkDestinationAlarm(context, selectedDestinationName)
        } else {
            "Location permission is needed for the destination alarm."
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = RailCream
        ) {
            if (isLoggedIn) {
                MainScreen(
                    selectedDestinationName = selectedDestinationName,
                    onDestinationChanged = { selectedDestinationName = it },
                    onCheckAlarm = {
                        if (hasLocationPermission(context)) {
                            alarmMessage = checkDestinationAlarm(context, selectedDestinationName)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onSimulateNearDestination = {
                        alarmMessage = "You are near $selectedDestinationName. Please get ready to deboard."
                        vibrateForAlert(context)
                    },
                    onLogout = {
                        isLoggedIn = false
                        alarmMessage = null
                    }
                )
            } else {
                LoginScreen(
                    onLogin = { username, password ->
                        if (username.isBlank() || password.isBlank()) {
                            alarmMessage = "Enter your username and password to continue."
                        } else {
                            isLoggedIn = true
                            alarmMessage = null
                        }
                    }
                )
            }
        }
    }

    alarmMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { alarmMessage = null },
            confirmButton = {
                TextButton(onClick = { alarmMessage = null }) {
                    Text("OK")
                }
            },
            title = { Text(if (isLoggedIn) "Destination Alarm" else "Login Required") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun LoginScreen(
    onLogin: (String, String) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RailDeep, RailGreen, Color(0xFFFAF4E8))
                )
            )
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(RailAmber.copy(alpha = 0.26f))
        )
        Box(
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.BottomStart)
                .clip(CircleShape)
                .background(RailCoral.copy(alpha = 0.24f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Namma-Railu Buddy",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Smart local train companion",
                color = Color(0xFFE6FFF6),
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
            )

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Welcome back",
                        color = RailInk,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sign in to view train guide, platform updates, and destination alarm.",
                        color = Color(0xFF5F6E69),
                        fontSize = 15.sp
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        placeholder = { Text("Enter your username") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        placeholder = { Text("Enter your password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onLogin(username.trim(), password) },
                        colors = ButtonDefaults.buttonColors(containerColor = RailGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Login", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LoginStat("Live", "Platform", Modifier.weight(1f))
                LoginStat("5 km", "Alarm", Modifier.weight(1f))
                LoginStat("Fast", "Guide", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LoginStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFE8FFF8), fontSize = 12.sp)
    }
}

@Composable
private fun MainScreen(
    selectedDestinationName: String,
    onDestinationChanged: (String) -> Unit,
    onCheckAlarm: () -> Unit,
    onSimulateNearDestination: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedCurrentStation by rememberSaveable { mutableStateOf(stations[0].name) }
    var selectedTrain by rememberSaveable { mutableStateOf(trainNames[0]) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var platformNumber by rememberSaveable { mutableStateOf(2) }
    var confirmedBy by rememberSaveable { mutableStateOf(14) }

    LaunchedEffect(selectedCurrentStation, selectedDestinationName, selectedTrain) {
        platformNumber = when (selectedCurrentStation) {
            "Mandya" -> 2
            "Mysuru" -> 1
            "Ramanagara" -> 3
            else -> 4
        }
        confirmedBy = when (selectedTrain) {
            "Chamundi Passenger 123" -> 14
            "Mysuru Local 456" -> 8
            else -> 5
        }
    }

    val trainInfo = TrainInfo(
        trainName = selectedTrain,
        currentStation = selectedCurrentStation,
        destinationStation = selectedDestinationName,
        platformNumber = platformNumber,
        confirmedBy = confirmedBy,
        arrivalStatus = when (selectedTrain) {
            "Chamundi Passenger 123" -> "Expected in 10 minutes"
            "Mysuru Local 456" -> "Arriving soon"
            else -> "Delayed by 5 minutes"
        },
        coachPosition = CoachPosition(
            general = "Front side",
            ladies = "Middle section",
            reserved = "Rear side"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderCard(onLogout = onLogout)

        SelectorCard(
            selectedCurrentStation = selectedCurrentStation,
            selectedDestinationName = selectedDestinationName,
            selectedTrain = selectedTrain,
            onCurrentStationChanged = { selectedCurrentStation = it },
            onDestinationChanged = onDestinationChanged,
            onTrainChanged = { selectedTrain = it },
            onShowGuide = { showGuide = true }
        )

        if (showGuide) {
            TrainGuideCard(trainInfo)

            PlatformPingCard(
                platformNumber = platformNumber,
                confirmedBy = confirmedBy,
                onConfirmPlatform = { confirmedBy += 1 },
                onReportDifferentPlatform = {
                    platformNumber = if (platformNumber == 4) 1 else platformNumber + 1
                    confirmedBy = 1
                }
            )

            DestinationAlarmCard(
                destinationName = selectedDestinationName,
                onCheckAlarm = onCheckAlarm,
                onSimulateNearDestination = onSimulateNearDestination
            )

            FirebaseUpgradeCard()
        }
    }
}

@Composable
private fun HeaderCard(onLogout: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RailDeep),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(RailDeep, RailGreen, RailBlue)
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Namma-Railu Buddy",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your local train trip, organized",
                            color = Color(0xFFE6FFF6),
                            fontSize = 15.sp
                        )
                    }
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeaderMetric("3", "Trains", Modifier.weight(1f))
                    HeaderMetric("4", "Stations", Modifier.weight(1f))
                    HeaderMetric("5 km", "Alarm", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeaderMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color(0xFFE8FFF8), fontSize = 12.sp)
    }
}

@Composable
private fun SelectorCard(
    selectedCurrentStation: String,
    selectedDestinationName: String,
    selectedTrain: String,
    onCurrentStationChanged: (String) -> Unit,
    onDestinationChanged: (String) -> Unit,
    onTrainChanged: (String) -> Unit,
    onShowGuide: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Station Selector")
            RailDropdown(
                label = "Current station",
                selectedValue = selectedCurrentStation,
                options = stations.map { it.name },
                onValueChanged = onCurrentStationChanged
            )
            RailDropdown(
                label = "Destination station",
                selectedValue = selectedDestinationName,
                options = stations.map { it.name },
                onValueChanged = onDestinationChanged
            )
            RailDropdown(
                label = "Train name / number",
                selectedValue = selectedTrain,
                options = trainNames,
                onValueChanged = onTrainChanged
            )
            Button(
                onClick = onShowGuide,
                colors = ButtonDefaults.buttonColors(containerColor = RailGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Train Guide", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun TrainGuideCard(trainInfo: TrainInfo) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Train Guide")
            Text(
                text = trainInfo.trainName,
                fontSize = 22.sp,
                color = RailBlue,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${trainInfo.currentStation} to ${trainInfo.destinationStation}",
                fontSize = 18.sp
            )
            StatusPill(trainInfo.arrivalStatus)
            CoachRow("General Coach", trainInfo.coachPosition.general)
            CoachRow("Ladies Coach", trainInfo.coachPosition.ladies)
            CoachRow("Reserved Coaches", trainInfo.coachPosition.reserved)
        }
    }
}

@Composable
private fun PlatformPingCard(
    platformNumber: Int,
    confirmedBy: Int,
    onConfirmPlatform: () -> Unit,
    onReportDifferentPlatform: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("Platform Ping")
            Text(
                text = "Platform $platformNumber",
                color = RailGreen,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Confirmed by $confirmedBy passengers",
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onConfirmPlatform,
                    colors = ButtonDefaults.buttonColors(containerColor = RailGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirm")
                }
                OutlinedButton(
                    onClick = onReportDifferentPlatform,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Report Different")
                }
            }
        }
    }
}

@Composable
private fun DestinationAlarmCard(
    destinationName: String,
    onCheckAlarm: () -> Unit,
    onSimulateNearDestination: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Destination Alarm")
            Text(
                text = "Alert will trigger within 5 km of $destinationName.",
                fontSize = 17.sp
            )
            Button(
                onClick = onCheckAlarm,
                colors = ButtonDefaults.buttonColors(containerColor = RailBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Destination Alarm")
            }
            OutlinedButton(
                onClick = onSimulateNearDestination,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Demo: Simulate Near Destination")
            }
        }
    }
}

@Composable
private fun FirebaseUpgradeCard() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Firebase upgrade path",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF704600)
            )
            Text(
                text = "Later, connect platformUpdates/train_123 in Firebase Realtime Database and replace this demo counter with live confirmations.",
                fontSize = 15.sp,
                color = Color(0xFF704600)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF1D1D1D),
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    )
}

@Composable
private fun StatusPill(text: String) {
    Text(
        text = text,
        color = Color(0xFF2B2B2B),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(RailAmber, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun CoachRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F5F7), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            color = RailGreen,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChanged(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineLocation || coarseLocation
}

private fun checkDestinationAlarm(context: Context, destinationName: String): String {
    val destination = stations.first { it.name == destinationName }
    val currentLocation = getLastKnownLocation(context)
        ?: return "Current location is not available. Turn on location, set emulator location, or use the demo simulation button."

    val results = FloatArray(1)
    Location.distanceBetween(
        currentLocation.latitude,
        currentLocation.longitude,
        destination.latitude,
        destination.longitude,
        results
    )
    val distanceKm = results[0] / 1000f

    return if (distanceKm <= 5f) {
        vibrateForAlert(context)
        "You are near $destinationName. Please get ready to deboard."
    } else {
        "You are ${"%.1f".format(distanceKm)} km away from $destinationName. Alarm will trigger within 5 km."
    }
}

private fun getLastKnownLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        listOfNotNull(gpsLocation, networkLocation).maxByOrNull { it.time }
    } catch (_: SecurityException) {
        null
    }
}

private fun vibrateForAlert(context: Context) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    vibrator.vibrate(
        VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}
