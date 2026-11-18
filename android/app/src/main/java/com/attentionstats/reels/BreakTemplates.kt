package com.attentionstats.reels

/** Finite, locally rotated invitations to real life: warm, without guilt or health claims. */
internal object BreakTemplates {
    data class Template(val prompt: String, val visual: Int)
    val all = listOf(
        Template("The best part of today could be just outside.", 0),
        Template("Your feet are ready for a little adventure.", 1),
        Template("Somebody would love to hear from you right now.", 2),
        Template("Open a book. Step into a whole new world.", 3),
        Template("Go make something that wasn't there this morning.", 0),
        Template("Find a patch of sky. Stay for a moment.", 1),
        Template("Put on a favorite song and move with it.", 2),
        Template("A fresh glass of water is a lovely next step.", 3),
        Template("Your attention is yours. Spend it on purpose.", 0),
        Template("Send a hello that makes someone's day.", 1),
        Template("A blank page is full of possibilities.", 2),
        Template("Step outside. Let the day surprise you.", 3),
        Template("Your next chapter is waiting on a bookshelf.", 0),
        Template("Make a little room for something you love.", 1),
        Template("The people near you are better than any feed.", 2),
        Template("Turn a few ingredients into something delicious.", 3),
        Template("Pick up that project. One tiny step counts.", 0),
        Template("There's a whole world beyond this rectangle.", 1),
        Template("Take your curiosity for a walk.", 2),
        Template("Boredom is where your best ideas start.", 3),
        Template("Deep focus grows when the feed goes quiet.", 0),
        Template("Call someone whose voice makes you smile.", 1),
        Template("Find a window. See what the light is doing.", 2),
        Template("A small stretch can be your next adventure.", 3),
        Template("Put your hands to work on something fun.", 0),
        Template("Read a poem. Let a few words stay with you.", 1),
        Template("Go discover one new thing in your neighborhood.", 2),
        Template("Make a cup of something and savor the moment.", 3),
        Template("Your favorite hobby has room for you today.", 0),
        Template("Trade a swipe for a step. See where it leads.", 1),
        Template("Write someone a note they can keep.", 2),
        Template("Listen to a whole song with your eyes closed.", 3),
        Template("Try a new word in a language you love.", 0),
        Template("Ten minutes of walking can lift your mood.", 1),
        Template("Look up. Your day still has possibilities.", 2),
        Template("Choose a little adventure you can touch.", 3),
    )
}
