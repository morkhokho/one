package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.util.RoutingInfo;
import util.Tuple;

import java.io.*;
import java.io.FileWriter;
import java.lang.Math;
import core.SimClock;
import core.UpdateListener;

public class EBCRouter extends ActiveRouter {

    private Map<DTNHost, Integer> encounters;
    private Map<DTNHost, Integer> buffer;
    private Map<DTNHost, Integer> cDuration;
    private Map<DTNHost, Integer> multiplication;
    Integer start1;

    public EBCRouter(Settings s) {
        super(s);
        initEncounter();
        initBuffer();
    }

    public EBCRouter(EBCRouter r) {
        super(r);
        // TODO Auto-generated constructor stub
        //this.tau = r.tau;
        initEncounter();
        initBuffer();
        initcDuration();
    }

    private void initcDuration() {
        this.cDuration = new HashMap<DTNHost, Integer>();
    }

    private void initEncounter() {
        this.encounters = new HashMap<DTNHost, Integer>();
    }

    private void initBuffer() {
        this.buffer = new HashMap<DTNHost, Integer>();
    }

    private void initMultiplication() {
        this.multiplication = new HashMap<DTNHost, Integer>();
    }

    @Override
    public void update() {
        super.update();
        if (!this.canStartTransfer() || this.isTransferring()) {
            return; // Nothing to transfer or is currently transferring.
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        tryOtherMessages();
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        if (con.isUp()) {
            DTNHost othHost = con.getOtherNode(this.getHost());
            start1 = (int) System.nanoTime();
            //System.out.println(getHost() + " " + othHost);
            this.updatecDuration(this.getHost(),othHost, con, start1);
            //System.out.println("cDuration" + cDuration);
            this.updateEncounters(othHost);
            //System.out.println("encounters" + this.encounters);
            this.updateBuffer(othHost);
            //System.out.println("buffer" + this.buffer);
            this.updateMultipli(othHost);
            //System.out.println("Multi" + this.multiplication);
            
        }
        
    }

    // private void updatecDuration(DTNHost host, Integer start) {
    //     if (this.cDuration == null) {
    //         this.initcDuration();
    //     }
    //     if (this.cDuration.containsKey(host)) {
    //         Integer end1 = (int) System.nanoTime();
    //         this.cDuration.put(host, this.buffer.get(host) + (end1 - start1));
    //     } else {
    //         this.cDuration.put(host,1);
    //     }
    // }

    
    private void updatecDuration(DTNHost host,DTNHost otrHost, Connection con, Integer start1) {
        if (this.cDuration == null) {
            this.initcDuration();
        }
        if (this.cDuration.containsKey(otrHost)) {
            // Integer end1 = (int) System.nanoTime();

            if (getCDFor(host) >= getAvgCDFor(host)) {
                ConnectionInfo cInfo = new ConnectionInfo(host, otrHost);
                cInfo.getConnectionTime();

                this.cDuration.put(otrHost, (int) cInfo.getConnectionTime());
                //System.out.println("Time "+(int) cInfo.getConnectionTime());
            }
           
        } else {
            this.cDuration.put(otrHost, 1);
        }
        
    }

    private void updateBuffer(DTNHost host) {
        if (this.buffer == null) {
            this.initBuffer();
        }
        if (this.buffer.containsKey(host)) {
            if (getBufferFor(host) >= getAvgBufferFor(host)) {
                this.buffer.put(host, (int) getFreeBufferSize());
            } 
            // this.buffer.put(host, this.buffer.get(host) + (int) getFreeBufferSize());
        } else {
            this.buffer.put(host, (int) getBufferSize());
        }
    }

    private void updateEncounters(DTNHost host) {
        if (this.encounters == null) {
            this.initEncounter();
        }
        if (this.encounters.containsKey(host)){
            if (getEncounterFor(host) >= getAvgEncounterFor(host)) {
                this.encounters.put(host, this.encounters.get(host) + 1);
            }
        } else {
            this.encounters.put(host, 1);
        }
    }

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
        Collection<Message> msgCollection = this.getMessageCollection();
        // For all host to which we have to send messages
        // calculate gamma.
        for (Connection conn : this.getConnections()) {
            DTNHost othHost = conn.getOtherNode(this.getHost());
            EBCRouter othRouter = (EBCRouter) othHost.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring.
            }

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other host has.
                }
                if (othRouter.getMultipliFor(m.getTo()) >= this.getAvgMultiplicationFor(m.getTo())) {
                    //					double delivPredNeighbor = othRouter.getLambdaFor(m.getTo()) *
                    //							othRouter.getPredFor(m.getTo()) * tau;
                    //					double delivPredSource = this.getLambdaFor(m.getTo()) *
                    //							this.getPredFor(m.getTo()) * tau;
                    //if (delivPredNeighbor > delivPredSource) {
                    messages.add(new Tuple<Message, Connection>(m, conn));
                    //}
                }
            }
        }

        if (messages.size() == 0) {
            return null;
        }

        return this.tryMessagesForConnected(messages); // try to send all messages
    }

    public double getAvgMultiplicationFor(DTNHost dest) {
        double sumOfMultiplication = 0;
        int nrofNeighbors = 0;
        for (Connection con : this.getConnections()) {
            EBCRouter neighbor = (EBCRouter) con.getOtherNode(this.getHost())
                    .getRouter();
            sumOfMultiplication += neighbor.getMultipliFor(dest);
            nrofNeighbors++;
        }
        if (nrofNeighbors != 0) {
            return sumOfMultiplication / nrofNeighbors;
        } else {
            return 0;
        }
    }

    public double getAvgEncounterFor(DTNHost dest) {
        double sumOfEncounter = 0;
        int nrofNeighbors = 0;
        for (Connection con : this.getConnections()) {
            EBCRouter neighbor = (EBCRouter) con.getOtherNode(this.getHost())
                    .getRouter();
            sumOfEncounter += neighbor.getEncounterFor(dest);
            nrofNeighbors++;
        }
        if (nrofNeighbors != 0) {
            return sumOfEncounter / nrofNeighbors;
        } else {
            return 0;
        }
    }
    
    public double getAvgBufferFor(DTNHost dest) {
        double sumOfBuffer = 0;
        int nrofNeighbors = 0;
        for (Connection con : this.getConnections()) {
            EBCRouter neighbor = (EBCRouter) con.getOtherNode(this.getHost())
                    .getRouter();
            sumOfBuffer += neighbor.getBufferFor(dest);
            nrofNeighbors++;
        }
        if (nrofNeighbors != 0) {
            return sumOfBuffer / nrofNeighbors;
        } else {
            return 0;
        }
    }
    
    public double getAvgCDFor(DTNHost dest) {
        double sumOfCD = 0;
        int nrofNeighbors = 0;
        for (Connection con : this.getConnections()) {
            EBCRouter neighbor = (EBCRouter) con.getOtherNode(this.getHost())
                    .getRouter();
                    sumOfCD += neighbor.getCDFor(dest);
            nrofNeighbors++;
        }
        if (nrofNeighbors != 0) {
            return sumOfCD / nrofNeighbors;
        } else {
            return 0;
        }
    }

    private void updateMultipli(DTNHost host) {
        if (this.multiplication == null) {
            this.initMultiplication();
        }
        if (this.multiplication.containsKey(host)) {
            // System.out.println("Encounter "+this.encounters.get(host));
            // System.out.println("Buffer "+this.buffer.get(host));
            // System.out.println("cDuration " + this.cDuration.get(host));
            // System.out.println("Working Directory = " + System.getProperty("user.dir"));

            // String fileName = "reports/reportValue.txt";
            // try {
            //     BufferedWriter out = new BufferedWriter(
            //             new FileWriter(fileName, true));

            //     out.write("\n Host " + host + "\nEncounter " + this.encounters.get(host) + "->" +
            //             "Buffer " + this.buffer.get(host) + "->" +
            //             "cDuration " + Math.abs(this.cDuration.get(host)));
            //     // Closing the connection
            //     out.close();
            // } catch (IOException e) {
            //     System.out.println("exception occurred" + e);
            // }

            this.multiplication.put(host, this.encounters.get(host) * this.buffer.get(host) * this.cDuration.get(host));
        } else {
            this.multiplication.put(host, 1);
        }
    }

    public int getMultipliFor(DTNHost host) {
        if (this.multiplication.containsKey(host)) {
            return this.multiplication.get(host);
        } else {
            return 0;
        }
    }

    public int getEncounterFor(DTNHost host) {
        if (this.encounters.containsKey(host)) {
            return this.encounters.get(host);
        } else {
            return 0;
        }
    }

    public int getCDFor(DTNHost host) {
        if (this.cDuration.containsKey(host)) {
            return this.cDuration.get(host);
        } else {
            return 0;
        }
    }

    public int getBufferFor(DTNHost host) {
        if (this.encounters.containsKey(host)) {
            return this.buffer.get(host);
        } else {
            return 0;
        }
    }

    @Override
    public MessageRouter replicate() {
        EBCRouter r = new EBCRouter(this);
        return r;
    }

    @Override
    public RoutingInfo getRoutingInfo() {
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(multiplication.size() +
                " delivery parameters(s)");
        ri.addMoreInfo(new RoutingInfo(encounters.size() +
                " host encounter(s)"));

        for (Map.Entry<DTNHost, Integer> e : encounters.entrySet()) {
            DTNHost host = e.getKey();
            int value = e.getValue();

            ri.addMoreInfo(new RoutingInfo(String.format("%s : %d",
                    host, value)));
        }

        for (Map.Entry<DTNHost, Integer> e : multiplication.entrySet()) {
            DTNHost host = e.getKey();
            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
                    host, e.getValue())));
        }

        top.addMoreInfo(ri);
        return top;
    }




    protected class ConnectionInfo {
		private double startTime;
		private double endTime;
		private DTNHost h1;
		private DTNHost h2;

		public ConnectionInfo (DTNHost h1, DTNHost h2){
			this.h1 = h1;
			this.h2 = h2;
			this.startTime = getSimTime();
			this.endTime = -1;
		}

		/**
		 * Should be called when the connection ended to record the time.
		 * Otherwise {@link #getConnectionTime()} will use end time as
		 * the time of the request.
		 */
		public void connectionEnd() {
			this.endTime = getSimTime();
		}

		/**
		 * Returns the time that passed between creation of this info
		 * and call to {@link #connectionEnd()}. Unless connectionEnd() is
		 * called, the difference between start time and current sim time
		 * is returned.
		 * @return The amount of simulated seconds passed between creation of
		 * this info and calling connectionEnd()
		 */
		public double getConnectionTime() {
			if (this.endTime == -1) {
				return getSimTime() - this.startTime;
			}
			else {
				return this.endTime - this.startTime;
			}
		}

		/**
		 * Returns true if the other connection info contains the same hosts.
		 */
		public boolean equals(Object other) {
			if (!(other instanceof ConnectionInfo)) {
				return false;
			}

			ConnectionInfo ci = (ConnectionInfo)other;

			if ((h1 == ci.h1 && h2 == ci.h2)) {
				return true;
			}
			else if ((h1 == ci.h2 && h2 == ci.h1)) {
				// bidirectional connection the other way
				return true;
			}
			return false;
		}

		/**
		 * Returns the same hash for ConnectionInfos that have the
		 * same two hosts.
		 * @return Hash code
		 */
		public int hashCode() {
			String hostString;

			if (this.h1.compareTo(this.h2) < 0) {
				hostString = h1.toString() + "-" + h2.toString();
			}
			else {
				hostString = h2.toString() + "-" + h1.toString();
			}

			return hostString.hashCode();
		}

		/**
		 * Returns a string representation of the info object
		 * @return a string representation of the info object
		 */
		public String toString() {
			return this.h1 + "<->" + this.h2 + " [" + this.startTime
				+"-"+ (this.endTime >0 ? this.endTime : "n/a") + "]";
		}
	}
    public double getSimTime() {
		return SimClock.getTime();
	}
}