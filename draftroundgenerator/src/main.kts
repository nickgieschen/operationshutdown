import java.io.File
import java.util.*

class Player(var name: String, var team: String, var pos: String)

fun unSpanishify(name: String): String {
    return name.replace("í", "i")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("é", "e")
            .replace("á", "a")
            .replace("ú", "u")
            .replace("ñ", "n")
}

var previousYearDraft = File("previousyeardraft.tsv").readLines().drop(1).map {
    it.split('\t').map {
        unSpanishify(it.substringAfter(":").trim()) // remove "some team" portion of "some team: player name"
    }
}

var yearendrosters = File("yearendrosters").readLines().filterNot { it.isBlank() }
var playersNotFound = arrayListOf<String>()
var ambiguousPlayers = arrayListOf<String>()
var draftRoundsByTeam = hashMapOf<String, ArrayList<Pair<Int, Player>>>()

fun isTeam(line: String) = line.startsWith("T:")

fun findPlayerInPreviousYearsDraft(player: Player): ArrayList<Pair<Int, Player>> {

    val playerNameUnspanishified = unSpanishify(player.name)

    val foundPlayers = arrayListOf<Pair<Int, Player>>()

    previousYearDraft.forEachIndexed { draftRound, playersFromDraft ->
        playersFromDraft
                .filter { playerNameUnspanishified == it }
                .forEach { foundPlayers.add(Pair(draftRound, player)) }
    }
    return foundPlayers
}

var currentTeam: ArrayList<Pair<Int, Player>> = arrayListOf()

for (line in yearendrosters) {
    if (isTeam(line)) {
        currentTeam = arrayListOf()
        draftRoundsByTeam[line.substringAfter(":").trim()] = currentTeam
    } else {
        val playerNameAndTeam = line.substringBeforeLast("-").trim()
        val playerName = playerNameAndTeam.substringBeforeLast(" ").trim()
        val playerTeam = playerNameAndTeam.substringAfterLast(" ").trim()
        val playerPos = line.substringAfterLast("-").trim()
        val player = Player(playerName, playerTeam, playerPos)
        val result = findPlayerInPreviousYearsDraft(player)
        when {
            result.size == 0 -> {
                currentTeam.add(Pair(25, player))
                playersNotFound.add(line)
            }
            result.size == 1 -> currentTeam.add(result[0])
            else -> ambiguousPlayers.add(line)
        }
    }
}

var output = StringBuilder()
draftRoundsByTeam.forEach {
    output.append(it.key + "\n")
    it.value.forEach {
        output.append("${it.second.name}\t${it.second.team}\t${it.second.pos}\t${it.first}\n")
    }
}

print(output.toString())

//println("Ambiguous Players")
//ambiguousPlayers.forEach { println(it) }
//
//println()
//println("Players Not Found")
//playersNotFound.forEach { println(it) }
