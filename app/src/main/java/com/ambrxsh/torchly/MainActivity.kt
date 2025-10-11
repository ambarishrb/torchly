package com.ambrxsh.torchly

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambrxsh.torchly.ui.theme.TorchlyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.sqrt


class MainActivity : ComponentActivity() {

    private var cameraId: String? = null
    private lateinit var cameraManager: CameraManager
    private var sosJob: Job? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private var shakeThreshold = 12f

    private var selectedScreen = "Home"
    private var isTorchOn = false

    private var maxStrength: Int? = null

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

            if (gForce > shakeThreshold) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > 200) {  // debounce
                    lastShakeTime = now
                    onShakeDetected()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun onShakeDetected() {
        // Only toggle torch if on Home or Settings screen
        if (selectedScreen == "Home" || selectedScreen == "Settings") {
            isTorchOn = !isTorchOn
            setTorch(isTorchOn)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            maxStrength = cameraManager.getCameraCharacteristics(cameraId!!)
                .get<Int?>(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
        }


        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)


        setContent {
            TorchlyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TorchHomeScreen()
                }
            }
        }
    }

    private fun setTorch(on: Boolean) {
        cameraId?.let {
            try {
                cameraManager.setTorchMode(it, on)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } ?: run {
            Toast.makeText(this, "No camera with flash found!", Toast.LENGTH_SHORT).show()
        }

    }

    private fun startSOS(vibrator: Vibrator, vibrationEnabled: Boolean) {
        stopSOS()
        sosJob = CoroutineScope(Dispatchers.IO).launch {
            val dot = 200L
            val dash = dot * 3
            val gap = dot

            val pattern = listOf(
                dot, gap, dot, gap, dot, gap * 3, // S
                dash, gap, dash, gap, dash, gap * 3, // O
                dot, gap, dot, gap, dot // S
            )

            while (isActive) {
                for (i in pattern.indices step 2) {
                    val onTime = pattern[i]
                    setTorch(true)
                    if (vibrationEnabled) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                onTime,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }
                    delay(onTime)
                    setTorch(false)
                    if (i + 1 < pattern.size) delay(pattern[i + 1])
                }
                delay(dot * 7)
            }
        }
    }


    private fun stopSOS() {
        sosJob?.cancel()
        sosJob = null
        setTorch(false)
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TorchHomeScreen() {
        var selectedScreen by remember { mutableStateOf("Home") }
        var isTorchOn by remember { mutableStateOf(false) }
        var brightness by remember { mutableStateOf(0.5f) }
        var isSOSActive by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val settingsManager = remember { SettingsManager(context) }
        val vibrator = remember {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        var shakeSensitivity by rememberSaveable { mutableStateOf(settingsManager.loadShakeSensitivity()) }

        // --- TOGGLE TORCH FUNCTION ---
        var autoOffJob: Job? = null
        fun mapTorchRange(value: Float): Int {
            val clamped = value.coerceIn(0f, 1f)
            return (clamped * (maxStrength!! - 1) + 1).toInt()
        }

        @SuppressLint("ObsoleteSdkInt")
        fun updateTorchState(isSwitchedOn: Boolean, brit: Float = -1.0f) {
            cameraId?.let {
                try {
                    isTorchOn = isSwitchedOn
                    if (isTorchOn) {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val maxStrength = cameraManager.getCameraCharacteristics(cameraId!!)
                                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                            if (maxStrength != null && maxStrength > 1) {
                                cameraManager.turnOnTorchWithStrengthLevel(
                                    cameraId!!,
                                    mapTorchRange(brit)
                                )
                            }
                        } else {
                            cameraManager.setTorchMode(cameraId!!, true)
                        }
                    } else {
                        cameraManager.setTorchMode(cameraId!!, false)
                    }
                } catch (ex: Exception) {
                    println(ex)
                    Toast.makeText(context, "", Toast.LENGTH_SHORT).show()
                }
            }

//            cameraId?.let {
//                try {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                        val maxStrength = cameraManager.getCameraCharacteristics(cameraId!!)
//                            .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
//                        if (maxStrength != null && maxStrength > 1) {
//                            cameraManager.turnOnTorchWithStrengthLevel(
//                                cameraId!!,
//                                if (isTorchOn && brit == 1) brit else 0
//                            );
//                        }
//                    }
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//        }

            // Cancel any existing timer
            autoOffJob?.cancel()

            // Set duration based on dropdown choice
            val durationMs = when (settingsManager.loadAutoOffTimer()) {
                "2 min" -> 2 * 60_000L
                "5 min" -> 5 * 60_000L
                "10 min" -> 10 * 60_000L
                else -> null
            }

            durationMs?.let { ms ->
                autoOffJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(ms)
                    isTorchOn = false
                    cameraId?.let { camId ->
                        (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
                            .setTorchMode(camId, false)
                    }
                }
            }
        }


        // --- SHAKE DETECTOR ---
        val shakeDetector = remember {
            ShakeDetector(
                context = context,
                shakeThreshold = 12f - shakeSensitivity * 6f,
                onShake = {
                    if (!settingsManager.loadShakeToFlash()) return@ShakeDetector
                    if (selectedScreen == "Home" || selectedScreen == "Settings") {
                        updateTorchState(!isTorchOn, brightness)
                        if (settingsManager.loadVibrationEnabled()) {
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                    100,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        }
                    }
                }
            )
        }

        DisposableEffect(shakeDetector)
        {
            shakeDetector.start()
            onDispose { shakeDetector.stop() }
        }

        Scaffold(
            topBar =
                {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = when (selectedScreen) {
                                    "Home" -> "Torch"
                                    "SOS" -> "SOS"
                                    "Morse" -> "Morse Code Converter"
                                    "Settings" -> "Settings"
                                    else -> ""
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    )
                },
            bottomBar =
                {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedScreen == "Home",
                            onClick = { selectedScreen = "Home" },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Torch") }
                        )
                        NavigationBarItem(
                            selected = selectedScreen == "SOS",
                            onClick = { selectedScreen = "SOS" },
                            icon = {
                                Icon(
                                    painterResource(R.drawable.ic_bolt),
                                    contentDescription = "SOS"
                                )
                            },
                            label = { Text("SOS") }
                        )
                        NavigationBarItem(
                            selected = selectedScreen == "Morse",
                            onClick = { selectedScreen = "Morse" },
                            icon = {
                                Icon(
                                    painterResource(R.drawable.ic_code),
                                    contentDescription = "Morse"
                                )
                            },
                            label = { Text("Morse") }
                        )
                        NavigationBarItem(
                            selected = selectedScreen == "Settings",
                            onClick = { selectedScreen = "Settings" },
                            icon = {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = { Text("Settings") }
                        )
                    }
                }
        )
        { innerPadding ->
            val isDarkMode = isSystemInDarkTheme()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        when {
                            !isTorchOn -> Color.Transparent
                            isDarkMode -> Color(0xFF1B2652)   // dark mode background
                            else -> Color(0xFF7BBCDC)         // light mode background
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {

                when (selectedScreen) {
                    "Home" -> TorchMainContent(
                        brightness = brightness,
                        onBrightnessChange = { it ->
                            brightness = it
                            updateTorchState(true, brightness)
                        },
                        isTorchOn = isTorchOn,
                        onToggleTorch = { updateTorchState(!isTorchOn, brightness) }
                    )

                    "SOS" -> SOSScreen(
                        isSOSActive = isSOSActive,
                        onToggleSOS = {
                            if (isSOSActive) stopSOS()
                            else startSOS(vibrator, settingsManager.loadVibrationEnabled())
                            isSOSActive = !isSOSActive
                        }
                    )

                    "Morse" -> MorseScreen(
                        vibrator = vibrator,
                        vibrationEnabled = settingsManager.loadVibrationEnabled()
                    )

                    "Settings" -> {
                        var vibrationEnabled by rememberSaveable { mutableStateOf(settingsManager.loadVibrationEnabled()) }
                        var autoOffTimer by rememberSaveable { mutableStateOf(settingsManager.loadAutoOffTimer()) }
                        var shakeToFlash by rememberSaveable { mutableStateOf(settingsManager.loadShakeToFlash()) }

                        SettingsScreen(
                            shakeSensitivity = shakeSensitivity,
                            onShakeSensitivityChange = { newValue ->
                                shakeSensitivity = newValue
                                settingsManager.saveShakeSensitivity(newValue)
                                shakeDetector.updateThreshold(16f - newValue * 12f) // <-- Live update
                            },
                            vibrationEnabled = vibrationEnabled,
                            onVibrationChange = {
                                vibrationEnabled = it
                                settingsManager.saveVibrationEnabled(it)
                            },
                            autoOffTimer = autoOffTimer,
                            onAutoOffChange = {
                                autoOffTimer = it
                                settingsManager.saveAutoOffTimer(it)
                            },
                            shakeToFlash = shakeToFlash,
                            onShakeToFlashChange = {
                                shakeToFlash = it
                                settingsManager.saveShakeToFlash(it)
                            }
                        )
                    }
                }
            }
        }
    }


    @Composable
    fun TorchMainContent(
        brightness: Float,
        onBrightnessChange: (Float) -> Unit,
        isTorchOn: Boolean,
        onToggleTorch: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Button(
                onClick = onToggleTorch,
                modifier = Modifier.size(280.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTorchOn)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                )
            ) {
                // Column inside button to show icon above text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_torch), // your torch icon
                        contentDescription = "Torch Icon",
                        tint = Color.White,
                        modifier = Modifier.size(140.dp) // adjust size as needed
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isTorchOn) "Turn Off" else "Turn On",
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }


            Text(
                "Brightness",
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground)
            )

            if (maxStrength != null)
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    modifier = Modifier.width(250.dp),
                    steps = maxStrength!! - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )

        }
    }


    @Composable
    fun SOSScreen(isSOSActive: Boolean, onToggleSOS: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Button(
                onClick = onToggleSOS,
                shape = CircleShape,
                modifier = Modifier.size(180.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSOSActive)
                        Color.Red
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isSOSActive) "Stop SOS" else "Start SOS",
                    fontSize = 20.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Standard blinking speed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "This will flash your device's light in the internationally recognized SOS distress signal pattern.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    @Composable
    fun MorseScreen(vibrator: Vibrator, vibrationEnabled: Boolean) {
        var inputText by rememberSaveable { mutableStateOf("") }
        var isBlinking by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current
        var showHowItWorks by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {


            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Enter text") },
                modifier = Modifier.fillMaxWidth(0.9f),
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = { inputText = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "Clear text"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (inputText.isBlank()) return@Button
                    if (!isBlinking) {
                        keyboardController?.hide()
                        stopMorseBlink()
                        isBlinking = true
                        morseJob = scope.launch {
                            blinkMorseCode(inputText, vibrator, vibrationEnabled)
                            isBlinking = false
                        }
                    }
                },
                enabled = !isBlinking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBlinking) Color.Gray else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isBlinking) "Blinking..." else "Convert & Blink")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    stopMorseBlink()
                    isBlinking = false
                },
                enabled = isBlinking,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Stop Blinking", color = Color.White)
            }





            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Standard blinking speed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "This will flash your device's light in Morse code at the standard international rate.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )


            Spacer(modifier = Modifier.height(100.dp))

            Button(
                onClick = { showHowItWorks = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("How It Works", color = MaterialTheme.colorScheme.onSecondary)
            }
        }

        // How It Works Dialog
        if (showHowItWorks) {
            AlertDialog(
                onDismissRequest = { showHowItWorks = false },
                confirmButton = {
                    TextButton(onClick = { showHowItWorks = false }) {
                        Text("Close")
                    }
                },
                title = { Text("How Morse Code Works") },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            "Morse code is a method used to encode text using dots (·) and dashes (–). " +
                                    "It is widely used in telecommunication and emergency signals like SOS.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Standard Rules:", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Dot (·) duration = 1 unit")
                        Text("• Dash (–) duration = 3 units")
                        Text("• Space between symbols = 1 unit")
                        Text("• Space between letters = 3 units")
                        Text("• Space between words = 7 units")

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Example:", fontWeight = FontWeight.Bold)
                        Text("SOS → ··· ––– ···")

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Alphabet & Numbers:", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Left column: A-M, 1-5 | Right column: N-Z, 6-0
                        val leftColumn = listOf(
                            "A" to "·-",
                            "B" to "-...",
                            "C" to "-.-.",
                            "D" to "-..",
                            "E" to "·",
                            "F" to "..-.",
                            "G" to "--.",
                            "H" to "....",
                            "I" to "..",
                            "J" to ".---",
                            "K" to "-.-",
                            "L" to ".-..",
                            "M" to "--",
                            "1" to ".----",
                            "2" to "..---",
                            "3" to "...--",
                            "4" to "....-",
                            "5" to "....."
                        )
                        val rightColumn = listOf(
                            "N" to "-.",
                            "O" to "---",
                            "P" to ".--.",
                            "Q" to "--.-",
                            "R" to ".-.",
                            "S" to "...",
                            "T" to "-",
                            "U" to "..-",
                            "V" to "...-",
                            "W" to ".--",
                            "X" to "-..-",
                            "Y" to "-.--",
                            "Z" to "--..",
                            "6" to "-....",
                            "7" to "--...",
                            "8" to "---..",
                            "9" to "----.",
                            "0" to "-----"
                        )

                        // Display in two columns
                        // Two-column cheat sheet with proper alignment
                        val maxRows = maxOf(leftColumn.size, rightColumn.size)
                        Column {
                            for (i in 0 until maxRows) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    // Left column
                                    if (i < leftColumn.size) {
                                        val (char, code) = leftColumn[i]
                                        Text(
                                            buildAnnotatedString {
                                                withStyle(
                                                    style = androidx.compose.ui.text.SpanStyle(
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                ) {
                                                    append("$char → ")
                                                }
                                                withStyle(
                                                    style = androidx.compose.ui.text.SpanStyle(
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    append(code)
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    // Right column
                                    if (i < rightColumn.size) {
                                        val (char, code) = rightColumn[i]
                                        Text(
                                            buildAnnotatedString {
                                                withStyle(
                                                    style = androidx.compose.ui.text.SpanStyle(
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                ) {
                                                    append("$char → ")
                                                }
                                                withStyle(
                                                    style = androidx.compose.ui.text.SpanStyle(
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    append(code)
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }


                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Use this app to convert text to Morse code and blink it with your device’s light. " +
                                    "Follow the standard timings for proper representation.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )
        }

    }


    private var morseJob: Job? = null

    private fun stopMorseBlink() {
        morseJob?.cancel()
        morseJob = null
        setTorch(false)
    }

    private suspend fun blinkMorseCode(
        text: String,
        vibrator: Vibrator,
        vibrationEnabled: Boolean
    ) {
        val morseMap = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
            'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
            'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
            'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
            'Z' to "--..",
            '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
            '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
            '9' to "----.", '0' to "-----"
        )

        val dot = 200L
        val dash = dot * 3
        val gap = dot
        val upperText = text.uppercase()

        for (char in upperText) {
            coroutineContext.ensureActive()
            if (char == ' ') {
                delay(dot * 7)
                continue
            }
            val morse = morseMap[char] ?: continue
            for (symbol in morse) {
                coroutineContext.ensureActive()
                when (symbol) {
                    '.' -> {
                        setTorch(true)
                        if (vibrationEnabled) vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                dot,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                        delay(dot)
                        setTorch(false)
                    }

                    '-' -> {
                        setTorch(true)
                        if (vibrationEnabled) vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                dash,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                        delay(dash)
                        setTorch(false)
                    }
                }
                delay(gap)
            }
            delay(dash)
        }
        setTorch(false)
    }


    @Preview(showBackground = true)
    @Composable
    fun TorchHomeScreenPreview() {
        TorchlyTheme {
            TorchHomeScreen()
        }
    }

    @Composable
    fun SettingsScreen(
        shakeSensitivity: Float,
        onShakeSensitivityChange: (Float) -> Unit,
        vibrationEnabled: Boolean,
        onVibrationChange: (Boolean) -> Unit,
        autoOffTimer: String,
        onAutoOffChange: (String) -> Unit,
        shakeToFlash: Boolean,
        onShakeToFlashChange: (Boolean) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),

            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start

        ) {
            Text("Shake Sensitivity", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(24.dp))


            Slider(
                value = shakeSensitivity,
                onValueChange = onShakeSensitivityChange,
                valueRange = 0f..1f
            )

            Spacer(modifier = Modifier.height(24.dp))


            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vibration", Modifier.weight(1f))
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = onVibrationChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))


            val timerOptions = listOf("Off", "2 min", "5 min", "10 min")
            var expanded by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-Off Timer", Modifier.weight(1f))

                Box {
                    Button(onClick = { expanded = true }) {
                        Text(autoOffTimer)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        timerOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    expanded = false
                                    onAutoOffChange(option)
                                }
                            )
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(24.dp))


            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shake to Flash On", Modifier.weight(1f))
                Switch(
                    checked = shakeToFlash,
                    onCheckedChange = onShakeToFlashChange
                )
            }
        }
    }
}



