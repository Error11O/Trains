package dev.Error110.trains.utils

internal fun progressBar10(progress: Double): String {
    return when (Math.round(progress * 10.0).toInt()) {
        0 ->  "\u2503<dark_gray>\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592<white>\u2503"
        1 ->  "\u2503\u2588<dark_gray>\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592<white>\u2503"
        2 ->  "\u2503\u2588\u2588<dark_gray>\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592<white>\u2503"
        3 ->  "\u2503\u2588\u2588\u2588<dark_gray>\u2592\u2592\u2592\u2592\u2592\u2592\u2592<white>\u2503"
        4 ->  "\u2503\u2588\u2588\u2588\u2588<dark_gray>\u2592\u2592\u2592\u2592\u2592\u2592<white>\u2503"
        5 ->  "\u2503\u2588\u2588\u2588\u2588\u2588<dark_gray>\u2592\u2592\u2592\u2592\u2592<white>\u2503"
        6 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588<dark_gray>\u2592\u2592\u2592\u2592<white>\u2503"
        7 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588<dark_gray>\u2592\u2592\u2592<white>\u2503"
        8 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588<dark_gray>\u2592\u2592<white>\u2503"
        9 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588<dark_gray>\u2592<white>\u2503"
        10 -> "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2503"
        else -> ""
    }
}
