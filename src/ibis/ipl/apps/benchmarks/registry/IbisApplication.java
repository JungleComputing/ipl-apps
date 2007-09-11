package ibis.ipl.apps.benchmarks.registry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisCreationFailedException;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.RegistryEventHandler;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;
import ibis.util.ThreadPool;

import org.apache.log4j.Logger;

final class IbisApplication implements Runnable, RegistryEventHandler,
		MessageUpcall {

	private static final Logger logger = Logger
			.getLogger(IbisApplication.class);

	private final boolean generateEvents;

	private Set<IbisIdentifier> ibisses;

	private boolean stopped = false;

	private final Random random;

	private final Ibis ibis;
	private final PortType portType;
	private final ReceivePort receivePort;

	private final Stats stats;
	
	private final Map<IbisIdentifier, Stats> gatheredStats;

	IbisApplication(boolean generateEvents) throws IbisCreationFailedException,
			IOException {
		this.generateEvents = generateEvents;
		gatheredStats = new HashMap<IbisIdentifier, Stats>();

		ibisses = new HashSet<IbisIdentifier>();
		random = new Random();

		portType = new PortType(PortType.CONNECTION_MANY_TO_ONE,
				PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_AUTO_UPCALLS);

		IbisCapabilities s = new IbisCapabilities(IbisCapabilities.MEMBERSHIP,
				IbisCapabilities.ELECTIONS, IbisCapabilities.SIGNALS);

		logger.debug("creating ibis");
		ibis = IbisFactory.createIbis(s, null, true, this, portType);
		
		logger.debug("ibis created, enabling upcalls");
		
		stats = new Stats(ibis.identifier());

		ibis.registry().enableEvents();
		logger.debug("upcalls enabled");

		receivePort = ibis.createReceivePort(portType, "stats", this);
		receivePort.enableConnections();
		receivePort.enableMessageUpcalls();

		// register shutdown hook
		try {
			Runtime.getRuntime().addShutdownHook(new Shutdown(this));
		} catch (Exception e) {
			// IGNORE
		}

		ThreadPool.createNew(this, "application");
	}

	private void sendStats() {
		try {
			IbisIdentifier master = ibis.registry().elect("master");

			SendPort sendPort = ibis.createSendPort(portType);
			sendPort.connect(master, "stats");

			WriteMessage message = sendPort.newMessage();
			message.writeObject(stats);

			message.finish();
			sendPort.close();

		} catch (Exception e) {
			// IGNORE
		}
	}
	
	private synchronized void writeStats() throws FileNotFoundException {
		if (gatheredStats.isEmpty()) {
			return;
		}
		
		File file = new File("stats");
		if (file.exists()) {
		file.renameTo(new File("stats.old"));
		}
		
		PrintWriter writer = new PrintWriter(file);

        writer.println("stats at "
                + new Date(System.currentTimeMillis()));
        writer.println("//  time   -  number of ibisses");
        writer.println("//(seconds)");

        long currentTime = stats.currentTime();
        long interval = currentTime / 25;
        
        for(int i = 0; i < currentTime; i+= interval) {
        	long total = 0;
        	for (Stats s: gatheredStats.values()) {
        		total += s.valueAt(i);
        	}
        	double value = (double) total / (double) gatheredStats.size();
        	
        	writer.printf("%d  %.4f\n", i, value);
        	
        }
        writer.close();
		
	}
		

	private synchronized boolean stopped() {
		return stopped;
	}

	synchronized void end() {
		stopped = true;
		notifyAll();

	}

	public synchronized void joined(IbisIdentifier ident) {
		ibisses.add(ident);
		stats.update(ibisses.size());
		logger.info("upcall for join of: " + ident);
	}

	public synchronized void left(IbisIdentifier ident) {
		ibisses.remove(ident);
		stats.update(ibisses.size());
		logger.info("upcall for leave of: " + ident);
	}

	public synchronized void died(IbisIdentifier corpse) {
		ibisses.remove(corpse);
		stats.update(ibisses.size());
		logger.info("upcall for died of: " + corpse);
	}

	public synchronized void gotSignal(String signal) {
		logger.info("got signal: " + signal);
	}

	private static class Shutdown extends Thread {
		private final IbisApplication app;

		Shutdown(IbisApplication app) {
			this.app = app;
		}

		public void run() {
			System.err.println("shutdown hook triggered");

			app.end();
		}
	}

	public int nrOfIbisses() {
		return ibisses.size();
	}

	private synchronized void waitUntilStopped() {
		while (!stopped) {
			try {
				wait();
			} catch (InterruptedException e) {
				// IGNORE
			}
		}
	}

	private synchronized IbisIdentifier getRandomIbis() {
		if (ibisses.isEmpty()) {
			return null;
		}

		int element = random.nextInt(ibisses.size());

		for (IbisIdentifier ibis : ibisses) {
			if (element == 0) {
				return ibis;
			}
			element--;
		}

		return null;
	}

	// get random ibisses. May/will contain some duplicates :)
	private synchronized IbisIdentifier[] getRandomIbisses() {
		if (nrOfIbisses() == 0) {
			return new IbisIdentifier[0];
		}

		IbisIdentifier[] result = new IbisIdentifier[random
				.nextInt(nrOfIbisses())];

		for (int i = 0; i < result.length; i++) {
			result[i] = getRandomIbis();
		}

		return result;
	}

	void doElect(String id) throws IOException {
		ibis.registry().elect(id);
	}

	void getElectionResult(String id) throws IOException {
		ibis.registry().getElectionResult(id);
	}

	public void run() {
		// start Ibis, generate events, stop Ibis, repeat
		while (true) {
			try {
				while (true) {
					if (stopped()) {
						ibis.end();
						return;
					}

					synchronized (this) {
						try {
							wait(10000);
						} catch (InterruptedException e) {
							// IGNORE
						}
					}
					
					if (stopped()) {
						ibis.end();
						return;
					}
					
					sendStats();
					writeStats();

					if (generateEvents) {

						int nextCase = random.nextInt(6);

						switch (nextCase) {
						case 0:
							logger.debug("signalling random member(s)");
							IbisIdentifier[] signalList = getRandomIbisses();

							ibis.registry().signal("ARRG to you all!",
									signalList);
							break;
						case 1:
							logger.debug("doing elect");
							IbisIdentifier id1 = ibis.registry().elect("bla");
							break;
						case 2:
							logger.debug("doing getElectionResult");
							// make sure this election exists
							ibis.registry().elect("bla");

							IbisIdentifier id2 = ibis.registry()
									.getElectionResult("bla");
							break;
						case 3:
							logger
									.debug("doing getElectionResult with timeout");
							IbisIdentifier id3 = ibis.registry()
									.getElectionResult("bla", 100);
							logger.debug("done getElectionResult with timeout");
							break;
						case 4:
							logger.debug("doing maybeDead() on random ibis");
							IbisIdentifier suspect = getRandomIbis();
							if (suspect != null) {
								ibis.registry().maybeDead(suspect);
							}
							break;
						case 5:
							logger.debug("signalling random member");
							IbisIdentifier signallee = getRandomIbis();

							if (signallee != null) {
								ibis.registry().signal("ARRG!", signallee);
							}
							break;
						default:
							logger.error("unknown case: " + nextCase);
						}

						logger.info("done");
					}

				}
			} catch (Exception e) {
				logger.error("error in  application", e);
			}
		}

	}
	
	private synchronized void receivedStats(Stats stats) {
		gatheredStats.put(stats.getIbis(), stats);
	}

	public void upcall(ReadMessage readMessage) throws IOException {
		Stats stats;
		try {
			stats = (Stats) readMessage.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return;
		}

		readMessage.finish();

		synchronized(this) {
			receivedStats(stats);
		}
	}
}
