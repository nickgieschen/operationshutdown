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

var inflation = hashMapOf(
        1 to 1,
        2 to 2,
        3 to 2,
        4 to 3,
        5 to 3,
        6 to 4,
        7 to 5,
        8 to 5,
        9 to 6,
        10 to 7,
        11 to 7,
        12 to 8,
        13 to 9,
        14 to 10,
        15 to 11,
        16 to 12,
        17 to 13,
        18 to 14,
        19 to 15,
        20 to 16,
        21 to 20,
        22 to 20,
        23 to 20,
        24 to 20,
        25 to 20
)

fun isTeam(line: String) = line.startsWith("T:")

fun findPlayerInPreviousYearsDraft(player: Player): ArrayList<Pair<Int, Player>> {

    val playerNameUnspanishified = unSpanishify(player.name)

    val foundPlayers = arrayListOf<Pair<Int, Player>>()

    previousYearDraft.forEachIndexed { draftRound, playersFromDraft ->
        playersFromDraft
                .filter {
//                    if (it.startsWith("Yasm") && playerNameUnspanishified.startsWith("Yasm")){
//                        println(player.name)
//                        println(playerNameUnspanishified)
//                        println(it)
//                    }
                    playerNameUnspanishified == it
                }
                .forEach { foundPlayers.add(Pair(inflation[draftRound + 1]!!, player)) }
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
    it.value.sortedWith(compareBy({it.first})).forEach {
        output.append("${it.second.name}\t${it.second.team}\t${it.second.pos}\t${it.first}\n")
    }
}

//print(output.toString())

//println("Ambiguous Players")
//ambiguousPlayers.forEach { println(it) }
//
println()
println("Players Not Found")
playersNotFound.forEach {
    println(it)
}
val playersNotFoundPossibleMatches = hashMapOf<String, ArrayList<String>>()
playersNotFound.forEach {
    val notFound = unSpanishify(it)
    playersNotFoundPossibleMatches[notFound] = arrayListOf()
    previousYearDraft.forEach {
        it.forEach {
            if (notFound.substring(0, 3) == it.substring(0, 3)){
                playersNotFoundPossibleMatches[notFound]!!.add(it)
            }
        }
    }
}

//playersNotFoundPossibleMatches.forEach {
//    println(it.key)
//    if (it.value.isNotEmpty()){
//        it.value.forEach {
//            println(it)
//        }
//    }
//    println("")
//}
