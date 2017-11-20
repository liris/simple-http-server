import java.nio.file.Paths
import java.nio.file.Files
import netscape.security.Privilege.FORBIDDEN
import java.nio.file.Path

private val mimeTypes = mapOf(
    "html" to "text/html",
        "jpeg" to "image/jpeg"
)

private fun getMimeType(path: Path): String {
    val splited = path.fileName.toString().split(".")
    val ext = splited[splited.size -1]
    return mimeTypes[ext] ?: "application/octet-stream"

}

fun requestHandler(request: Request): Response {

    val resourcePath = Paths.get("public", request.path).normalize();
    if (!resourcePath.startsWith("public/")) { // ディレクトリトラバーサル
        return Response("403 Forbbiden", "text/plain", "Forbidden".toByteArray())
    }
    if (Files.isRegularFile(resourcePath)) {
        return Response("200 OK", getMimeType(resourcePath), Files.readAllBytes(resourcePath))
    }
    val indexFilePath = resourcePath.resolve("index.html");
    if (Files.isDirectory(resourcePath) && Files.exists(indexFilePath)) {
        return Response("200 OK", "text/html", Files.readAllBytes(indexFilePath))
    }

    return Response("404 Not Found", "text/plain", "Not Found".toByteArray())
}