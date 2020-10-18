package org.example.rpc.consumer;

import org.example.rpc.service.SomeService;

public class RpcConsumer {
	public static void main(String[] args) {
		SomeService service = RpcProxy.create(SomeService.class);
		System.out.println("service = " + service.hello("xxx"));;
	}
}
