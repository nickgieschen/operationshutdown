package yahooFacade

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.PutObjectRequest
import com.github.scribejava.apis.YahooApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthConstants
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import debuts.awsBucketName
import debuts.Data

//val awsCredentials = BasicAWSCredentials(System.getenv("AWS_ACCESS_KEY_ID"), System.getenv("AWS_SECRET_ACCESS_KEY"))
//val awsBucketName = "operationshutdown-debuts"

private var accessToken: AccessToken? = null
val clientID = "dj0yJmk9M2cxa2FoSTZsczhGJmQ9WVdrOU5raDZiMVpOTkcwbWNHbzlNQS0tJnM9Y29uc3VtZXJzZWNyZXQmeD1hMw--"
val clientSecret = "17c8a5f104f2fb99a25b5dd963f224af48952fe3"
val accessTokenPath = Paths.get("./access-token-$clientID")
val service = ServiceBuilder()
        .apiKey(clientID)
        .apiSecret(clientSecret)
        .build(YahooApi.instance())
val data = Data()

/**
 * @param payload Should be string xml
 */
fun sendRequest(verb: Verb, url: String, payload: String? = null): String? {

    val sendRequestFn = fun(handler401: () -> String?): String? {
        val request = OAuthRequest(verb, url, service)
        if (payload != null){
            request.addPayload(payload)
            request.addHeader("Content-type","application/xml")
        }
        service.signRequest(getAccessToken(), request)
        val response = request.send()
        if (response.code == 401) {
            return handler401()
        } else {
            if (response.isSuccessful) {
                return response.body
            } else {
                println(response.message)
                println(response.body)
                throw Exception()
            }
        }
    }

    val refresh = fun(): String? {
        println("We need to refresh the access token or get a new one")
        refreshToken() || getNewToken()
        return sendRequestFn {
            println("Could neither refresh nor get new token")
            throw Exception()
        }
    }

    return sendRequestFn(refresh)
}

fun getAccessToken(): OAuth1AccessToken? {


    // If token is in memory use it
    if (accessToken != null) {
        return accessToken
    }

    // If not in memory try to get it from disk
    if (readTokenFromS3()) {
        println("Getting access token from S3")
        return accessToken
    }

    // If we can't read the access token from disk we need to get a new one
    if (getNewToken()) {
        return accessToken
    }

    throw Exception("Shouldn't get here")
}

fun refreshToken(): Boolean {
    accessToken!!.let {
       // val request = OAuthRequest(Verb.POST, "https://api.login.yahoo.com/oauth/v2/get_token", service);
       // request.addOAuthParameter("oauth_session_handle", it.sessionHandle);
       // service.signRequest(it, request)
       // var response = request.send();

        val request = OAuthRequest(service.api.getAccessTokenVerb(), "https://api.login.yahoo.com/oauth/v2/get_token", service)
		request.addOAuthParameter(OAuthConstants.TOKEN, it.token);
		request.addOAuthParameter("oauth_session_handle", it.sessionHandle);

        request.addOAuthParameter(OAuthConstants.TIMESTAMP, service.api.getTimestampService().getTimestampInSeconds());
        request.addOAuthParameter(OAuthConstants.NONCE, service.api.getTimestampService().getNonce());
        		request.addOAuthParameter(OAuthConstants.CONSUMER_KEY, clientID);
        		request.addOAuthParameter(OAuthConstants.SIGN_METHOD, service.api.getSignatureService().getSignatureMethod());
        		//request.addOAuthParameter(OAuthConstants.VERSION, getVersion());
        		val sig = service.api.getSignatureService().getSignature(service.api.getBaseStringExtractor().extract(request), clientSecret, it.tokenSecret);
        		request.addOAuthParameter(OAuthConstants.SIGNATURE, sig);

        				request.addHeader(OAuthConstants.HEADER, service.api.getHeaderExtractor().extract(request));
        val response = request.send()
		if (response.isSuccessful) {
            AccessToken(service.api.accessTokenExtractor.extract(response.body)).let {
                sendTokenToS3(it)
                accessToken = it
            }
            return true
        } else {
            println("Couldn't refresh the token")
            println(response.message)
            println(response.body)
            return false
        }
    }
}

fun sendTokenToS3(accessToken: AccessToken) {
    Files.write(accessTokenPath, listOf(accessToken.token, accessToken.tokenSecret, accessToken.sessionHandle))
    val s3Client = data.s3Client
    s3Client.putObject(PutObjectRequest(awsBucketName, "yahooAccessToken", accessTokenPath.toFile()))
}

fun readTokenFromS3(): Boolean {
    try {
        val s3Client = data.s3Client
        val obj = s3Client.getObject(GetObjectRequest(awsBucketName, "yahooAccessToken"))
        accessToken = AccessToken(InputStreamReader(obj.objectContent).buffered().use { it.readLines() })
        return true
    } catch (e:AmazonS3Exception){
        if (e.errorCode.equals("NoSuchKey")){
            return false
        } else {
            throw e
        }
    }
}

fun getNewToken(): Boolean {
    val requestToken = service.requestToken
    println("Paste the response from this link to create a new access token:")
    println(service.getAuthorizationUrl(requestToken))
    val oAuthVerifier = Scanner(System.`in`).nextLine()
    println("Thanks! Getting auth token")
    AccessToken(service.getAccessToken(requestToken, oAuthVerifier)).let {
        sendTokenToS3(it)
        accessToken = it
    }
    return true
}

class AccessToken : OAuth1AccessToken {

    val sessionHandle: String

    constructor(accessToken: OAuth1AccessToken) : super(accessToken.token, accessToken.tokenSecret) {
        sessionHandle = accessToken.rawResponse.split("&").associate { it ->
            val parts = it.split("=")
            Pair(parts[0], parts[1])
        }["oauth_session_handle"] ?: throw Exception("Session handle could not be extracted.")
    }

    constructor(data: List<String>) : super(data[0], data[1]) {
        this.sessionHandle = data[2]
    }
}
