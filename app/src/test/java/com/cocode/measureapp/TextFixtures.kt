package com.cocode.measureapp

import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText

/** Any message, for tests that only need "some text" and not a particular one. */
val SOME_TEXT = UiText(TextKey.CAUSE_CALCULATION_UNSTABLE)

/** Every message key in this text, the nested ones included, outermost first. */
fun UiText.allKeys(): List<TextKey> =
    listOf(key) + args.filterIsInstance<UiText.TextArg.Message>().flatMap { it.text.allKeys() }

/** True when this text, or a message inside it, is [wanted]. */
infix fun UiText.mentions(wanted: TextKey): Boolean = wanted in allKeys()
