package com.vyulabs.update.common.process

import org.slf4j.Logger

import java.io.BufferedReader

class LinesReaderThread(input: BufferedReader, lineWaitingTimeoutMs: Option[Int],
                        onLine: (String, Boolean) => Unit, onEof: () => Unit, onError: (Exception) => Unit)(implicit log: Logger) extends Thread {

  override def run(): Unit = {
    val buffer = StringBuilder.newBuilder
    val chunk = new Array[Char](1024)
    try {
      var cnt = input.read(chunk)
      while (cnt != -1) {
        buffer.appendAll(chunk, 0, cnt)
        var index1 = 0
        var index2 = 0
        while (index2 < buffer.size) {
          if (buffer(index2) == '\n') {
            onLine(buffer.substring(index1, index2), true)
            index1 = index2 + 1
          }
          index2 += 1
        }
        buffer.delete(0, index1)
        if (!buffer.isEmpty) {
          for (lineWaitingTimeoutMs <- lineWaitingTimeoutMs) {
            val expire = System.currentTimeMillis() + lineWaitingTimeoutMs
            var rest = expire - System.currentTimeMillis()
            if (rest > 0) {
              do {
                Thread.sleep(if (rest > 100) 100 else rest)
                rest = expire - System.currentTimeMillis()
              } while (!input.ready() && rest > 0)
            }
            if (!input.ready()) {
              onLine(buffer.toString(), false)
              buffer.clear()
            }
          }
        }
        cnt = input.read(chunk)
      }
    } catch {
      case e: Exception =>
        onError(e)
    }
    onEof()
  }
}