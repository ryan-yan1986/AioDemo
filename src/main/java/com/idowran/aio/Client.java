package com.idowran.aio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client implements CompletionHandler<Integer, Integer>{
	private static Logger log = LoggerFactory.getLogger(Client.class);
	// 异步Socket通道
	private AsynchronousSocketChannel asc;
	// 读写公用的缓冲区
	private ByteBuffer buffer = ByteBuffer.allocate(10240);
	// 当前要读取的文件输入流
	private FileInputStream fis;
	// 当前要读取的文件通道
	private FileChannel fc;
	// 待发送文件的最大编号
	private int maxFileNo = 2;
	
	public void start() throws IOException, InterruptedException, ExecutionException {
		// 创建Socket通道
		asc = AsynchronousSocketChannel.open();
		// 连接服务端，并持有Futrue对象
		Future<Void> ft = asc.connect(new InetSocketAddress("127.0.0.1", 9000));
		ft.get();
		// 从等0个文件开始发送，这里也可以将文件集合的iterator作为参数
		this.send(0);
	}
	
	public void send(Integer i) throws IOException {
		// 此方法会反复调用，发送新文件前，需要先确保上一个文件已被关闭
		StreamUtil.close(fc);
		StreamUtil.close(fis);
		
		// 读取当前文件，参数i为当前要读取的文件编号
		String fn = String.format("d:/temp/s%d.rar", i);
		fis = new FileInputStream(new File(fn));
		fc = fis.getChannel();
		
		// 首次读取时，读取文件大小，并作为前8字节发送
		buffer.clear();
		// 要发送的字节数为文本大小+文件长度变量(Long型)所占的8字节
		buffer.putLong(fc.size() + 8);
		// 将文件内容读取到buffer，一次读不完，就等这次发送出去后继续读取和发送
		fc.read(buffer);
		// 将buffer的limit设置为当前position(有效字节数)，position设置为0
		buffer.flip();
		// 异步发送，注意观察日志中的线程号
		log.info("Write first buffer on file {}", fn);
		// 将文件号作为附件，这里也可以将文件集合的iterator作为附件
		asc.write(buffer, i, this);
	}
	
	public void completed(Integer result, Integer attachment) {
		// TODO Auto-generated method stub
		if(result <= 0) {
			log.info("No written data now. Quit");
			return;
		}
		// 注意观察日志中的线程号
		log.info("Written {} bytes", result);
		
		try {
			// 上次发送可能没发完，需要继续发送
			if (buffer.hasRemaining()) {
				asc.write(buffer, attachment, this);
				return;
			}
			// 也可以不发送剩余的数据，在compact后继续使用
//			buffer.compact();
			buffer.clear();
			// 将文件内容读取到缓冲区
			if(fc.read(buffer) > 0) {
				// 将buffer翻转，写入Socket通道，发送出去
				buffer.flip();
				asc.write(buffer, attachment, this);
			}else {
				// 读不到则说明已经遇到文件尾，表示发送完成，这是可以读取服务端的响应了
				result = asc.read(buffer).get();
				log.info("Read response {} bytes", result);
				// 读取服务端的响应，依次是应发字节总数、已接收成功的字节数
				buffer.flip();
				long total = buffer.getLong();
				long received = buffer.getLong();
				System.out.println(String.format("%d %d", total, received));
				// 发送下一个文件，这里可以将文件集合的iterator作为附件
				if (attachment < maxFileNo) {
					this.send(attachment + 1);
				}else {
					// 关闭输出流，告诉服务端，数据已发送完成
					asc.shutdownInput();
					// 通知主线程继续执行(退出)
					synchronized (this) {
						this.notify();
					}
				}
			}
		} catch (Exception e) {
			log.error("Error on send file", e);
			this.close();
		}
	}

	public void failed(Throwable exc, Integer attachment) {
		// TODO Auto-generated method stub
		// 遇到异常，直接关闭
		this.close();
		// 打印日志
		SocketAddress sa = null;
		try{
			sa = asc.getRemoteAddress();
		}catch (IOException e) {
			log.error("Error on getRemoteAddress", e);
		}
		log.error("Error on read from {}", sa, exc);
	}
	
	public void close() {
		StreamUtil.close(asc);
		StreamUtil.close(fis);
		StreamUtil.close(fc);
	}
	
	public static void main(String[] args) {
		BufferedReader br = null;
		Client client = new Client();
		try {
			client.start();
			// 堵塞主线程，等待client完成传输并唤醒自己
			synchronized (client) {
				client.wait();
			}
		} catch (Exception e) {
			log.error("Error on run client", e);
		}finally {
			StreamUtil.close(br);
			client.close();
		}
		System.out.println("bye");
	}

}
