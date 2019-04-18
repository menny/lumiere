package net.evendanan.lumiere

import android.net.Uri

class FakeMediaProvider : MediaProvider {
    override fun blockingSearch(phrase: String): List<Media> {
        return listOf(
            Media(
                "search 1 $phrase",
                Uri.parse("https://media3.giphy.com/media/l4hLTU9vannBNUQy4/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/l4hLTU9vannBNUQy4/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/l4hLTU9vannBNUQy4/giphy.gif"),
                "l4hLTU9vannBNUQy4.gif"
            ),
            Media(
                "search 2 $phrase",
                Uri.parse("https://media1.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy.gif"),
                "3oKIPmUUz1MT9u3UA0.gif"
            )
        )
    }

    override fun blockingTrending(): List<Media> {
        return listOf(
            Media(
                "trending 1",
                Uri.parse("https://media3.giphy.com/media/ikXcqqlSNH2Mw/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/ikXcqqlSNH2Mw/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/ikXcqqlSNH2Mw/giphy.gif"),
                "ikXcqqlSNH2Mw.gif"
            ),
            Media(
                "trending 2",
                Uri.parse("https://media1.giphy.com/media/12sHg8v0G84V20/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/12sHg8v0G84V20/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/12sHg8v0G84V20/giphy.gif"),
                "ikXcqqlSNH2Mw.gif"
            )
        )
    }
}