package org.example.rpc.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.example.rpc.dto.Invocation;

import java.util.Map;

public class RpcServerHandler extends SimpleChannelInboundHandler<Invocation> {
	private Map<String, Object> registerMap;

	public RpcServerHandler(Map<String, Object> registerMap){
		this.registerMap = registerMap;
	}


	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Invocation msg) throws Exception {
        //从调用信息 找到接口 从 registerMap找到具体实例,再通过方法 调用 具体方法
		Object result = "没有指定提供者方法";
		if(registerMap.containsKey(msg.getClassName())){
			Object provider = registerMap.get(msg.getClassName());
			result = provider.getClass()
					.getMethod(msg.getMethodName(),msg.getParamTypes())
					.invoke(provider,msg.getParamValues());
			//将结果发送给 client
			ctx.writeAndFlush(result);
			ctx.close();
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		super.exceptionCaught(ctx, cause);
	}
}
