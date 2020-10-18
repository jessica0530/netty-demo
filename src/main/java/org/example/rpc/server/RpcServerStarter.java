package org.example.rpc.server;

public class RpcServerStarter {
	public static void main(String[] args) throws  Exception{
		RpcServer server = new RpcServer();
		server.publish("org.example.rpc.service");
		server.start();
	}
}
