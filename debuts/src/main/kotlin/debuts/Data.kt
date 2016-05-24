package debuts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import java.io.BufferedReader
import java.io.InputStreamReader
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.PutObjectRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

val mapper = jacksonObjectMapper()
val awsBucketName = "operationshutdown-debuts"
val awsCredentials = BasicAWSCredentials(System.getenv("AWS_ACCESS_KEY_ID"), System.getenv("AWS_SECRET_ACCESS_KEY"))
//val awsCredentials = BasicAWSCredentials("AKIAIEGEWS7XSW5GKKVA", "XNwk+CUif/dWK0lL+vADcTsjzCc+iyPzMYcexRiQ ")
val s3Client = AmazonS3Client(awsCredentials)

class Data {
    fun read(objName: PlayerStatus): MutableList<Entry> {
        println("Reading ${objName.name} from S3")

        val obj = s3Client.getObject(GetObjectRequest(awsBucketName, objName.name))
        val reader = BufferedReader(InputStreamReader(obj.objectContent));
        val players = mutableListOf<Entry>()

        reader.forEachLine {
            players.add(mapper.readValue<Entry>(it, Entry::class.java))
        }
        obj.close()
        return players
    }

    fun append(playerStatus: PlayerStatus, entry: Entry) {
        val players = read(playerStatus)
        if (!players.contains(entry)) {
            val new = players.toMutableList()
            new.add(entry)
            persist(playerStatus, new)
        }
    }

    // This deletes players
    fun delete(playerStatus: PlayerStatus, entry: Entry) {
        val players = read(playerStatus)
        val new = players.toMutableList()
        if (new.remove(entry)) {
            persist(playerStatus, new)
        }
    }

    fun persist(playerStatus: PlayerStatus, data: List<Entry>) {
        val path = Paths.get(playerStatus.name)

        Files.write(path, data.map {
            mapper.writeValueAsString(it)
        })

        persist(playerStatus.name, path.toFile())
    }

    fun persist(awsObjectName: String, file: File) {
        try {
            s3Client.putObject(PutObjectRequest(awsBucketName, awsObjectName, file))
        }
        catch (e: Exception){
            log("Couldn't persist.", null, e)
        }
    }
}

