package com.floatingdj.visualizer

import android.content.Context
import android.util.AttributeSet
import android.widget.Switch

/**
 * A native Android Switch kept as the interaction/accessibility control.
 * ThemePlayThumbDrawable supplies the visible Play/Pause glyph inside its thumb.
 */
class ThemePlaySwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.switchStyle
) : Switch(context, attrs, defStyleAttr)
