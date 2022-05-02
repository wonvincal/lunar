package com.lunar.service;

import org.junit.BeforeClass;

import com.lunar.message.binary.MessageCodec;

public class RemoteThroughputTest {

	static MessageCodec messageCodec;

	@BeforeClass
	public static void setup(){
		messageCodec = MessageCodec.of();
	}

	/*
	@Test
	public void testEcho() throws InterruptedException{
		String configFile = getClass().getClassLoader().getResource("remote_application.conf").getFile();
		Config config = ConfigFactory.parseFile(new File(configFile));
		ActorSystem system = ActorSystem.create("RemoteSystem", config);
		ActorRef remoteEchoActor = system.actorOf(PongActor.props(), "remote");
		LOG.debug("remote is ready");
		
		String localConfigFile = getClass().getClassLoader().getResource("local_application.conf").getFile();
		Config localConfig = ConfigFactory.parseFile(new File(localConfigFile));
		ActorSystem localSystem = ActorSystem.create("LocalSystem", localConfig);
		CountDownLatch latch = new CountDownLatch(1);
		ActorRef localActor = localSystem.actorOf(SenderActor.props("akka.tcp://RemoteSystem@127.0.0.1:5150/user/remote", latch, 5), "local");
		LOG.debug("local is ready");
		
		Frame message = messageCodec.createMessage();
		messageCodec.encodeCommandEcho(message, 0, 10);
		localActor.tell(message, ActorRef.noSender());
		latch.await();
	}
	*/
}
