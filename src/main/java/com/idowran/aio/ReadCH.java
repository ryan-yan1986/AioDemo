package com.idowran.aio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 用来处理一个来自客户端的请求
 * @author Tony
 *
 */
public class ReadCH implements CompletionHandler<Integer, Integer>{
	private static Logger log = LoggerFactory.getLogger(ReadCH.class);
	
	private ByteBuffer buffer = ByteBuffer.allocate(10240);
	private AsynchronousSocketChannel asc;
	private FileOutputStream fos;
	private FileChannel fc;
	// 当前这笔数据的总字节数
	private long total;
	// 当前这笔数据已接收到的字节数
	private long received;
	
	public ReadCH(AsynchronousSocketChannel asc) {
		this.asc = asc;
	}
	
	public void completed(Integer result, Integer attachment) {
		if(result <= 0) {
			// 当客户端调用Socket通道的shutdownOutput时就会进到这里
			log.info("No more incoming data now. Quit");
			return;
		}
		
		received += result;
		log.info("Read {}/{}/{} bytes", result, received, total);
		try {
			// buffer中已填充好数据，只需取出来处理
			buffer.flip();
			// fc为空，表示这是一个新文件
			if (fc == null) {
				// 首8字节记录了要传输的字节总数(包括这8个字节)
				total = buffer.getLong();
				// 根据当前文件编号生成文件名
				InetSocketAddress isa = (InetSocketAddress) asc.getRemoteAddress();
				fos = new FileOutputStream(new File(String.format("d:/temp/%d_%d.rar", isa.getPort(), attachment)));
				// 打开文件通道，准备写入
				fc = fos.getChannel();
			}
			fc.write(buffer);	// 写入(从第9字节开始)
			buffer.clear();
			if (received < total) {
				// 没有接受我就继续read
				asc.read(buffer,attachment, this);
			}else {
				// 接收完则发回响应，一次是应发字节数、已接受成功的字节数
				buffer.putLong(total);
				buffer.putLong(received);
				buffer.flip();
				// write后等待I/O完成
				result = asc.write(buffer).get();
				log.info("Written response {} bytes", result);
				// 重设reader，准备在此通道上的下一次读取
				this.reset();
				// 读取下一个文件的数据，文件编号加1
				this.asc.read(buffer, attachment + 1, this);
			}
		} catch (Exception e) {
			log.error("Error on recevie file", e);
			this.close();
		}
	}

	public void failed(Throwable exc, Integer attachment) {
		// 遇到异常，直接关闭
		this.close();
		// 打印日志
		SocketAddress sa = null;
		try {
			sa = asc.getRemoteAddress();
		} catch (IOException e) {
			log.error("Error on getRemoteAddress", e);
		}
		log.error("Error on read from {}", sa, exc);
		this.close();
	}
	
	public ByteBuffer getBuffer() {
		return buffer;
	}

	public void reset() {
		// 关闭正在写入的文件，准备读取一个文件
		StreamUtil.close(fos);
		StreamUtil.close(fc);
		fos = null;
		fc = null;	// 重要，新文件传输开始标记
		buffer.clear();
		total = 0;
		received = 0;
	}
	
	public void close() {
		this.reset();
		StreamUtil.close(asc);
	}
}
