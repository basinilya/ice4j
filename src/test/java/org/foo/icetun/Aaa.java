package org.foo.icetun;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.harvest.CandidateHarvester;
import org.ice4j.ice.harvest.StunCandidateHarvester;

import test.SdpUtils;

public class Aaa {

	
    public static void main(final String[] args) throws Exception {
    	/*
    	FutureTask<String> fut = new FutureTask<String>(new Callable<String>() {
    		@Override
    		public String call() throws Exception {
    			return null;
    		}
		});
    	*/
    	final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    	// https://stackoverflow.com/questions/36829060/how-to-receive-public-ip-and-port-using-stun-and-ice4j
    	Messenger messenger = new Messenger() {
    		@Override
    		public void onMessage(String msg) {
    			queue.add(msg);
    		}
    	};

    	new Thread() {
    		public void run() {
    	    	try {
    	    		for(;;) {
    	    			messenger.sendMessage("hello");
    	    			queue.poll(10, TimeUnit.SECONDS);
    	    		}
				} catch (Exception e) {
					e.printStackTrace();
				}
    		};
    	}.start();

        final Agent agent = new Agent(); // A simple ICE Agent
        /*** Setup the STUN servers: ***/
        final String[] hostnames = new String[] { "138.201.172.116" };
        for (final String hostname : hostnames) {
            final TransportAddress ta =
                new TransportAddress(InetAddress.getByName(hostname), 3478, Transport.UDP);
            final CandidateHarvester harvester = new StunCandidateHarvester(ta);
            agent.addCandidateHarvester(harvester);
        }

        final IceMediaStream stream = agent.createMediaStream("audio");
        final int port = 5000; // Choose any port
        // agent.createco
        try {
            agent.createComponent(stream, Transport.UDP, port, port, port + 100);
            // The three last arguments are: preferredPort, minPort, maxPort
        } catch (final BindException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        String toSend = null;
        try {
            toSend = SdpUtils.createSDPDescription(agent);
            // Each computersends this information
            // This information describes all the possible IP addresses and
            // ports
        } catch (final Throwable e) {
            e.printStackTrace();
        }

        System.out.println(toSend);
        System.exit(0);
    }


}
