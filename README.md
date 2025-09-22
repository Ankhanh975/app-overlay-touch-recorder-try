# App Overlay Touch Recorder

A sophisticated Android accessibility service that provides real-time FPS monitoring and comprehensive touch event recording with a generalized overlay system.

## 🚀 Features

### Current Functionality
- **Real-time FPS Counter**: Displays current frame rate in landscape or fullscreen mode
- **Touch Event Recording**: Captures and logs various touch interactions
- **Scrollable Event Log**: View all recorded touch events with timestamps
- **Interactive Controls**: Toggle log visibility and clear recorded events
- **System Overlay**: Non-intrusive overlay that appears above other apps
- **Accessibility Integration**: Uses Android's accessibility service for comprehensive event capture

### Recorded Touch Events
- **Clicks**: Single tap interactions
- **Long Clicks**: Press and hold gestures
- **Swipes**: Directional swipe gestures with velocity calculation
- **Scrolls**: Scrolling interactions
- **Focus Events**: UI focus changes
- **Selection Events**: Item selections
- **Text Changes**: Text input modifications
- **Gestures**: Complex gesture detections

## 🏗️ Architecture

### Generalized Overlay System
The app has been refactored with a flexible, extensible overlay system that supports arbitrary UI elements:

#### Configuration Classes
```kotlin
// Overlay window configuration
data class OverlayConfig(
    val width: Int,
    val height: Int,
    val gravity: Int,
    val x: Int,
    val y: Int,
    val flags: Int,
    val pixelFormat: Int
)

// UI element configuration
data class UIElementConfig(
    val type: UIElementType,
    val text: String?,
    val textSize: Float,
    val textColor: Int,
    val backgroundColor: Int,
    val padding: Int,
    val gravity: Int,
    val onClick: (() -> Unit)?,
    val isVisible: Boolean
)
```

#### Supported UI Elements
- `TEXT_VIEW`: Configurable text displays
- `BUTTON`: Interactive buttons with click handlers
- `LIST_VIEW`: Scrollable lists with custom adapters
- `LINEAR_LAYOUT`: Flexible layout containers
- `FRAME_LAYOUT`: Frame-based layouts

#### Core Methods
- `createOverlay()`: Core overlay creation method
- `showOverlay()`: Main overlay display
- `hideOverlay()`: Generic overlay hiding
- `showCustomOverlay()`: Custom overlay configurations
- `showTextOverlay()`: Simple text overlays
- `showButtonOverlay()`: Button overlays
- `showCustomMultiElementOverlay()`: Complex multi-element overlays

## 📱 Usage

### Setup
1. **Install the app** on your Android device
2. **Grant overlay permission** when prompted
3. **Enable accessibility service** in Settings > Accessibility
4. **Rotate to landscape** or use fullscreen apps to see the overlay

### Controls
- **FPS Display**: Shows real-time frame rate at the top
- **Hide/Show Log**: Toggle touch event log visibility (clickable button)
- **Clear Log**: Remove all recorded events (clickable button)
- **Scrollable Log**: Browse through recorded touch events (fully scrollable)
- **Draggable Overlay**: Touch and drag the overlay to reposition it

### Event Log Format
```
🆕 CLICK at (540, 1200)       14:32:20.012  ← Newest (Green)
SWIPE RIGHT (1200px/s)        14:32:18.789 - 250ms
LONG_CLICK at (300, 800)      14:32:16.456
SCROLL at (400, 600)          14:32:15.123  ← Oldest
```

**Note**: Events are displayed with newest first, and the most recent event is highlighted in green with a 🆕 indicator.

## 🛠️ Technical Details

### Dependencies
- **Android SDK**: API 21+ (Android 5.0+)
- **Kotlin**: Modern Kotlin with coroutines
- **Jetpack Compose**: For main app UI
- **Traditional Views**: For system overlays
- **Accessibility Service**: For touch event capture

### Permissions
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.ACCESSIBILITY_SERVICE" />
```

### Key Components
- `FpsOverlayService`: Main accessibility service
- `MainActivity`: App launcher with Compose UI
- `TouchEventAdapter`: Custom adapter for event display
- `OverlayConfig`: Overlay configuration system
- `UIElementConfig`: UI element configuration system

## 🔧 Development

### Building
```bash
./gradlew assembleDebug
```

### Project Structure
```
app/
├── src/main/
│   ├── java/com/appoverlaytouchrecorder/
│   │   ├── FpsOverlayService.kt    # Main service with overlay system
│   │   └── MainActivity.kt         # App launcher
│   ├── res/
│   │   ├── xml/accessibility_service_config.xml
│   │   └── values/strings.xml
│   └── AndroidManifest.xml
├── build.gradle
└── proguard-rules.pro
```

### Extending the Overlay System

#### Adding New UI Elements
1. Add new type to `UIElementType` enum
2. Create builder method in service class
3. Update `showCustomMultiElementOverlay()` method

#### Creating Custom Overlays
```kotlin
// Simple text overlay
showTextOverlay(
    text = "Custom Message",
    position = Pair(50, 150),
    size = Pair(300, 100)
)

// Button overlay
showButtonOverlay(
    text = "Custom Action",
    onClick = { /* handle click */ },
    position = Pair(50, 250),
    size = Pair(200, 80)
)

// Multi-element overlay
val elements = listOf(
    UIElementConfig(type = UIElementType.TEXT_VIEW, text = "Header"),
    UIElementConfig(type = UIElementType.BUTTON, text = "Action", onClick = { /* action */ })
)
showCustomMultiElementOverlay(elements, LinearLayout.VERTICAL)
```

## 🎯 Future Enhancements

The generalized overlay system enables easy addition of:
- **Custom UI Elements**: Charts, graphs, custom views
- **Multiple Overlays**: Simultaneous different overlay types
- **Dynamic Content**: Real-time data updates
- **Theming System**: Customizable colors and styles
- **Animation Support**: Smooth transitions and effects
- **Advanced Positioning**: Smart positioning algorithms

## 📋 Requirements

- **Android**: 5.0+ (API 21+)
- **Permissions**: System overlay and accessibility
- **Orientation**: Works best in landscape mode
- **Accessibility**: Must be enabled in device settings

## 🐛 Known Limitations

- Touch event capture depends on accessibility service limitations
- Some system apps may not generate accessibility events
- Overlay positioning is fixed (future enhancement: draggable overlays)
- FPS calculation is approximate (based on update frequency)

## 📄 License

This project is for educational and development purposes. Please ensure compliance with Android accessibility service guidelines when using in production.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

---

**Note**: This app requires accessibility service permissions and system overlay permissions to function properly. These permissions are necessary for the app to capture touch events and display overlays above other applications.