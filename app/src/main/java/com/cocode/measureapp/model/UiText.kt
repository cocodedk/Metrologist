package com.cocode.measureapp.model

/**
 * A sentence for the screen. Pure code says WHICH message ([key]) and with which values
 * ([args]); the Android layer owns the words (res/values/strings.xml), so no English lives
 * in the engine and every message can be translated.
 */
data class UiText(val key: TextKey, val args: List<TextArg> = emptyList()) {
    /** One value a message slots in, in the order of the resource's `%1$`, `%2$` placeholders. */
    sealed interface TextArg {
        data class Number(val value: Double) : TextArg
        data class Message(val text: UiText) : TextArg
    }

    companion object {
        /** [key] with number arguments, e.g. an angle in degrees or a percentage. */
        fun withNumbers(key: TextKey, vararg values: Double) =
            UiText(key, values.map { TextArg.Number(it) })

        /** [key] with other messages nested in its placeholders. */
        fun withMessages(key: TextKey, vararg parts: UiText) =
            UiText(key, parts.map { TextArg.Message(it) })
    }
}
