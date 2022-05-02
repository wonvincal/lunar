package com.lunar.service;

import org.junit.BeforeClass;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.MessageCodec;

public class RemotePingPongTest {
	@SuppressWarnings("unused")
	private static Logger LOG = LogManager.getLogger(RemotePingPongTest.class);

	static MessageCodec messageCodec;

	@BeforeClass
	public static void setup(){
		messageCodec = MessageCodec.of();
	}
	

	@SuppressWarnings("unused")
	@Test
	public void testEcho() throws InterruptedException{
		int pingSvcId = 101;
		int pongSvcId = 102;
/*		String configFile = getClass().getClassLoader().getResource("remote_application.conf").getFile();
		Config config = ConfigFactory.parseFile(new File(configFile));
		ActorSystem system = ActorSystem.create("RemoteSystem", config);
		system.actorOf(PongActor.props(pongSvcId), "remote");
		LOG.debug("remote is ready");
		String localConfigFile = getClass().getClassLoader().getResource("local_application.conf").getFile();
		Config localConfig = ConfigFactory.parseFile(new File(localConfigFile));
		ActorSystem localSystem = ActorSystem.create("LocalSystem", localConfig);
		CountDownLatch latch = new CountDownLatch(1);
		ActorRef localActor = localSystem.actorOf(PingActor.props(pingSvcId, "akka.tcp://RemoteSystem@127.0.0.1:5150/user/remote", pongSvcId, latch), "local");
		LOG.debug("local is ready");
		Frame message = messageCodec.createMessage();
		messageCodec.encodeCommandEcho(message, -1, pingSvcId, 0, 0, 10000);
		localActor.tell(message, ActorRef.noSender());
		latch.await();
*/	}

/*	
	@Test
	public void testKryoEcho() throws InterruptedException{
		String configFile = getClass().getClassLoader().getResource("kryo_remote_application.conf").getFile();
		Config config = ConfigFactory.parseFile(new File(configFile));
		ActorSystem system = ActorSystem.create("RemoteSystem", config);
		ActorRef remoteEchoActor = system.actorOf(EchoActor.props(), "remote");
		LOG.debug("remote is ready");
		Thread.sleep(1000);
		String localConfigFile = getClass().getClassLoader().getResource("kryo_local_application.conf").getFile();
		Config localConfig = ConfigFactory.parseFile(new File(localConfigFile));
		ActorSystem localSystem = ActorSystem.create("LocalSystem", localConfig);
		ActorRef localActor = localSystem.actorOf(LocalActor.props("akka.tcp://RemoteSystem@127.0.0.1:5150/user/remote"), "local");
		LOG.debug("local is ready");
		Thread.sleep(100);
		for (int i = 0; i < 5; i++){
			Message message = messageCodec.createMessage();
			messageCodec.encodeCommandEcho(message, 0, 10000);
			localActor.tell(message, ActorRef.noSender());
			Thread.sleep(4000);
		}
	}*/
}
