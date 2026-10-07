package com.cocode.measureapp.contracts

import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText

/** True when this text, or a message inside it, is [wanted]. */
infix fun UiText.mentions(wanted: TextKey): Boolean =
    key == wanted || args.filterIsInstance<UiText.TextArg.Message>().any { it.text mentions wanted }
