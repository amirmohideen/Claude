package com.amirmohideen.foldfx

enum class Screen { HOME, DOOR, LAB }

enum class Access { FREE, PREMIUM }

/** One thing to do with the fold. [screen] is null until it's built. */
data class Experience(
    val title: String,
    val tagline: String,
    val emoji: String,
    val access: Access,
    val screen: Screen?,
)

val experiences = listOf(
    Experience(
        "Creaky Door",
        "Fold slowly for a long, loud creak. Fold fast for a quick squeak. Open it flat fast to slam it.",
        "🚪",
        Access.FREE,
        Screen.DOOR,
    ),
    Experience("Crocodile Chomp", "Hold it sideways and snap the jaws on falling fruit.", "🐊", Access.FREE, null),
    Experience("Treasure Chest", "Creak the lid open and watch the gold light spill out.", "💰", Access.PREMIUM, null),
    Experience("Flip Lighter", "Flick it open fast for the clink and a flame.", "🔥", Access.PREMIUM, null),
    Experience("Safe Cracker", "Find the hidden angles. Feel for the click.", "🔐", Access.PREMIUM, null),
)
