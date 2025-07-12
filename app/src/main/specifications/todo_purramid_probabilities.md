# Probabilities Outstanding Tasks

## Core Features
- ensure all features defined in the specification document have been accurately implemented

## Graph Implementation
- ensure graphing has been fully and accurately implemented
  - bar chart/histogram
  - line graph
  - Q-Q scatter plot
  - manual distribution (per-roll graphing)
  - normal distribution (Box-Muller transformation)
  - uniform distribution
  - graph toggle button and display area

## Touch and Window Handling
- Implement window resize/move (via FloatingWindowActivity)
- Add individual die/coin touch for free form
- Implement drag functionality for free form coins

## Free Form Position Restoration
- properly handle free form layout
  - Current implementation finds coins in RecyclerView
  - Free form coins are in FrameLayout and need different handling
- implement proper position restoration for free form coins

## Grid Cell Animation
When filling multiple grid cells at once (spec 15.5.6.4.2.2.1), there should be sequential animations.

## Coin Pool Dialog Implementation
The CoinPoolDialogFragment is referenced but not implemented.

## Settings Implementation
- The settings fragment needs proper handling for coin-specific settings.
- Fix mode toggle in ProbabilitiesSettingsFragment
- Implement all toggle listeners with proper mutual exclusivity
- Add "Add Another" functionality
- Implement settings persistence per instance

## UI Dialogs and Popups
- Error Handling for Max Coins (Per spec 15.4.2.4.2.1 and 15.4.2.5.2.1, maximum 10 coins per type)
- Complete dice pool popup (with up/down arrows)
- Complete coin pool popup (with up/down arrows)
- Fix color picker dialogs to use PurramidPalette
- Complete modifier configuration dialog
- Complete graph distribution settings dialog

## Flip Animation Timing
The flip animation should properly show the coin rotating through heads/tails states during the animation.

## Missing Layout Elements and Drawables
- Implement announcement overlay
- item_probability_grid_cell.xml layout file
- Proper two-column coin display areas
- d20_result_blank.xml
- ic_coin_pool.xml
- ic_dice_pool.xml
- ic_graph.xml
- ic_back.xml