##Code Quality Issues

###Missing Declarations
1. File: RandomizerMainFragment.kt
Current: Missing particleSystems declaration

2. File: SpinDialView.kt
Current: Missing variables and functions for the following terms:
- dialRadius
- startAnimation
- invalidate
- settings
- context
- textBounds
- textPaint
- arrowStrokePaint
- arrowPaint

3. File: RandomizerSettingsFragment
Current: All bindings are showing terms as undefined (e.g., switchSpinResultAnnouncement).  
Is the binding variable properly initialized?

###Memory Leak Risk in SpinDialView
File: SpinDialView.kt
Current: Bitmap cache without lifecycle management

>private val imageBitmapCache = ConcurrentHashMap<UUID, Bitmap?>()
>// Never cleared, bitmaps not recycled

### Inefficient Adapter Updates
File: ItemEditorAdapter.kt
Current: Updates trigger full list refresh
Required: Use DiffUtil properly with payload updates

### Database Migration Issues
File: PurramidDatabase.kt
Current: Migration 15 has incomplete implementation  
The migration renames columns but doesn't handle the related foreign keys and indices properly.

### Missing Navigation Actions
File: RandomizerSettingsFragment.kt
Current: References undefined navigation actions
Required: Define in navigation graph

#### Recommendations
**Immediate Priority**:
- Fix architecture to use Repository pattern
- Resolve Service vs Activity architecture question
- Implement missing preloaded lists
- Fix database migrations
- Add missing string resources

**Next Priority**:
- Implement proper gesture handling for resize/move
- Create missing drawable resources
- Fix memory management in SpinDialView
- Implement custom color picker per spec

**Lower Priority**:
- Optimize adapter updates
- Consistent field naming
- Code cleanup and TODOs


## Questions

### Service vs Activity Architecture
The spec lists Randomizers as "Activity only" but requires multi-window support. This conflicts with the technical decision that all overlays use Foreground Services. Should Randomizers be refactored to use a Service-based architecture like Clock/Timers?

*Answer*: 
1. Randomizers runs no background services. This was the primary factor in deciding not to create services. The solution instead is to create a custom window theme and use a WindowState manager to create a windonwed experience similar to the service app intents.
2. Review the custom window implementation and address the following:
	1. Is it the best solution for creating a headless window experience that matches the service app-intent experience?
	2. Was it implemented completely?
		1. Are resize and move gestures accommodated, per the specs?
		2. Are pass-through events supported, per the specs, either by default or by supported code?
	3. Was it implemented correctly?

### Dice/Coin References
Several enums and converters still reference Dice and Coin modes (e.g., DiceSumResultType, CoinProbabilityMode in Converters.kt). Should these be removed completely or are they shared with the Probabilities app-intent?

*Answer*: Dice and Coin Flip were moved into a new app-intent named Probabilities. They would continue to use the enums and converters.

### Instance Limit Discrepancy
The spec mentions supporting "up to 4" instances but also states "Dice mode allows for seven instances". Since Dice is moved to Probabilities, should the constant be 4 or 7?

*Answer*: Randomizers (spin and slots) supports up to 4 instances. Probabilities (dice and coin flip) supports up to 7 instances.

### Color Square Behavior
Spec 10.3.2.3.2.4.2.1 describes detailed color picker requirements, but ItemEditorAdapter uses a third-party library. Should we implement the custom color picker per spec?

*Answer*: 
1. If an existing library provides the functionality dictated in the specs, it is okay to deviate from the proposed visual design. 
2. Propose a library or libraries that would best accomplish the specified color picker features.
3. Make a recommendation on whether a custom solution would be a better choice.


## Deferred Action

Icon state management (i.e., the color of a button when in its active state) is currently a placeholder. A solution will be consistent across app-intents. That solution will be applied in the future. Continue to use the existing placeholder.