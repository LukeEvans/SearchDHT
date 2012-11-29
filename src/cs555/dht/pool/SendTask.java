package cs555.dht.pool;

import java.io.IOException;

import cs555.dht.communications.Link;

public class SendTask implements Task {

	Link link;
	byte[] dataToSend;

	//================================================================================
	// Constructor
	//================================================================================
	public SendTask(Link l, byte[] d) {
		link = l;
		dataToSend = d;
	}

	public void run() {				
		link.sendData(dataToSend);
	}

	@Override
	public void setRunning(int i) {
		// TODO Auto-generated method stub

	}



}
