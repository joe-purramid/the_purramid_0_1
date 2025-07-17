# Probabilities Outstanding Tasks

## Feature Validation
- ensure all features defined in the specification document have been accurately implemented

## Complete Missing Dice Features
- Add Modifiers UI (spec 14.5.8)
- Graph Distribution implementation (spec 14.5.10)
- Proper dice grouping and spacing (spec 7.2)

## Complete Missing Coin Flip Features
- Free form drag functionality (spec 15.5.4.5)
- Two columns probability display (spec 15.5.6.4.1)
- Grid probability displays (3x3, 6x6, 10x10) (spec 15.5.6.4.2-4)
- Graph distribution for coins (spec 15.5.7)

## Animation Requirements
- 1 second roll animation for dice (spec 14.2.3)
- 1 second flip animation for coins (spec 15.2.3)
- 0.5 second animation for manual distribution (spec 14.5.10.1.1.1)

## Window State Management
- Current: Uses SharedPreferences for window state
- Required: Should use WindowState.kt per review decision (July 6)

## Code Quality Issues
- Error Handling: Try-catch blocks suppress exceptions without proper logging
- Memory Leaks: No cleanup of position data when instances are destroyed
- Duplicate Code: Similar update patterns could be abstracted in DiceViewModel.kt and CoinFlipViewModel.kt

## Bug Detection
- updateSettings() methods require Context parameter but ViewModels shouldn't have Context
- DicePoolDialogFragment.kt Nested class with same name causes compilation error
- Implement all toggle listeners with proper mutual exclusivity
- Add "Add Another" functionality
- Implement settings persistence per instance

## Answers to Questions
1. Percentile Dice Implementation: The spec mentions percentile dice use two d10s (14.5.2.5.2). Should these be displayed as separate dice or combined?

Answer: Visually, the dice should remain separate, each with its own numeric result. Specifications address how display is managed when Announcement settings are turned on.

2. Graph Distribution: For manual distribution with over 50 results, should the graph auto-scale or require user interaction?

Answer: The graph should auto-scale.

3. Free Form Persistence: Should coin positions in free form mode persist across app restarts?

Answer: Yes

4. Color Customization: The spec mentions PurramidPalette but the implementation uses custom color pickers. Which approach is correct?

Answer: Use PurramidPalette. Custom color pickers is an artifact from when Probabilites was part of the Randomizers app-intent.