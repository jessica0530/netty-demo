package org.example.rpc.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.example.rpc.client.RpcClientHandler;
import org.example.rpc.dto.Invocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class RpcProxy {
	//泛型方法，运行的时候检测类型,不然就是编译的时候
	public static <T> T create(final Class<?> clazz){
		return(T) Proxy.newProxyInstance(clazz.getClassLoader(),
				new Class[]{clazz},
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						//若调用的方法是Object的方法,则直接进行本地调用 比如object.hashcode
						if(Object.class.equals(method.getDeclaringClass())){
							return method.invoke(this,args);
						}
						//远程调用
						return rpcInvoke(clazz,method,args);
					}
				});
	}

	private static Object rpcInvoke(Class<?> clazz, Method method, Object[] args) throws InterruptedException {
		final RpcClientHandler rpcClientHandler = new RpcClientHandler();
		NioEventLoopGroup group =new NioEventLoopGroup();
		try {
			Bootstrap bootstrap =new Bootstrap();
			bootstrap.group(group)
					.channel(NioSocketChannel.class)
					//每次发送的数据块 尽量的大,Nagel 算法,所以会导致等,这个是有数据就发布等待
					.option(ChannelOption.TCP_NODELAY,true)
					.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel socketChannel) throws Exception {
							//接受的数据 会 被pipeline里面的处理器依次处理
							ChannelPipeline pipeline = socketChannel.pipeline();
							pipeline.addLast(new ObjectEncoder());
							pipeline.addLast(new ObjectDecoder(Integer.MAX_VALUE,
									ClassResolvers.cacheDisabled(null)));
							pipeline.addLast(rpcClientHandler);

						}
					});
		ChannelFuture future = bootstrap.connect("localhost",8888).sync();
		//创建并且初始化调用信息实例
		Invocation invocation = new Invocation();
		invocation.setClassName(clazz.getName());
		invocation.setMethodName(method.getName());
		invocation.setParamTypes(method.getParameterTypes());
		invocation.setParamValues(args);
		//将invocation发送给server
		future.channel().writeAndFlush(invocation).sync();
		future.channel().closeFuture().sync();
		} finally {
			group.shutdownGracefully();
		}
		return rpcClientHandler.getResult();

	}
}
