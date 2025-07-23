# The Purramid - Implementation Decisions

## All App-Intents
- **Reviewed**: 14 June 2025
- **Architecture**: Instance management
- **Key Decisions**:
  - Do not use AtomicInteger for instance numbering
  - Use thepurramid.instance.InstanceManager.kt

- **Reviewed**: 15 June 2025
- **Architecture**: Service settings Window Implementation
- **Key Decisions**:
  - Settings should open at the center of the screen
  - Use an explosion animation
  - Future efforts may revise this implemenation to have settings open from the settings button in the app-intent window
 
- **Reviewed**: 20 June 2025
- **Architecture**: HiltViewModelFactory is incompatible with LifecycleService
- **Key Decisions**:
  - Use the standard ViewModelProvider with just the factory
  - Use a unique key for each ViewModel instance
  - Add an "initialize()" method to set the instance ID after creation
  - Remove the HiltViewModelFactory usage
 
- **Reviewed**: 30 June 2025
- **Architecture**: Change Traffic Light colors programmatically
- **Key Decisions**:
  - Use a single circle icon ic_circle_base
  - Change the color of that icon programmatically
  - Do not use tint values for traffic lights
  - Do not use ic_circle_*_filled where * is color
  
- **Reviewed**: 3 July 2025
- **Architecture**: Add a sequence selection marquee to traffic light UI
- **Key Decisions**:
  - Do not use edit sequence for sequence selection
    - Remove selection functionality from settings
  - Add a marquee (a rectangle with rounded corners, as used in Randomizers) to the bottom of the traffic light
    - The marquee is only present when in Timed Change mode
	- The marquee shows the title of the active sequence
	- If the teacher has not manually selected a sequence, the active sequence is the last one edited
	- The marquee includes a drop down triangle that opens a drop down list
  - The drop down list shows all available sequences
    - The active sequence is the top option
	- All other options are listed in alphanumeric order
  
- **Reviewed**: 5 July 2025
- **Architecture**: Randomizer Spin Animation Duration
- **Key Decisions**:
  - Randomziers spin animation has a cap of 3 seconds for lists that have 30 items or more.
    - Previously it was 1 second for every ten items, rounded up, which meant max time could be five seconds.
  
- **Reviewed**: 6 July 2025
- **Architecture**: Window Management for Activities
- **Key Decisions**:
  - Randomziers and Probabilities use ui.WindowState.kt to manage windows
    - Add a "floating window" theme
	- Add window dragging functionality to activities
	- Services continue to use WindowManager.LayoutParams
  
- **Reviewed**: 10 July 2025
- **Architecture**: SharedPreferences for Probabilities
- **Key Decisions**:
  - Probabilities will not use Room database
    - Preferences stored are simple key-value pairs that don't require queries or complex relationships.
	- SharedPreferences with JSON serialization is appropriate

- **Reviewed**: 10 July 2025
- **Architecture**: Icon background color fill 808080
- **Key Decisions**:
  - This is a placeholder solution used in all app intents
    - A final solution will be decided on once Probabilities is correct
	- Leave the 808080 color fill as is for now.
  
- **Reviewed**: 15 July 2025
- **Architecture**: Persistence of coin position in free-form mode
- **Key Decisions**:
  - The position of coins moved by users should persist across restarts
    - Store coin position in preferences along with other persisted values
  
- **Reviewed**: 18 July 2025
- **Architecture**: Button Activation for all app-intents (fragment/layout files)
- **Key Decisions**:
- Button icons will be 757575 when inactive (default state)
- Button icons will be 2196F3 when active (active state)
- Button background will be E3F2FD when active
- A ripple effect will occur with the onTouch event that activates the button.
- Use isActivated property for boolean on/off states  
  
  

## MainActivity
- **Reviewed**: [Date]
- **Architecture**: Activity without ViewModel
- **Key Decisions**:
  - Curved list uses custom RecyclerView.LayoutManager
  - App-intent launching via explicit Intents
  - No persistence needed

## Clock
- **Reviewed**: [Date]
- **Architecture**: Foreground Service + ViewModel
- **Key Decisions**:
  - Time zones use java.time API
  - 3D globe uses SceneView library
  - Alarms stored in Room, not system AlarmManager
  
[Continue for each app-intent]