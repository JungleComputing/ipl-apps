package ibis.ipl.apps.benchmarks.registry;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisCreationFailedException;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.PortType;
import ibis.ipl.RegistryEventHandler;
import ibis.util.ThreadPool;

import org.apache.log4j.Logger;

final class IbisApplication implements Runnable, RegistryEventHandler {

    private static final Logger logger =
            Logger.getLogger(IbisApplication.class);

    private final boolean generateEvents;

    private final boolean generateLeaves;

    private Set<IbisIdentifier> ibisses;

    private boolean stopped = false;

    private final Random random;

    IbisApplication(boolean generateEvents, boolean generateLeaves) {
        this.generateEvents = generateEvents;
        this.generateLeaves = generateLeaves;

        ibisses = new HashSet<IbisIdentifier>();
        random = new Random();

        // register shutdown hook
        try {
            Runtime.getRuntime().addShutdownHook(new Shutdown(this));
        } catch (Exception e) {
            // IGNORE
        }

        ThreadPool.createNew(this, "application");
    }

    private Ibis join() throws IbisCreationFailedException {
        IbisCapabilities s =
                new IbisCapabilities(IbisCapabilities.MEMBERSHIP,
                        IbisCapabilities.ELECTIONS, IbisCapabilities.SIGNALS);
        PortType p =
                new PortType(PortType.CONNECTION_ONE_TO_ONE,
                        PortType.SERIALIZATION_DATA);

        logger.debug("creating ibis");
        Ibis ibis = IbisFactory.createIbis(s, null, true, this, p);
        logger.debug("ibis created, enabling upcalls");

        ibis.registry().enableEvents();
        logger.debug("upcalls enabled");

        return ibis;
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
    }

    public void left(IbisIdentifier ident) {
        ibisses.remove(ident);
    }

    public void died(IbisIdentifier corpse) {
        ibisses.remove(corpse);
    }

    public void gotSignal(String signal) {
        System.err.println("got signal: " + signal);
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

        IbisIdentifier[] result =
                new IbisIdentifier[random.nextInt(nrOfIbisses())];

        for (int i = 0; i < result.length; i++) {
            result[i] = getRandomIbis();
        }

        return result;
    }

    public void run() {
        try {
            if (!generateEvents) {
                Ibis ibis = join();
                waitUntilStopped();
                ibis.end();
                return;
            }

            // start Ibis, generate events, stop Ibis, repeat
            while (true) {
                Ibis ibis = join();

                // generate events, stop when we left
                boolean left = false;
                while (!left) {
                    if (stopped()) {
                        ibis.end();
                        return;
                    }

                    Thread.sleep(10000);

                    int nextCase = random.nextInt(8);
                    switch (nextCase) {
                    case 0:
                        if (generateLeaves) {
                            logger.debug("doing leave");
                            ibis.end();
                            left = true;
                        }
                        break;
                    case 1:
                        logger.debug("doing elect");
                        IbisIdentifier id1 = ibis.registry().elect("bla");
                        break;
                    case 2:
                        logger.debug("doing getElectionResult");
                        //make sure this election exists
                        ibis.registry().elect("bla");
                        
                        IbisIdentifier id2 =
                                ibis.registry().getElectionResult("bla");
                        break;
                    case 3:
                        logger.debug("doing getElectionResult with timeout");
                        IbisIdentifier id3 =
                                ibis.registry().getElectionResult("bla", 100);
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
                        logger.debug("doing assumeDead() on random ibis");
                        if (generateLeaves) {
                            IbisIdentifier victim = getRandomIbis();

                            if (victim != null) {
                                ibis.registry().assumeDead(victim);
                            }
                        }
                        break;
                    case 6:
                        logger.debug("signalling random member");
                        IbisIdentifier signallee = getRandomIbis();

                        if (signallee != null) {
                            ibis.registry().signal("ARRG!", signallee);
                        }
                        break;
                    case 7:
                        logger.debug("signalling random member(s)");
                        IbisIdentifier[] signalList = getRandomIbisses();

                        ibis.registry().signal("ARRG to you all!", signalList);
                        break;
                    default:
                        logger.error("unknown case: " + nextCase);
                    }
                    
                    System.out.println("done");

                }
            }

        } catch (Exception e) {
            logger.error("error in  application", e);
            return;
        }

    }
}
