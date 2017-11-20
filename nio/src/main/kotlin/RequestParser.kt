import java.nio.channels.SocketChannel
import java.nio.charset.Charset

class RequestParser(private val server: SimpleHttpServer, private val socketChannel: SocketChannel) {
    companion object {
        val requestLinePattern = "(?<method>.*) (?<path>.*?) (?<version>.*?)".toRegex()
    }
    private var data = ByteArray(0)
    private var requestLines = ""

    fun add(data: ByteArray) {
        this.data += data
    }

    val isFinished: Boolean
        get() {
            requestLines = String(data, Charset.forName("utf-8"))
            return requestLines.endsWith("\r\n\r\n")
        }

    fun parse(): Request {
        val matcher = requestLinePattern.matchEntire(requestLines.split("\r\n")[0]) ?: throw IllegalStateException("InvalidFormat")
        val method = matcher.groups["method"]?.value
        val path = matcher.groups["path"]?.value
        val httpVersion = matcher.groups["version"]?.value
        if (method == null || path == null || httpVersion == null) {
            throw IllegalStateException("InvlidHader")
        }

        return Request(method, path, httpVersion)
    }
}