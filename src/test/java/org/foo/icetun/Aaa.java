package org.foo.icetun;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
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
    	final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    	// https://stackoverflow.com/questions/36829060/how-to-receive-public-ip-and-port-using-stun-and-ice4j
    	Messenger messenger = new Messenger() {
    		@Override
    		public void onMessage(String msg) {
    			try {
					queue.put(msg);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
    		}
    	};
    	messenger.start();
    	logger.info("messenger started");
    	
        final Agent agent = new Agent(); // A simple ICE Agent
        /*** Setup the STUN servers: ***/
        final String[] hostnames = new String[] { "138.201.172.116" };
        for (final String hostname : hostnames) {
            final TransportAddress ta =
                new TransportAddress(InetAddress.getByName(hostname), 3478, Transport.UDP);
            final CandidateHarvester harvester = new StunCandidateHarvester(ta);
            agent.addCandidateHarvester(harvester);
        }

    	logger.info("creating stream...");

    	final IceMediaStream stream = agent.createMediaStream("data");
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

    	logger.info("sending message...");

        String toSend = null;
        try {
            toSend = SdpUtils.createSDPDescription(agent);
            // Each computer sends this information
            // This information describes all the possible IP addresses and
            // ports
            messenger.sendMessage(toSend);
        } catch (final Throwable e) {
            e.printStackTrace();
        }
        
    	logger.info("receiving message...");

    	String remoteReceived = ""; // This information was grabbed from the server, and shouldn't be empty.

        remoteReceived = queue.take();

    	logger.info("parsing message...");

        SdpUtils.parseSDP(agent, remoteReceived); // This will add the remote information to the agent.
        //Hopefully now your Agent is totally setup. Now we need to start the connections:

        agent.addStateChangeListener(new IceProcessingListener());

    	logger.info("establishing...");

        // You need to listen for state change so that once connected you can then use the socket.
        agent.startConnectivityEstablishment(); // This will do all the work for you to connect

        
        Thread.sleep(30000);
        System.exit(0);
    }

    static long startTime = System.currentTimeMillis();
    
    public static final class IceProcessingListener
    implements PropertyChangeListener
{
    /**
     * System.exit()s as soon as ICE processing enters a final state.
     *
     * @param evt the {@link PropertyChangeEvent} containing the old and new
     * states of ICE processing.
     */
    public void propertyChange(PropertyChangeEvent evt)
    {
        long processingEndTime = System.currentTimeMillis();

        Object iceProcessingState = evt.getNewValue();

        logger.info(
                "Agent entered the " + iceProcessingState + " state.");
        if(iceProcessingState == IceProcessingState.COMPLETED)
        {
            logger.info(
                    "Total ICE processing time: "
                        + (processingEndTime - startTime) + "ms");
            Agent agent = (Agent)evt.getSource();
            List<IceMediaStream> streams = agent.getStreams();

            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                logger.info(
                        "Pairs selected for stream: " + streamName);
                List<Component> components = stream.getComponents();

                for(Component cmp : components)
                {
                    String cmpName = cmp.getName();
                    logger.info(cmpName + ": "
                                    + cmp.getSelectedPair());
                }
            }

            logger.info("Printing the completed check lists:");
            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                logger.info("Check list for  stream: " + streamName);
                //uncomment for a more verbose output
                logger.info(stream.getCheckList().toString());
            }

            logger.info("Total ICE processing time to completion: "
                + (System.currentTimeMillis() - startTime));
        }
        else if(iceProcessingState == IceProcessingState.TERMINATED
                || iceProcessingState == IceProcessingState.FAILED)
        {
            /*
             * Though the process will be instructed to die, demonstrate
             * that Agent instances are to be explicitly prepared for
             * garbage collection.
             */
            ((Agent) evt.getSource()).free();

            logger.info("Total ICE processing time: "
                + (System.currentTimeMillis() - startTime));
            System.exit(0);
        }
    }
}

	private static final Logger logger = Logger.getLogger(Aaa.class.getName());
}
