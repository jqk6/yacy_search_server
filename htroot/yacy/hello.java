// hello.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
//
// You must compile this file with
// javac -classpath .:../../classes hello.java
// if the shell's current path is HTROOT

import java.net.InetAddress;
import java.util.Date;
import de.anomic.http.httpHeader;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class hello {

    private static final String STR_YOURTYPE  = "yourtype";
    private static final String STR_LASTSEEN  = "LastSeen";

    private static final String STR_IP        = "IP";
    private static final String STR_YOURIP    = "yourip";
    private static final String STR_MYTIME    = "mytime";
    private static final String STR_SEED      = "seed";
    private static final String STR_EQUAL     = "=";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final serverObjects prop = new serverObjects(); // return variable that accumulates replacements
        if ((post == null) ||
            (env == null) ||
            (yacyCore.seedDB == null) ||
            (yacyCore.seedDB.mySeed == null)) { return new serverObjects(); }

        // final String iam      = (String) post.get("iam", "");      // complete seed of the requesting peer
        // final String pattern  = (String) post.get("pattern", "");  //        
        // final String mytime   = (String) post.get(STR_MYTIME, ""); // 
        final String key      = (String) post.get("key", "");    // transmission key for response
        final String seed     = (String) post.get(STR_SEED, ""); // 
        final String countStr = (String) post.get("count", "0"); //
        int  i;
        int  count = 0;
        try {count = (countStr == null) ? 0 : Integer.parseInt(countStr);} catch (NumberFormatException e) {count = 0;}
        final Date remoteTime = yacyCore.parseUniversalDate((String) post.get(STR_MYTIME)); // read remote time
        final yacySeed remoteSeed = yacySeed.genRemoteSeed(seed, key, remoteTime);

        //System.out.println("YACYHELLO: REMOTESEED=" + ((remoteSeed == null) ? "NULL" : remoteSeed.toString()));
        if (remoteSeed == null) { return new serverObjects(); }

        // we easily know the caller's IP:
        final String clientip = (String) header.get("CLIENTIP", "<unknown>"); // read an artificial header addendum
        final String reportedip = remoteSeed.get(STR_IP, "");
        final String reportedPeerType = remoteSeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR);
        final float clientversion = remoteSeed.getVersion();

        int urls = -1;
        // if the remote client has reported its own IP address and the client supports
        // the port forwarding feature (if client version >= 0.383) then we try to 
        // connect to the reported IP address first
        if (reportedip.length() > 0 && !clientip.equals(reportedip) && clientversion >= (float)0.383) {
            // try first the reportedip, since this may be a connect from a port-forwarding host
            prop.put(STR_YOURIP, reportedip);
            remoteSeed.put(STR_IP, reportedip);
            urls = yacyClient.queryUrlCount(remoteSeed);
        }

        // if the previous attempt (using the reported ip address) was not successful, try the ip where 
        // the request came from
        if (urls < 0) {                        
            boolean isLocalIP = false;
            if (serverCore.portForwardingEnabled) {
                try {
                    final InetAddress clientAddress = InetAddress.getByName(clientip);                    
                    if (clientAddress.isAnyLocalAddress() || clientAddress.isLoopbackAddress()) {
                        isLocalIP = true;
                    } else {
                        final InetAddress[] localAddress = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
                        for (i = 0; i < localAddress.length; i++) {
                            if (localAddress[i].equals(clientAddress)) {
                                isLocalIP = true;
                                break;
                            }
                        }  
                    }
                } catch (Exception e) {}
            }

            // we are only allowed to connect to the client IP address if it's not our own address
            if (!isLocalIP) {
                prop.put(STR_YOURIP, clientip);
                remoteSeed.put(STR_IP, clientip);
                urls = yacyClient.queryUrlCount(remoteSeed);
            }
        }

//      System.out.println("YACYHELLO: YOUR IP=" + clientip);

        // assign status
        if (urls >= 0) {
            if (remoteSeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) == null) {
                prop.put(STR_YOURTYPE, yacySeed.PEERTYPE_SENIOR);
                remoteSeed.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR);
            } else if (remoteSeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_PRINCIPAL).equals(yacySeed.PEERTYPE_PRINCIPAL)) {
                prop.put(STR_YOURTYPE, yacySeed.PEERTYPE_PRINCIPAL);
            } else {
                prop.put(STR_YOURTYPE, yacySeed.PEERTYPE_SENIOR);
                remoteSeed.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR);
            }
            // connect the seed
            yacyCore.peerActions.peerArrival(remoteSeed, true);
        } else {
            prop.put(STR_YOURTYPE, yacySeed.PEERTYPE_JUNIOR);
            remoteSeed.put(STR_LASTSEEN, yacyCore.universalDateShortString());
            yacyCore.peerActions.juniorConnects++; // update statistics
            remoteSeed.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR);
            yacyCore.log.logInfo("hello: responded remote junior peer '" + remoteSeed.getName() + "' from " + reportedip);
            // no connection here, instead store junior in connection cache
            if ((remoteSeed.hash != null) && (remoteSeed.isProper() == null)) {
                yacyCore.peerActions.peerPing(remoteSeed);
            }
        }
        if (!((String)prop.get(STR_YOURTYPE)).equals(reportedPeerType)) {
            yacyCore.log.logInfo("hello: changing remote peer '" + remoteSeed.getName() +
                                                           "' [" + reportedip +
                                             "] peerType from '" + reportedPeerType +
                                                        "' to '" + prop.get(STR_YOURTYPE) + "'.");
        }

        final StringBuffer seeds = new StringBuffer(768);
        // attach some more seeds, as requested
        if ((yacyCore.seedDB != null) && (yacyCore.seedDB.sizeConnected() > 0)) {
            if (count > yacyCore.seedDB.sizeConnected()) { count = yacyCore.seedDB.sizeConnected(); }
            if (count > 100) { count = 100; }
            final yacySeed[] ySeeds = yacyCore.seedDB.seedsByAge(true, count); // latest seeds
            seeds.ensureCapacity((ySeeds.length + 1) * 768);
            // attach also my own seed
            seeds.append("seed0=").append(yacyCore.seedDB.mySeed.genSeedStr(key)).append(serverCore.crlfString);
            count = 1;
            for (i = 1; i < ySeeds.length; i++) {
                if ((ySeeds[i] != null) && (ySeeds[i].isProper() == null)) {
                    seeds.append(STR_SEED).append(count).append(STR_EQUAL).append(ySeeds[i].genSeedStr(key)).append(serverCore.crlfString);
                    count++;
                }
            }
        } else {
            // attach also my own seed
            seeds.append("seed0=").append(yacyCore.seedDB.mySeed.genSeedStr(key)).append(serverCore.crlfString);
        }

        prop.put(STR_MYTIME, yacyCore.universalDateShortString());
        prop.put("seedlist", seeds.toString());
        // return rewrite properties
        return prop;
    }

}

