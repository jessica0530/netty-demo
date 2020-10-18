package org.example.rpc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RpcServer {
	/**
	 * 注册中心
	 * 注册service 的实现到registerMap
	 * key:业务接口名,value:实现类的实例
	 */

	private Map<String,Object> registerMap = new HashMap<>();
	/**
	 * 类的缓存
	 * 需要把类反射
	 * 用于缓存指定包下的业务接口实现类 类名
	 */
	//private List<String> classCache = Collections.synchronizedList(new ArrayList<String>());
	private List<String> classCache = new ArrayList<>();

	public void publish(String basePackage) throws Exception {
		getProviderClass(basePackage);
		doRegister();
	}

	private void doRegister() throws Exception {
		if(classCache.size()==0){
			return;
		}
		for(String className: classCache){
			Class<?> clazz = Class.forName(className);
			if(clazz.getInterfaces().length==1){
				registerMap.put(clazz.getInterfaces()[0].getName(),clazz.newInstance());
			}
		}
	}

	/**
	 * 把service包下的类放到
	 * @param basePackage
	 */
	private void getProviderClass(String basePackage) {
		//获取指定包目录中的资源
		URL resource = this.getClass().getClassLoader()
				//org.example.rpc.service => org/example/rpc/service
				.getResource(basePackage.replaceAll("\\.","/"));
		//若目录中没有任何资源,则直接结束
		if(resource == null){
			return;
		}
		//将URL资源转换为File
		File dir = new File(resource.getFile());

		//遍历指定包及其子孙包中的所有文件 查找.class文件
		for(File file: dir.listFiles()){
			if(file.isDirectory()){
				//递归
				getProviderClass(basePackage+"."+file.getName());
			} else if(file.getName().endsWith(".class")){
				//获取到实现类的简单类名
				String fileName = file.getName().replace(".class","").trim();
				//将实现类加到classCache中
				classCache.add(basePackage+"."+fileName);
			}
		}


	}

	public void start() throws InterruptedException {
		EventLoopGroup parentGroup = new NioEventLoopGroup();
		EventLoopGroup childGroup = new NioEventLoopGroup();

		try {
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(parentGroup, childGroup)
					//指定用于存放连接请求的队列长度
					.option(ChannelOption.SO_BACKLOG, 1024)
					//指定 启动心跳机制来检测长连接的存活性
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					.channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {

						@Override
						protected void initChannel(SocketChannel socketChannel) throws Exception {
							//接受的数据 会 被pipeline里面的处理器依次处理
							ChannelPipeline pipeline = socketChannel.pipeline();
							pipeline.addLast(new ObjectEncoder());
							pipeline.addLast(new ObjectDecoder(Integer.MAX_VALUE,
									ClassResolvers.cacheDisabled(null)));
							pipeline.addLast(new RpcServerHandler(registerMap));
						}
					});
			ChannelFuture future = bootstrap.bind(8888).sync();
			future.channel().closeFuture().sync();
		} finally {
			parentGroup.shutdownGracefully();
			childGroup.shutdownGracefully();
		}


	}

}
