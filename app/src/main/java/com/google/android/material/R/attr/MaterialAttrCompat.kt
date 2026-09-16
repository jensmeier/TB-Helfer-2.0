package com.google.android.material.R.attr

/**
 * Android 200049 compatibility alias.
 *
 * The currently used Material dependency does not expose
 * com.google.android.material.R.attr.colorError, while the generated photo-source patch
 * references exactly that qualified name. Android itself provides colorError since API 23,
 * which is also TB-Helfer's minSdk. Keeping the alias here lets the generated source use the
 * active theme error color without hard-coding a red value or changing the camera/KM logic.
 *
 * Remove this alias once the generated 200049 source patch uses android.R.attr.colorError
 * directly or the generated-source bridge is retired.
 */
const val colorError: Int = android.R.attr.colorError
