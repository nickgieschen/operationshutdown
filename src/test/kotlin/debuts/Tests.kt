package debuts

import org.junit.Test
import java.io.File
import java.util.*

class tests {
    val yahooPlayer = YahooPlayer(name="William Cuevas", first="William", last="Cuevas", team="Bos",
        key="357.p.10272", id="10272", ownership="freeagents")

    val brPlayer = BrPlayer("/players/b/barreja01.shtml", name="Jake Barrett", debut="2016-04-04", team="ARI")

    fun parsesBrForPlayers() {
        val f = File("testfile.html")
        //val players = grabDebuts { Jsoup.parse(f, "UTF-8") }
        //assertEquals(227, players.size)
    }

    //@Test
    fun debutsFiltersOutOnStash(){

    }

    //@Test
    fun debutsFiltersOutProcessed(){
    }

    //@Test
    fun addsToFile(){
        addToFile(processedDebutsPath, Entry(Date(), brPlayer, yahooPlayer))
    }

    //@Test
    fun removesFromFile(){
        removeFromFile(processedDebutsPath, Entry(Date(), brPlayer, yahooPlayer))
    }

    fun dropsPlayersFromStash(){
        addPlayersToStash()
        //println(getFromYahoo(brPlayer))
    }
}