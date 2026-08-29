package com.autopilot.driver.matcher

data class TargetPhrase(val language: String, val phrase: String)

object TargetPhrases {
    val defaults = listOf(
        TargetPhrase("English", "Accept"),
        TargetPhrase("Hindi", "स्वीकार करें"),
        TargetPhrase("Kannada", "ಸ್ವೀಕರಿಸಿ"),
        TargetPhrase("Telugu", "అంగీకరించండి"),
        TargetPhrase("Tamil", "ஏற்றுக்கொள்"),
        TargetPhrase("Bengali", "গ্রহণ করুন"),
        TargetPhrase("Marathi", "स्वीकारा"),
        TargetPhrase("Malayalam", "സ്വീകരിക്കുക"),
    )
}