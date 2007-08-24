package ibis.ipl.apps.benchmarks.registry;

import ibis.util.Log;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public final class Main {

	private static final Logger logger = Logger.getLogger(Main.class);

	private final IbisApplication[] apps;

	private final boolean generateEvents;
	private final boolean generateLeaves;

	Main(int threads, boolean generateEvents, boolean generateLeaves) {
		this.generateEvents = generateEvents;
		this.generateLeaves = generateLeaves;

		apps = new IbisApplication[threads];
		for (int i = 0; i < threads; i++) {
			logger.debug("starting thread " + i + " of " + threads);
			apps[i] = new IbisApplication(generateEvents, generateLeaves);
		}
	}

	void printStats() {
		int totalSeen = 0;
		for (int i = 0; i < apps.length; i++) {
			totalSeen += apps[i].nrOfIbisses();
		}
		double average = (double) totalSeen / (double) apps.length;

		System.out.printf("average seen members = %.2f  (total = %d)\n",
				average, apps.length);
	}

	public static void main(String[] args) {
		int threads = 1;
		boolean generateEvents = false;
		boolean generateLeaves = false;

		Log.initLog4J("ibis.ipl.apps", Level.INFO);

		for (int i = 0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("--threads")) {
				i++;
				threads = new Integer(args[i]);
			} else if (args[i].equalsIgnoreCase("--events")) {
				generateEvents = true;
			} else if (args[i].equalsIgnoreCase("--leaves")) {
				generateEvents = true;
				generateLeaves = true;
			} else {
				System.err.println("unknown option: " + args[i]);
				System.exit(1);
			}
		}

		Main main = new Main(threads, generateEvents, generateLeaves);

		while (true) {
			try {
				main.printStats();
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// IGNORE
			}
		}
	}

}
