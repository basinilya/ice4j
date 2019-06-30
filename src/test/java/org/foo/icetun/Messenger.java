package org.foo.icetun;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Messenger implements Closeable {

	static {
		LogTestConf.init();
	}

	private static final int PORT = 6270;
	// private static final int MAX_JUMBO = 10000;

	private static final InetSocketAddress[] PEERS = { //
			mkPeer("192.168.21.116", PORT), //
			mkPeer("192.168.148.87", PORT), //
			mkPeer("192.168.21.116", PORT + 1), //
			mkPeer("192.168.148.87", PORT + 1) //
	};

	private static InetSocketAddress mkPeer(String hostname, int port) {
		return hostname == null ? new InetSocketAddress(port) : InetSocketAddress.createUnresolved(hostname, port);
	}

	public static void main(String[] args) throws Exception {
		try (Messenger inst = new Messenger() {
			@Override
			public void onMessage(String msg) {
				System.out.println(msg);
			}
		}) {
			inst.start();
			for (;;) {
				inst.sendMessage("zzz");
				Thread.sleep(10000);
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (ss != null) {
			ss.close();
			ss = null;
		}
		if (sock != null) {
			sock.close();
			sock = null;
		}
		if (recvThread.isAlive()) {
			try {
				recvThread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (executor != null) {
			executor.shutdown();
			try {
				executor.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public void onMessage(String msg) {
		//
	}

	public void start() throws Exception {
		boolean ok = false;
		try {
			start0();
			ok = true;
		} finally {
			if (!ok) {
				close();
			}
		}
	}

	public void sendMessage(String msg) throws IOException {
		out.writeUTF(msg);
		out.flush();
	}

	private final ArrayList<InetSocketAddress> otherPeers = new ArrayList<>();

	private ServerSocket ss;

	private Socket sock;

	private DataOutputStream out;

	private DataInput in;

	private static final String HANDSHAKE = "hi\n";

	private void handshake(Socket tmp) throws IOException {
		boolean ok = false;
		try {
			byte[] sample = HANDSHAKE.getBytes("UTF-8");
			tmp.getOutputStream().write(sample);
			DataInputStream tmpIn = new DataInputStream(tmp.getInputStream());
			byte[] recvBuf = new byte[sample.length];
			int saveTmo = tmp.getSoTimeout();
			tmp.setSoTimeout(10000);
			tmpIn.readFully(recvBuf);
			tmp.setSoTimeout(saveTmo);
			if (!Arrays.equals(sample, recvBuf)) {
				throw new IOException("messenger handshake failed");
			}
			ok = true;
		} finally {
			if (!ok) {
				tmp.close();
			}
		}
	}

	private ExecutorService executor;

	private void start0() throws Exception {

		for (InetSocketAddress unresolved : PEERS) {
			final InetSocketAddress resolved;
			try {
				// TODO: resolve may take long
				resolved = unresolved.isUnresolved()
						? new InetSocketAddress(InetAddress.getByName(unresolved.getHostName()), unresolved.getPort())
						: unresolved;
			} catch (UnknownHostException e) {
				LOGGER.log(Level.FINE, "{0}", (Object) e);
				continue;
			}
			if (ss == null) {
				boolean ok = false;
				try {
					ss = new ServerSocket(resolved.getPort(), 10, resolved.getAddress());
					LOGGER.log(Level.INFO, "bound to: {0}", ss.getLocalSocketAddress());
					ok = true;
					continue;
				} catch (SocketException e) {
					LOGGER.log(Level.FINE, "{0} {1}", new Object[] { resolved, e });
				} finally {
					if (!ok && ss != null) {
						close();
					}
				}
			}
			otherPeers.add(resolved);
		}
		if (ss == null) {
			throw new Exception("bind failed");
		}
		LOGGER.log(Level.INFO, "other peers: {0}", otherPeers);

		int nTasks = 0;
		ExecutorCompletionService<Socket> ecs = null;
		executor = Executors.newCachedThreadPool();
		try {
			ecs = new ExecutorCompletionService<>(executor);

			final ServerSocket saveSs = ss;
			ecs.submit(new Callable<Socket>() {
				@Override
				public Socket call() throws Exception {
					for (;;) {
						Socket tmp = saveSs.accept();
						handshake(tmp);
						return tmp;
					}
				}
			});
			nTasks = 1;
			LOGGER.log(Level.FINE, "done ecs.submit()");

			for (InetSocketAddress otherPeer : otherPeers) {
				LOGGER.log(Level.FINE, "done ecs.submit()");
				ecs.submit(new Callable<Socket>() {
					@Override
					public Socket call() throws Exception {
						Socket tmp = new Socket(otherPeer.getAddress(), otherPeer.getPort());
						handshake(tmp);
						return tmp;
					}
				});
				nTasks++;
			}
			for (; nTasks > 0;) {
				try {
					LOGGER.log(Level.FINE, "calling ecs.take()...");
					Future<Socket> completedFut = ecs.take();
					nTasks--;
					sock = completedFut.get();
					LOGGER.log(Level.INFO, "connected to: {0}", sock.getRemoteSocketAddress());
					break;
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					LOGGER.log(Level.INFO, "connect failed: {0}", (Object) cause);
				}
			}
		} finally {
			// this does not call Future.cancel(), i.e. already commenced
			// futures can be resolved
			List<Runnable> uncommenced = executor.shutdownNow();
			nTasks -= uncommenced.size();
			LOGGER.log(Level.FINE, "uncommenced tasks: {0}", uncommenced.size());

			final ExecutorService saveExecutor = executor;
			executor = Executors.newSingleThreadExecutor();
			final int nTasks2 = nTasks;
			final ExecutorCompletionService<Socket> ecs2 = ecs;
			executor.submit(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int nTasks = nTasks2;
					for (; nTasks > 0; nTasks--) {
						try {
							LOGGER.log(Level.FINE, "calling ecs.take()...");
							Socket tmp = ecs2.take().get();
							LOGGER.log(Level.INFO, "also connected to: {0}", sock.getRemoteSocketAddress());
							try {
								tmp.close();
							} catch (Exception e) {
								//
							}
						} catch (ExecutionException e) {
							Throwable cause = e.getCause();
							LOGGER.log(Level.INFO, "connect failed: {0}", (Object) cause);
						}
					}
					saveExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
					return null;
				}
			});
		}
		if (sock == null) {
			throw new Exception("failed to connect");
		}
		ss.close();
		ss = null;
		in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
		out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
		recvThread.setName("recvThread " + sock.getRemoteSocketAddress());
		recvThread.start();
	}

	// private final LinkedBlockingQueue<DatagramPacket> recvQueue = new
	// LinkedBlockingQueue<>();
	// private final byte[] recvBuf = new byte[MAX_JUMBO];

	// private final byte[] sendBuf = new byte[2000];
	// private final ByteBuffer ba = ByteBuffer.allocate(MAX_JUMBO);
	// private DatagramPacket udpReve = new DatagramPacket(ba.array(),

	private void recvLoop() throws Exception {
		for (;;) {
			// recvQueue.add(udpReve);
			final String msg;
			try {
				msg = in.readUTF();
			} catch (IOException e) {
				break;
			}
			try {
				onMessage(msg);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "", e);
			}
		}
	}

	// private final ByteBuffer ba = ByteBuffer.allocate(MAX_JUMBO);
	// private DatagramPacket udpReve = new DatagramPacket(ba.array(),
	// ba.capacity());

	private final Thread recvThread = new Thread(new Runnable() {

		@Override
		public void run() {
			try {
				recvLoop();
			} catch (Throwable e) {
				LOGGER.log(Level.SEVERE, "", e);
			}
		}
	});

	private static final Logger LOGGER = Logger.getLogger(Messenger.class.getName());

}
