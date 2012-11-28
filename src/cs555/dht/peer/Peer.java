package cs555.dht.peer;

import java.io.IOException;

import cs555.dht.communications.Link;

// Class to abstract the peer
public class Peer {

	public String hostname;
	public int port;
	public String nickname;
	public int id;
	Link link;

	public boolean ready;
	
	//================================================================================
	// Constructors
	//================================================================================
	public Peer(String host, int p, int h) {
		hostname = host;
		port = p;
		id = h;
		link = null;
		ready = true;
	}

	public Peer(String host, int p) {
		hostname = host;
		port = p;
		id = -1;
		ready = true;
	}

	//================================================================================
	// Link Mehotds
	//================================================================================
	public void setLink(Link l) {
		link = l;
	}

	public void initLink() {
		if (link != null) {
			link.initLink();
		}
	}

	public void sendData(byte[] bytes) throws IOException {
		if (link != null) { 
			link.sendData(bytes);
		}

		else {
			System.out.println("Fucking link is null");
		}
	}

	public byte[] waitForData() {
		return link.waitForData();
	}

	//================================================================================
	// House Keeping
	//================================================================================

	// Override .equals method
	public boolean equals(Peer other) {
		if (other.hostname.equalsIgnoreCase(this.hostname)) {
			if (other.port == this.port) {
				if (other.id == this.id){
					return true;
				}
			}
		}

		return false;
	}

	// Override .toString method
	public String toString() {
		String s = "";

		s += "[" + hostname + ", " + port + ", " + id + "]";

		return s;
	}

}