package com.idowran.aio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server implements CompletionHandler<AsynchronousSocketChannel, Object>{
	private static Logger log = LoggerFactory.getLogger(Server.class);
	
	private AsynchronousServerSocketChannel assc = null;
	
	public void start() throws IOException {
		// 创建服务端Socket，开始监听
		assc = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress("127.0.0.1", 9000));
		// 接收新连接，操作系统会在有新连接请求时，调用this的completed方法
		// 第二个参数，是继承了CompletionHandler接口的对象
		assc.accept(null, this);
	}
	
	public void completed(AsynchronousSocketChannel result, Object attachment) {
		// 继续受理一个连接
		assc.accept(null, this);
		// 为当前通道准备一个reader对象来完成处理，而不是用一个线程来处理
		// 此reader拥有一个buffer，并持有当前通道的引用，后面有用
		ReadCH reader = new ReadCH(result);
		// 对于新建的连接，接受的文件在命名时，编号从0开始
		result.read(reader.getBuffer(), 0, reader);
	}

	public void failed(Throwable exc, Object attachment) {
		this.close();
		log.error("Error on accept connection", exc);
	}
	
	public void close() {
		StreamUtil.close(assc);
	}
	
	public static void main(String[] args) {
		BufferedReader br = null;
		// 创建一个服务器实例
		Server server = new Server();
		try {
			server.start();
			String cmd = null;
			System.out.println("Enter 'exit' to exit");
			br = new BufferedReader(new InputStreamReader(System.in));
			while ((cmd = br.readLine()) != null) {
				// 忽略大小写
				if("exit".equalsIgnoreCase(cmd)) {
					break;
				}
			}
		} catch (Exception e) {
			log.error("Error on run server", e);
		}finally {
			StreamUtil.close(br);
			server.close();
		}
		System.out.println("bye");
	}
	
}
