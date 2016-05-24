package debuts

import org.jsoup.Jsoup
import yahooFacade.*

import com.github.scribejava.core.model.Verb;
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter
import com.sun.jersey.core.util.MultivaluedMapImpl
import org.apache.commons.cli.*
import org.jdom2.input.SAXBuilder
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.*
import javax.ws.rs.core.MediaType

val baseUrl = "http://fantasysports.yahooapis.com/fantasy/v2"
val leagueId = "357.l.25371"
val leagueUrl = "$baseUrl/league/$leagueId"
val stashKey = "$leagueId.t.19"
var cmd = ""
val logger = Logger.getLogger("debuts")
var logFileName: String = ""
val mailApiKey = "key-3ea1820206278de2b23796653ae96116"
var testing = true
val data = Data()
val teamAbbrMap = mapOf(
        Pair("ARI", "Ari"),
        Pair("ATL", "Atl"),
        Pair("BAL", "Bal"),
        Pair("BOS", "Bos"),
        Pair("CHC", "ChC"),
        Pair("CHW", "CWS"),
        Pair("CIN", "Cin"),
        Pair("CLE", "Cle"),
        Pair("COL", "Col"),
        Pair("DET", "Det"),
        Pair("HOU", "Hou"),
        Pair("KCR", "KC"),
        Pair("LAA", "LAA"),
        Pair("LAD", "LAD"),
        Pair("MIA", "Mia"),
        Pair("MIL", "Mil"),
        Pair("MIN", "Min"),
        Pair("NYM", "NYM"),
        Pair("NYY", "NYY"),
        Pair("OAK", "Oak"),
        Pair("PHI", "Phi"),
        Pair("PIT", "Pit"),
        Pair("SDP", "SD"),
        Pair("SEA", "Sea"),
        Pair("SFG", "SF"),
        Pair("STL", "StL"),
        Pair("TBR", "TB"),
        Pair("TEX", "Tex"),
        Pair("TOR", "Tor"),
        Pair("WSN", "Was")
)


data class BrPlayer(val id: String, val name: String, val debut: String, val team: String)

data class YahooPlayer(val name: String, val first: String, val last: String, val team: String,
                       val key: String, val id: String, val ownership: String)

//TODO: this
data class Entry(var timestamp: Date?, val brPlayer: BrPlayer, val yahooPlayer: YahooPlayer?) {
    override fun equals(other: Any?): Boolean {
        return (other as Entry).brPlayer.id == brPlayer.id
    }

    override fun hashCode(): Int {
        var result = timestamp?.hashCode() ?: 0
        result = 31 * result + brPlayer.hashCode()
        result = 31 * result + (yahooPlayer?.hashCode() ?: 0)
        return result
    }
}

enum class PlayerStatus {
    STASHED,
    PROCESSED
}

fun main(args: Array<String>) {

    println("hi")
    println(args[0])

    fun parseCommandLine(): CommandLine {
        val options = Options()
        options.addOption("t", "Run in $args test mode. No persistence to S3 or league.")
        options.addOption(Option.builder("p").hasArg().argName("PLAYER_ID").desc("The player id process").build())

        val parser = DefaultParser()
        return parser.parse(options, args)!!
    }

    fun setupLog(cmd: String) {
        logger.level = Level.ALL

        logFileName = "$cmd-${SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().time)}"
        val fh = FileHandler(logFileName)
        fh.formatter = SimpleFormatter()
        logger.addHandler(fh)

        val ch = ConsoleHandler()
        ch.formatter = SimpleFormatter()
        logger.addHandler(ch)
    }

    try {
        val cl = parseCommandLine()

        testing = cl.hasOption("t")
        val player = cl.getOptionValue("p", null)
        cmd = cl.argList.last()

        setupLog(cmd)

        logger.info("executing $cmd")
        if (testing) logger.info("TESTING")

        when (cmd) {
            "addtostash" -> addPlayersToStash(player)
            "dropfromstash" -> dropPlayersFromStash()
            "createinitialdebuts" -> createInitialDebuts()
            "refreshaccesstoken" -> refreshAccessToken()
        }
    } catch(e: Exception) {
        logger.info(e.message)
        data.persist(logFileName, File(logFileName))
        sendResults("Top level error:\n${e.toString()}")
    }
}


fun refreshAccessToken() {
    getNewToken()
}

fun finalize(message: String) {
    if (!testing) {
        sendResults(message)
        data.persist(logFileName, File(logFileName))
    }
}

fun sendResults(message: String) {
    try {
        val client = Client.create()
        client.addFilter(HTTPBasicAuthFilter("api", mailApiKey))
        val webResource = client.resource("https://api.mailgun.net/v3/sandbox3399bf9e4d004e239afcc5e0e0b1c336.mailgun.org/messages")
        val formData = MultivaluedMapImpl()
        formData.add("from", "Mailgun Sandbox <postmaster@sandbox3399bf9e4d004e239afcc5e0e0b1c336.mailgun.org>")
        formData.add("to", "nickgieschen@gmail.com")
        formData.add("subject", "debuts $cmd - $cmd-${SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().time)}")
        formData.add("text", message)
        webResource.type(MediaType.APPLICATION_FORM_URLENCODED).post(ClientResponse::class.java, formData)
    } catch (e: Exception) {
        log("Couldn't send results", null, e)
    }
}

fun log(message: String, player: Any?, e: Exception? = null){
    val logMessage = mutableListOf<String>()
    logMessage.add(message)
    logMessage.add("----------------------------------------")
    logMessage.add(player.toString())
    log(logMessage.reduce { acc, line -> acc + "\n" + line }, e)
}

fun log(message: String, e: Exception? = null) {
    val logMessage = mutableListOf<String>()
    logMessage.add("\n")
    logMessage.add("----------------------------------------")
    logMessage.add(message)
    logMessage.add("----------------------------------------")
    if (e != null) {
        logMessage.add(e.toString())
    }
    logger.info(logMessage.reduce { acc, line -> acc + "\n" + line })
}

/*
asdfsadfsdaf
asdfsadfsdaf
asdfsadfsdaf
 */
fun grabDebuts(): List<BrPlayer> {
    return Jsoup.connect("http://www.baseball-reference.com/leagues/MLB/2016-debuts.shtml").get().select("#misc_bio tbody tr").map {
        val tds = it.select("td")
        BrPlayer(tds[1].select("a").attr("href"), tds[1].text(), tds[4].attr("csk"), tds[7].text())
    }
}

/**
 * Doc string thing
 */
fun getUnprocessedDebuts(): List<BrPlayer> {
    val allDebuts = grabDebuts()
    val someNum = 0
    val processedDebuts = data.read(PlayerStatus.PROCESSED)
    val onStash = data.read(PlayerStatus.STASHED)
    return allDebuts.filterNot { fromAll ->
        processedDebuts.any { fromProcessed -> fromProcessed.brPlayer == fromAll }
                || onStash.any { fromStash -> fromStash.brPlayer == fromAll }
    }
}

// Entry point
fun addPlayersToStash(player: String?) {

    val ambiguousNames = mutableMapOf<BrPlayer, List<YahooPlayer>>()
    val unmatchedPlayers = mutableListOf<BrPlayer>()
    val matchedPlayers = mutableListOf<Entry>()
    val errorPlayers = mutableListOf<Entry>()
    val stashedPlayers = mutableListOf<Entry>()

    var unprocessedDebuts = getUnprocessedDebuts()

    // We're only processing the player passed in
    if (player != null) {
        unprocessedDebuts = unprocessedDebuts.filter { it.id == player }
    }

    unprocessedDebuts.forEach { player ->
        val matchesFromYahoo = getFromYahoo(player)

        if (matchesFromYahoo.count() > 1) {
            ambiguousNames.put(player, matchesFromYahoo)
        } else if (matchesFromYahoo.count() == 0) {
            unmatchedPlayers.add(player)
        } else {
            matchedPlayers.add(Entry(null, player, matchesFromYahoo[0]))
        }
    }

    matchedPlayers.forEach {
        val yahooPlayer = it.yahooPlayer!!
        if (yahooPlayer.ownership == "waivers") {
            log("Waivered player", it)
        } else if (yahooPlayer.ownership == "freeagents") {
            var tries = 0
            while (tries < 3) {
                try {
                    tries++
                    addPlayerToStash(it)
                    it.timestamp = Date()
                    data.append(PlayerStatus.STASHED, it)
                    stashedPlayers.add(it)
                    break;
                } catch (e: Exception) {
                    if (tries == 3) {
                        errorPlayers.add(it)
                        log("Couldn't add to stash", it, e)
                    }
                }
            }
        } else {
            log("Ownership problem", it)
        }
    }

    // We consider these players processed since when Yahoo adds them they will already have debuted and hence
    // their waiver period will be appropriately after the fact that they've debuted
    unmatchedPlayers.forEach {
        data.append(PlayerStatus.PROCESSED, Entry(null, it, null))
    }

    // TODO
    //${ambiguousNames.map { entry -> entry.key.toString() + entry.value.fold("") { acc, yahoo -> "\n${yahoo.toString()}" } }.reduce { acc, item -> acc + item + "\n" }}
    val msg = """The following players were added to stash:
        ${stashedPlayers.fold("") { acc, entry -> acc + toJson(entry) + "\n" }}

        The following players had no matches and were added to processed:
        ${unmatchedPlayers.fold("") { acc, entry -> acc + toJson(Entry(null, entry, null)) + "\n" }}

        The following players had multiple matches:

        There were errors processing the following players:
        ${errorPlayers.fold("") { acc, entry -> acc + toJson(entry) + "\n" }}
    """

    if (player == null) {
        //finalize(msg)
    } else {
        print(msg)
    }
}

fun toJson(entry: Entry): String? {
    return mapper.writeValueAsString(entry)
}

fun dropPlayersFromStash() {

    val players = data.read(PlayerStatus.STASHED).filter { it.timestamp!!.before(aDayAgo()) }
    val droppedFromStashPlayers = mutableListOf<Entry>()
    val errorPlayers = mutableListOf<Entry>()

    players.forEach {
        var tries = 0
        while (tries < 3) {
            try {
                tries++
                dropPlayerFromStash(it)
                data.delete(PlayerStatus.STASHED, it)
                data.append(PlayerStatus.PROCESSED, it)
                droppedFromStashPlayers.add(it)
                break
            } catch (e: Exception) {
                if (tries == 3) {
                    errorPlayers.add(it)
                    log("Couldn't drop player from stash", it, e)
                }
            }
        }
    }

    val msg = """The following players were dropped from stash:
        ${droppedFromStashPlayers.fold("") { acc, entry -> acc + toJson(entry) + "\n" }}

        There were errors processing the following players:
        ${errorPlayers.fold("") { acc, entry -> acc + toJson(entry) + "\n" }}
    """

    finalize(msg)
}

fun aDayAgo(): Date {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DATE, -1)
    return cal.time!!
}

fun addPlayerToStash(player: Entry) {
    if (!testing) {
        val addPayload = """<fantasy_content>
      <transaction>
        <type>add</type>
        <player>
          <player_key>${player.yahooPlayer!!.key}</player_key>
          <transaction_data>
            <type>add</type>
            <destination_team_key>$stashKey</destination_team_key>
          </transaction_data>
        </player>
      </transaction>
    </fantasy_content>"""
        sendRequest(Verb.POST, "$leagueUrl/transactions?format=xml", addPayload)
    }
}

fun dropPlayerFromStash(player: Entry) {
    if (!testing) {
        val dropPayload = """<fantasy_content>
      <transaction>
        <type>drop</type>
        <player>
          <player_key>${player.yahooPlayer!!.key}</player_key>
          <transaction_data>
            <type>drop</type>
            <source_team_key>$stashKey</source_team_key>
          </transaction_data>
        </player>
      </transaction>
    </fantasy_content>"""
        sendRequest(Verb.POST, "$leagueUrl/transactions?format=xml", dropPayload)
    }
}

fun getFromYahoo(player: BrPlayer): List<YahooPlayer> {
    val lastPartOfName = player.name.substringAfterLast(" ")
    val response = sendRequest(Verb.GET, "$leagueUrl/players;search=$lastPartOfName/ownership")
    val sax = SAXBuilder()
    val doc = sax.build(StringReader(response))
    val ns = doc.rootElement.namespace
    val p = doc.rootElement.getChild("league", ns).getChild("players", ns).children.map {
        //print(XMLOutputter().outputString(it))
        YahooPlayer(it.getChild("name", ns).getChildText("full", ns),
                it.getChild("name", ns).getChildText("ascii_first", ns),
                it.getChild("name", ns).getChildText("ascii_last", ns),
                it.getChildText("editorial_team_abbr", ns),
                it.getChildText("player_key", ns),
                it.getChildText("player_id", ns),
                it.getChild("ownership", ns).getChildText("ownership_type", ns))
    }
    return p.filter {
        it.last.endsWith(lastPartOfName) && it.team == teamAbbrMap[player.team]
    }
}

fun createInitialDebuts() {
    val allDebuts = grabDebuts()
    val abiguousNames = mutableMapOf<BrPlayer, List<YahooPlayer>>()
    val unmatchedPlayers = mutableListOf<BrPlayer>()
    val matchedPlayers = mutableListOf<Entry>()
    allDebuts.forEach { player ->
        val matchesFromYahoo = getFromYahoo(player)

        if (matchesFromYahoo.count() > 1) {
            abiguousNames.put(player, matchesFromYahoo)
        } else if (matchesFromYahoo.count() == 0) {
            unmatchedPlayers.add(player)
        } else {
            matchedPlayers.add(Entry(null, player, matchesFromYahoo[0]))
        }
    }

    data.persist(PlayerStatus.PROCESSED, matchedPlayers)
}

/*
fun readFileOfYahooPlayers(path: Path): List<YahooPlayer> {
    return Files.readAllLines(path).map {
        mapper.readValue<YahooPlayer>(it, YahooPlayer::class.java)
    }
}

fun addUndebutedPlayersToUndebutedList(undebutedPlayersProvider: () -> List<YahooPlayer> = { getNewUndebutedPlayersFromYahoo() }) {
    val prevListPath = datedPath(undebutedPlayersPath)
    val undebutedPlayers = undebutedPlayersProvider()
    Files.copy(undebutedPlayersPath, prevListPath)
    Files.write(undebutedPlayersPath, undebutedPlayers.map {
        mapper.writeValueAsString(it)
    }, StandardOpenOption.APPEND)
}

fun getNewUndebutedPlayersFromYahoo(addedPlayersProvider: () -> List<YahooPlayer> = { getAddedPlayersFromYahoo() }, gamesPlayedProvider: (YahooPlayer) -> Int? = ::getCareerGamesPlayed): List<YahooPlayer> {
    val errors = mutableListOf<YahooPlayer>()
    return addedPlayersProvider().filter {
        val g = gamesPlayedProvider(it)
        if (g is Int) {
            g == 0
        } else {
            errors.add(it)
            false
        }
    }
}

fun getAddedPlayersFromYahoo(allPlayersProvider: () -> List<YahooPlayer> = ::getAllPlayers,
                             oldAllPlayersProvider: () -> List<YahooPlayer> = { readFileOfYahooPlayers(allPlayersPath) }): List<YahooPlayer> {
    val allPlayers = allPlayersProvider()
    val oldAllPlayers = oldAllPlayersProvider()
    val newPlayers = allPlayers.filterNot { oldAllPlayers.contains(it) }
    return newPlayers
}

fun getAllPlayers(): List<YahooPlayer> {
    var start = 0
    val players = mutableListOf<YahooPlayer>()
    //    do {
    //        val response = sendRequest(Verb.GET, "$leagueUrl/players;status=A;start=$start")
    //        val sax = SAXBuilder()
    //        val doc = sax.build(StringReader(response))
    //        val ns = doc.rootElement.namespace
    //        val p = doc.rootElement.getChild("league", ns).getChild("players", ns).children.map {
    //            YahooPlayer(it.getChild("name", ns).getChildText("full", ns),  it.getChildText("player_key", ns), it.getChildText("player_id", ns))
    //        }
    //        players.addAll(p)
    //        start += 25
    //    } while (p.count() > 0)
    return players
}

fun writeAllPlayers() {
    var players = getAllPlayers().map {
        mapper.writeValueAsString(it)
    }
    Files.write(allPlayersPath, players)
}

fun getPlayer(key: String): YahooPlayer {
    val response = sendRequest(Verb.GET, "$playerUrl/$key")
    val doc = builder.parse(InputSource(StringReader(response)));
    val name = doc.documentElement.getElementsByTagName("full").item(0).textContent
    val id = doc.documentElement.getElementsByTagName("player_id").item(0).textContent
    return YahooPlayer(name, "", "", "", key, id)
}

fun getPlayerStatsPage(key: String): Document {
    val url = "http://sports.yahoo.com/mlb/players/$key"
    return Jsoup.connect(url).get()!!
}

fun getAllPlayersFromFile(): List<YahooPlayer> {
    return Files.readAllLines(allPlayersPath).map {
        mapper.readValue<YahooPlayer>(it, YahooPlayer::class.java)
    }
}

fun getAllPlayers0GamesPlayed(runErrors: Boolean = false) {

    fun createFileIfItDoesntExist(path: Path) {
        Files.createFile(path)
    }

    val gamesPlayersPath = path("gamesPlayers")
    val errorsPath = path("errors")

    // First run so reset everything
    if (!runErrors) {
        Files.delete(undebutedPlayersPath)
        Files.delete(gamesPlayersPath)
        Files.delete(errorsPath)
    }

    createFileIfItDoesntExist(undebutedPlayersPath)
    createFileIfItDoesntExist(gamesPlayersPath)
    createFileIfItDoesntExist(errorsPath)

    val players = if (runErrors) {
        Files.readAllLines(errorsPath).map {
            mapper.readValue<YahooPlayer>(it, YahooPlayer::class.java)
        }.toMutableList()
    } else {
        getAllPlayersFromFile()
    }

    val newErrors = mutableListOf<YahooPlayer>()
    val noGamesPlayers = mutableListOf<YahooPlayer>()
    val gamesPlayers = mutableListOf<YahooPlayer>()

    players.forEach {
        val g = getCareerGamesPlayed(it)
        if (g is Int) {
            if (g == 0) {
                noGamesPlayers.add(it)
            } else {
                gamesPlayers.add(it)
            }
        } else {
            newErrors.add(it)
        }
    }

    Files.write(undebutedPlayersPath, noGamesPlayers.map {
        mapper.writeValueAsString(it)
    }, StandardOpenOption.APPEND)

    Files.write(gamesPlayersPath, gamesPlayers.map {
        mapper.writeValueAsString(it)
    }, StandardOpenOption.APPEND)

    Files.write(errorsPath, newErrors.map {
        mapper.writeValueAsString(it)
    })
}

fun getCareerGamesPlayed(player: YahooPlayer): Int? {
    try {
        var g = getPlayerStatsPage(player.id).select("td.mlb-stat-type-1.stat-total").text()
        if (g is String && g.isEmpty()) {
            g = getPlayerStatsPage(player.id).select("td.mlb-stat-type-103.stat-total").text()
        }
        if (g is String && !g.isEmpty()) {
            return Integer.parseInt(g)
        }
        // We're assuming this is because there isn't a line for career stats. It might be that there's an error here.
        return 0
    } catch (e: Exception) {
        println("Couldn't get player stats for $player")
        println(e)
        return null
    }
}
*/
