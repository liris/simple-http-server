import java.nio.channels.SocketChannel
import java.nio.charset.Charset
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

const val CRLF = "\r\n"
val rfc1123Formatter = DateTimeFormatter.RFC_1123_DATE_TIME

class Response(private val status: String, private var contentType: String = "text/html",
               private val content: ByteArray = ByteArray(0)) {
    private var buf = ByteArray(0)

    fun getOutput(): ByteArray {
        val now = OffsetDateTime.now(ZoneOffset.UTC);
        val header =
                "HTTP/1.1 $status" + CRLF +
                "Date: ${rfc1123Formatter.format(now)}" + CRLF +
                "Server: SimpleJavaHttpServer" + CRLF +
                "Content-Type: ${contentType}" + CRLF +
                "Content-Length: ${content.size}" + CRLF +
                "Connection: Close" + CRLF +
                CRLF

        val buf = header.toByteArray()
        return buf + content
    }
}