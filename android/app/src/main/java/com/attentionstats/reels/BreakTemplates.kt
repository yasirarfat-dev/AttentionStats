package com.attentionstats.reels

/** Finite, locally rotated invitations. No history, wellness claims, guilt or engagement rewards. */
internal object BreakTemplates {
    data class Template(val prompt: String, val visual: Int)
    val all = listOf(
        Template("If it feels comfortable, take one slow breath.", 0),
        Template("Let your shoulders settle. The next video can wait.", 1),
        Template("Look away from the screen for a moment.", 2),
        Template("You can put your phone down right here.", 3),
        Template("Breathe at your own pace. There is no target to reach.", 0),
        Template("Unclench your hands, if that feels comfortable.", 1),
        Template("Notice something in the room beyond your phone.", 2),
        Template("A pause can be as simple as setting the phone aside.", 3),
        Template("One easy breath. Then choose what comes next.", 0),
        Template("Would a gentle stretch feel good right now?", 1),
        Template("Let your eyes rest on something farther away.", 2),
        Template("You do not need to finish the feed.", 3),
        Template("There is no hurry. Take a breath if you would like.", 0),
        Template("Shift into a more comfortable position.", 1),
        Template("Notice the light around you for a moment.", 2),
        Template("Your place in the day matters more than a place in the feed.", 3),
        Template("Let your next breath happen without rushing it.", 0),
        Template("Rest your hands in your lap for a moment.", 1),
        Template("Is there a sound nearby you had not noticed?", 2),
        Template("A glass of water is an option. So is a quiet minute.", 3),
        Template("Try a quiet breath, or simply sit still.", 0),
        Template("Relax your jaw if you notice it feels tense.", 1),
        Template("Turn your gaze toward a window, if one is nearby.", 2),
        Template("You can leave now and decide about more videos later.", 3),
        Template("This moment does not need another task. Just pause.", 0),
        Template("Would standing up for a moment feel comfortable?", 1),
        Template("Notice one color in the space around you.", 2),
        Template("A short walk is there if you want it.", 3),
        Template("Rest for a breath. Nothing to count or get right.", 0),
        Template("Let the phone rest on a nearby surface.", 1),
        Template("Give the screen a moment without your gaze.", 2),
        Template("You are free to stop, even in the middle of something.", 3),
        Template("Keep your breathing easy and comfortable.", 0),
        Template("Notice how you are sitting. Adjust if you want to.", 1),
        Template("Look up. What is one thing you can see around you?", 2),
        Template("Choose a little space before the next thing.", 3),
    )
}
