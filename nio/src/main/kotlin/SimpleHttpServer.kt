import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue

typealias handler = (request: Request) -> Response

class SimpleHttpServer(private val port: Int = 3000) {
    private val wpool = ConcurrentLinkedDeque<SelectionKey>()
    private val selector = Selector.open()
    private val channel = ServerSocketChannel.open()

    init {
        channel.configureBlocking(false)
        val socket = channel.socket()
        val address = InetSocketAddress(port)
        socket.bind(address)
        channel.register(selector, SelectionKey.OP_ACCEPT)
    }

    fun addWriter(selectionKey: SelectionKey) {
        wpool.add(selectionKey)
        selector.wakeup()
    }

    fun run() {
        val readWorker = ReadWorker(this)
        val writeWorker = WriteWorker(::requestHandler)
        runWorkers(readWorker, writeWorker)

        while (true) {
            val selectorCount = selector.select()
            if (selectorCount > 0) {
                val keys = selector.selectedKeys()
                println("keys $keys")
                val l = keys.map { selectedKey ->
                    try {
                        processSelector(selectedKey, readWorker, writeWorker)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    selectedKey
                }
                keys.removeAll(l)
            } else {
                while (!wpool.isEmpty()) {
                    val key = wpool.poll()
                    val channel = key.channel() as SocketChannel
                    try {
                        channel.register(selector, SelectionKey.OP_WRITE, key.attachment())
                    } catch (e: Exception) {
                        try {
                            channel.finishConnect()
                            channel.socket().close()
                            channel.close()
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    private fun processSelector(selectedKey: SelectionKey, readWorker: ReadWorker, writeWorker: WriteWorker) {
        if (!selectedKey.isValid) {
            return
        }

        when {
            selectedKey.isAcceptable -> {
                println("accept $selectedKey")
                val ssChannel = selectedKey.channel() as ServerSocketChannel
                val sc = ssChannel.accept()
                sc?.let {
                    it.configureBlocking(false)

                    val parser = RequestParser(this, it)
                    selectedKey.attach(parser)
                    it.register(selector, SelectionKey.OP_READ, parser)
                }
            }
            selectedKey.isReadable -> {
                readWorker.add(selectedKey)
            }
            selectedKey.isWritable -> {
                writeWorker.add(selectedKey)
                selectedKey.cancel()
            }
        }
    }

    private fun runWorkers(readWorker: ReadWorker, writeWorker: WriteWorker) {
        val executor = Executors.newFixedThreadPool(2)
        executor.execute {
            readWorker.readInfinite()
        }
        executor.execute {
            writeWorker.writeInfinite()
        }
    }
}


class ReadWorker(private val server: SimpleHttpServer) {
    private val maxBufferSize: Int = 1024
    private val pool = SynchronousQueue<SelectionKey>()

    fun add(selectionKey: SelectionKey) {
        pool.put(selectionKey)
    }

    fun readInfinite() {
        while (true) {
            val selectionKey = pool.take()
            read(selectionKey)
        }
    }

    private fun read(key: SelectionKey) {
        println("read ")
        println(key)
        val channel = key.channel() as SocketChannel
        val parser = key.attachment() as RequestParser

        val byteArray = read(channel)
        if (!byteArray.isEmpty()) {
            parser.add(byteArray)
            if (parser.isFinished) {
                server.addWriter(key)
                key.cancel()
            } else {
                println("-------")
            }
        } else {
            key.cancel()
        }

    }

    private fun read(socketChannel: SocketChannel): ByteArray {
        val buffer = ByteBuffer.allocate(maxBufferSize)
        socketChannel.read(buffer)
        val array = buffer.array()

        return array.copyOf(buffer.position())
    }
}

class WriteWorker(private val handler: handler) {
    private val pool = SynchronousQueue<SelectionKey>()
    private val executor = Executors.newFixedThreadPool(32)

    fun add(selectionKey: SelectionKey) {
        pool.put(selectionKey)
    }

    fun writeInfinite() {
        while (true) {
            val selectionKey = pool.take()
            executor.execute { write(selectionKey) }

        }
    }

    private fun write(key: SelectionKey) {
        println("write ")
        println(key)
        val channel = key.channel() as SocketChannel
        val parser = key.attachment() as RequestParser
        val request = parser.parse()
        val response = handler(request)
        val buf = ByteBuffer.wrap(response.getOutput())
        try {
            channel.write(buf)
            channel.finishConnect()
            channel.socket().close()
            channel.close()
        } catch (e: Exception) {

        }
    }

}

fun main(args: Array<String>) {
    SimpleHttpServer().run()
}