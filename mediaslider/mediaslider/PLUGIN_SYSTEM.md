# MediaSlider Plugin System

`MediaSliderView` and `MediaSliderController` support pluggable behavior through `MediaSliderConfiguration`.

## Plugin types

- `SliderControllerPlugin`: customize or extend overlay controller behavior (buttons, lifecycle events).
- `SliderViewPlugin`: render/refresh view-level UI (metadata, overlays, custom chrome).
- `SliderKeyEventPlugin`: intercept key events with chain behavior.

### Controller Plugins

Controller plugins can contribute buttons to the controller row by overriding `provideControllerButton`. The system automatically manages the placement and clearing of these buttons during pagination.

Recommended way to create a button:
```kotlin
class MyButtonPlugin : SliderControllerPlugin {
    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? {
        val button = createDefaultControllerButton(
            context = context,
            contentDescriptionRes = R.string.my_button_desc,
            iconRes = R.drawable.ic_my_icon
        )
        button.setOnClickListener { /* ... */ }
        
        return ControllerButtonSpec(
            button = button,
            placement = ControllerButtonPlacement.RIGHT_OF,
            anchorViewId = R.id.media_play_pause
        )
    }
}
```

Available placements:
- `START`
- `END`
- `LEFT_OF` (requires `anchorViewId`)
- `RIGHT_OF` (requires `anchorViewId`)

### View Plugins

Plugins can mount UI directly on the slider root through `SliderViewPlugin.attachView(rootView: ConstraintLayout)`.
Plugins use `SliderViewPluginContext` to access the lifecycle and data.

Example:
```kotlin
class CustomOverlayPlugin : SliderViewPlugin<Any> {
    override fun attachView(rootView: ConstraintLayout, state: Any?) {
        val myView = View.inflate(rootView.context, R.layout.my_overlay, null)
        val params = ConstraintLayout.LayoutParams(...)
        rootView.addView(myView, params)
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: Any?
    ) {
        // Update overlay content based on the current item
        state?.findViewById<TextView>(R.id.label)?.text = sliderItem.mainItem.name
    }
}
```

Methods:
- `createState`: Initialize any state needed for the plugin (e.g. an adapter or a custom object).
- `attachView`: Inflate and add views to the `rootView`.
- `onLoadConfig`: Update UI based on initial configuration.
- `onPageSettled`: Reactive update when a new page is fully selected and settled.
- `onPageSelected`: Reactive update as soon as a page is selected (may be during scrolling).
- `onDestroy`: Cleanup views and resources.

### Key Event Plugins

Plugins can intercept key events before they reach the default slider behavior.

Example:
```kotlin
class MyKeyPlugin : SliderKeyEventPlugin {
    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            // Show custom menu
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        return SliderKeyEventResult.UNHANDLED
    }
}
```

Chain behavior for `onKeyDown`:
- `UNHANDLED`: continue to the next plugin or default behavior.
- `HANDLED_CONTINUE`: mark as handled by this plugin, but allow other plugins to run.
- `HANDLED_CONSUME`: stop the pipeline and consume the event.
- `DISPATCH_TO_SUPER`: stop the pipeline and dispatch to the parent view.

## Register plugins

Pass plugins via `MediaSliderConfiguration`:

```kotlin
MediaSliderConfiguration(
    // ...
    controllerPlugins = listOf(FavoriteButtonControllerPlugin()),
    viewPlugins = listOf(MetadataViewPlugin()),
    keyEventPlugins = listOf(MyKeyPlugin())
)
```

## Built-in Plugins

- `MetadataViewPlugin`: Layered date/EXIF overlays, Enter/Back details toggle (register the same instance on view + controller + key lists).
- `MediaRemoteControlsKeyEventPlugin`: Remote FF/RW tap-vs-hold seek, photo play/pause slideshow, opt-in D-pad seek, Back-exits-slideshow-without-pausing.
- `FavoriteButtonControllerPlugin`: Adds a toggle for favoriting assets.
- `ExternalPlayerButtonControllerPlugin`: Adds a button to open videos in an external player.
- `TimelineStoryProgressPlugin`: Renders a segment-based progress bar (IG/Snapchat style) and manages slideshow timing.
- `TopRightSlotViewPlugin`: Provides a container in the top-right corner for custom overlays.

Key event plugins may also implement `onKeyUp` (dispatched for `ACTION_UP`) for tap-vs-hold gestures.

## Implementation Details

- **Button Clearing**: The `MediaSliderController` automatically removes any views tagged with `"controller_plugin_button"` from the button row before re-configuring plugins for a new page.
- **Contexts**:
    - `ControllerPluginContext`: Provides access to the current `SliderItem`, `isVideo` status, and the `MediaSliderController`.
    - `SliderViewPluginContext`: Provides access to `CoroutineScope`, `rootView`, and a provider for the current `SliderItem`.
