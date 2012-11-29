package cs555.dht.node;

import java.io.IOException;
import java.util.ArrayList;

import cs555.dht.communications.Link;
import cs555.dht.data.DataItem;
import cs555.dht.data.DataList;
import cs555.dht.peer.Peer;
import cs555.dht.state.RefreshThread;
import cs555.dht.state.State;
import cs555.dht.utilities.*;
import cs555.dht.wireformats.DeregisterRequest;
import cs555.dht.wireformats.LookupRequest;
import cs555.dht.wireformats.LookupResponse;
import cs555.dht.wireformats.Payload;
import cs555.dht.wireformats.PredessesorLeaving;
import cs555.dht.wireformats.PredessesorRequest;
import cs555.dht.wireformats.PredessesorResponse;
import cs555.dht.wireformats.RegisterRequest;
import cs555.dht.wireformats.RegisterResponse;
import cs555.dht.wireformats.SuccessorLeaving;
import cs555.dht.wireformats.SuccessorRequest;
import cs555.dht.wireformats.TransferRequest;
import cs555.dht.wireformats.Verification;
import cs555.search.common.Continue;
import cs555.search.common.Word;
import cs555.search.common.WordSet;

public class PeerNode extends Node{


	Link managerLink;
	int refreshTime;
	String nickname;

	public String hostname;
	public int port;
	public int id;

	State state;
	DataList dataList;

	RefreshThread refreshThread;
	
	WordSet words;

	//================================================================================
	// Constructor
	//================================================================================
	public PeerNode(int p, int i, int r){
		super(p);

		port = p;
		id = i;
		refreshTime = r;

		if (id == -1) {
			id = Tools.generateHash();
		}

		managerLink = null;

		hostname = Tools.getLocalHostname();

		refreshThread = new RefreshThread(this, refreshTime);

		dataList = new DataList();
		
		words = new WordSet();
	}

	//================================================================================
	// Init
	//================================================================================
	public void initServer(){
		// Start server listening on specified port
		super.initServer();

		// Start thread for refreshing hash
		refreshThread.start();
	}

	//================================================================================
	// Send
	//================================================================================
	public void sendData(Peer p, byte[] bytes) {
		try {
			p.sendData(bytes);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void sendObject(Peer p, Object o) {
		sendData(p, Tools.objectToBytes(o));
	}
	
	
	//================================================================================
	// Enter DHT
	//================================================================================
	public void enterDHT(String dHost, int dPort) {
		managerLink = connect(new Peer(dHost, dPort));
		RegisterRequest regiserReq = new RegisterRequest(hostname, port, id);
		managerLink.sendData(regiserReq.marshall());


		// Keep sending until we are able to enter
		while (managerLink.waitForIntReply() == Constants.Failure) {
			id = Tools.generateHash();
			regiserReq = new RegisterRequest(hostname, port, id);
			managerLink.sendData(regiserReq.marshall());
		}

		// Tell Discovery we're ready for our access point
		Verification verify = new Verification(Constants.Success);
		managerLink.sendData(verify.marshall());

		state = new State(id, this);

		// Wait for data from Discovery
		byte[] randomNodeData = managerLink.waitForData();
		int messageType = Tools.getMessageType(randomNodeData);

		switch (messageType) {
		case Constants.Registration_Reply: 
			RegisterResponse accessPoint = new RegisterResponse();
			accessPoint.unmarshall(randomNodeData);

			LookupRequest lookupReq = new LookupRequest(hostname, port, id, id, 0);
			Peer poc = new Peer(accessPoint.hostName, accessPoint.port, accessPoint.id);
			poc.setLink(connect(poc));
			poc.initLink();
			
			System.out.println("Got POC : " + poc.hostname);
			
//			Link accessLink = connect(poc);
//			if (accessLink == null) {
//				System.out.println("Null:  " + accessPoint.hostName + ":" + accessPoint.port);
//			}
			
			poc.link.sendData(lookupReq.marshall());
			break;

		case Constants.Payload:
			Payload response = new Payload();
			response.unmarshall(randomNodeData);

			// If we heard back that we're the first node, modify state accordingly
			if (response.number == Constants.Null_Peer) {				
				// Add ourselves as all entries in FT
				state.firstToArrive();
			}

			break;

		default:
			System.out.println("Could not get access point from Discovery");
			break;
		}	

	}

	//================================================================================
	// Exit CDN
	//================================================================================
	public void leaveDHT(){
		// If we haven't entered, leave
		if (managerLink == null) {
			return;
		}

		DeregisterRequest dreq = new DeregisterRequest(hostname, port, id);
		managerLink.sendData(dreq.marshall());

		// Tell our successor we're leaving
		PredessesorLeaving predLeaving = new PredessesorLeaving(state.predecessor.hostname, state.predecessor.port, state.predecessor.id);
		Link successorLink = state.successor.link; 
		successorLink.sendData(predLeaving.marshall());

		// Tell our predessor we're leaving
		SuccessorLeaving sucLeaving = new SuccessorLeaving(state.successor.hostname, state.successor.port, state.successor.id);
		Link predLink = state.predecessor.link;
		predLink.sendData(sucLeaving.marshall());

		// Pass all data to our successor
		for (DataItem d : dataList.getAllData()) {
			transferData(d, state.successor);
		}

		// Remove all items from file system
		ArrayList<DataItem> filesToRemove = new ArrayList<DataItem>(dataList.getAllData());
		for (DataItem d : filesToRemove) {
			dataList.removeData(d);
		}
	}

	//================================================================================
	// DHT maintence
	//================================================================================
	public void updateFT() {
		// Ensure accuracy of Finger Table
		state.update();
	}

	public void transferDataToPredesessor() {
		ArrayList<DataItem> subset = new ArrayList<DataItem>(dataList.subsetToMove(state.predecessor.id));

		// Move all data our predessor should be in charge of
		for (DataItem d : subset) {
			transferData(d, state.predecessor);
		}

		// Remove these items from preddessesor
		for (DataItem d : subset) {
			dataList.removeData(d);
		}

	}

	//================================================================================
	// Send
	//================================================================================
	public void sendLookup(Peer p, LookupRequest l) {		
//		Link lookupPeer = p.link; 
		System.out.println("Sending lookup to peer: " + p.hostname);
		System.out.println("Lookup : " + l);
		p.link.sendData(l.marshall());
	}

	public void sendPredessessorRequest(Peer p, PredessesorRequest r) {
		//Link sucessorLink = p.link; 
		//p.initLink();
		p.link.sendData(r.marshall());
	}

	//================================================================================
	// Transfer data
	//================================================================================
	public void transferData(DataItem d, Peer p) {

		Link link = p.link;

		if (link == null){
			link = state.getNextSuccessor().link;

		}

		// Send store request
		TransferRequest storeReq = new TransferRequest(d.filename, d.filehash);
		link.sendData(storeReq.marshall());


		if (link.waitForIntReply() == Constants.Continue) {
			// Send data item to candidate
			Tools.sendFile(d.filename, link.socket);
		}
	}

	//================================================================================
	// Handle incoming words
	//================================================================================
	public void handleWord(Word word) {
		// If the word is mine, add it to our word list
		if (state.itemIsMine(word.hash)) {
			//System.out.println("My id : " + id + " word id: " + word.hash);
			words.tallyWord(word);
		}
		
		else {
			Peer nextPeer = state.getNexClosestPeer(word.hash);
			Link nextHop = nextPeer.link; 
			nextHop.sendData(Tools.objectToBytes(word));
		}
	}
	
	//================================================================================
	// Receive
	//================================================================================
	// Receieve data
	public synchronized void receive(byte[] bytes, Link l){
		int messageType = Tools.getMessageType(bytes);

		Object obj = Tools.bytesToObject(bytes);

		if (obj != null && obj instanceof WordSet) {
			WordSet words = (WordSet) obj;
			
			System.out.println("Got words set: " + words);

			for (Word word : words.words) {
				word.hash = Tools.generateHash(word.word);
				handleWord(word);
			}
			
			Continue processed = new Continue("continue");
			l.sendData(Tools.objectToBytes(processed));
			
			return;
		}

		if (obj != null && obj instanceof Word) {
			Word word = (Word) obj;
			
			handleWord(word);
			
			return;
		}
		
		switch (messageType) {
		case Constants.lookup_request:

			LookupRequest lookup = new LookupRequest();
			lookup.unmarshall(bytes);

			System.out.println("Got lookup req from : " + l.remoteHost);
			System.out.println("Lookup : " + lookup);
			
			// Info about the lookup
			int resolveID = lookup.resolveID;
			String requesterHost = lookup.hostName;
			int requesterPort = lookup.port;
			int requesterID = lookup.id;
			int entry = lookup.ftEntry;

			// If we are the target, handle it
			if (state.itemIsMine(resolveID)) {

				LookupResponse response = new LookupResponse(hostname, port, id, resolveID, entry);
//				Peer requester = new Peer(requesterHost, requesterPort, requesterID);
//				Link requesterLink = connect(requester);

				l.sendData(response.marshall());
			}

			// Else, pass it along
			else {

				//System.out.println("is not mine : " + resolveID);
				Peer nextPeer = state.getNexClosestPeer(resolveID);
				Link nextHop = nextPeer.link; 

				if (nextHop == null) {
					state.update();
					return;
				}

				lookup.hopCount++;
				
				if (Constants.logging) {
					System.out.println("Routing query from " + lookup);
				}
				
				nextHop.sendData(lookup.marshall());
			}

			break;

		case Constants.lookup_reply:

			System.out.println("Got a reply");
			LookupResponse reply = new LookupResponse();
			reply.unmarshall(bytes);

			// Heard back for FingerTable entry, update state
			state.parseState(reply, this);

			break;

		case Constants.Predesessor_Request:
			
			PredessesorRequest predReq = new PredessesorRequest();
			predReq.unmarshall(bytes);

			PredessesorResponse oldPred = new PredessesorResponse(state.predecessor.hostname, state.predecessor.port, state.predecessor.id);
			l.sendData(oldPred.marshall());

			// Add this node as our predessesor
			Peer pred = new Peer(predReq.hostName, predReq.port, predReq.id);
			pred.setLink(connect(pred));
			pred.initLink();
			state.addPredecessor(pred,false);

			break;

		case Constants.Predesessor_Response:

			PredessesorResponse predResp = new PredessesorResponse();
			predResp.unmarshall(bytes);

			Peer p = new Peer(predResp.hostName, predResp.port, predResp.id);
			p.setLink(connect(p));
			p.initLink();
			state.addPredecessor(p, false);

			if (state.successor.id != state.predecessor.id) {
				SuccessorRequest sucReq = new SuccessorRequest(hostname, port, id);
				Link successorLink = p.link; 
				successorLink.sendData(sucReq.marshall());
			}

			break;

		case Constants.Successor_Request:

			SuccessorRequest sReq = new SuccessorRequest();
			sReq.unmarshall(bytes);

			Peer sucessor = new Peer(sReq.hostName, sReq.port, sReq.id);
			sucessor.setLink(connect(sucessor));
			sucessor.initLink();
			state.addSucessor(sucessor, false);

			break;

		case Constants.Predessesor_Leaving:
			PredessesorLeaving predLeaving = new PredessesorLeaving();
			predLeaving.unmarshall(bytes);

			Peer newPred = new Peer(predLeaving.hostName, predLeaving.port, predLeaving.id);
			newPred.setLink(connect(newPred));
			newPred.initLink();
			state.addPredecessor(newPred,true);

			break;

		case Constants.Successor_Leaving:
			SuccessorLeaving sucLeaving = new SuccessorLeaving();
			sucLeaving.unmarshall(bytes);

			Peer newSuc = new Peer(sucLeaving.hostName, sucLeaving.port, sucLeaving.id);
			newSuc.setLink(connect(newSuc));
			newSuc.initLink();
			state.addSucessor(newSuc, true);

			break;

		case Constants.store_request:
			TransferRequest storeReq = new TransferRequest();
			storeReq.unmarshall(bytes);

			System.out.println("Recieved store request");

			Verification cont = new Verification(Constants.Continue);
			l.sendData(cont.marshall());

			// If we receive file, add it to our data list
			if (Tools.receiveFile(storeReq.path, l.socket)) {
				System.out.println("Receieved file: " + storeReq.path);

				DataItem data = new DataItem(storeReq.path, storeReq.filehash);
				dataList.addData(data);

				printDiagnostics();
			}

			else {
				System.out.println("Could not read : " + storeReq.path);
			}

			break;

		default:
			//System.out.println("Unrecognized Message : " + messageType);
			break;
		}
	}


	//================================================================================
	// Diagnostics
	//================================================================================
	public void printDiagnostics() {
		if (Constants.logging) {
			System.out.println("\n================================================================================");
			System.out.println(state);
			System.out.println(dataList);
			System.out.println("================================================================================\n");
		}
	}
	
	public void printWordSet() {
		System.out.println("\n================================================================================");
		System.out.println(" : " + words);
		System.out.println("================================================================================\n");
		
	}

	//================================================================================
	//================================================================================
	// Main
	//================================================================================
	//================================================================================
	public static void main(String[] args){

		String discoveryHost = "";
		int discoveryPort = 0;
		int localPort = 0;
		int id = -1;
		int refreshTime = Constants.Refresh_Time;

		if (args.length >= 3) {
			discoveryHost = args[0];
			discoveryPort = Integer.parseInt(args[1]);
			localPort = Integer.parseInt(args[2]);

			if (args.length >= 4) {
				id = Integer.parseInt(args[3]);

				if (args.length >= 5) {
					refreshTime = Integer.parseInt(args[4]);

				}
			}
		}

		else {
			System.out.println("Usage: java cs555.dht.node.PeerNode DISCOVERY-NODE DISCOVERY-PORT LOCAL-PORT <HASH> <REFRESH-TIME>");
			System.exit(1);
		}

		// Create node
		PeerNode peer = new PeerNode(localPort, id, refreshTime);

		// Enter DHT
		peer.initServer();
		peer.enterDHT(discoveryHost, discoveryPort);

		// Wait and accept User Commands
		boolean cont = true;
		while (cont){
			String input = Tools.readInput("Command: ");

			if (input.equalsIgnoreCase("exit")){
				peer.leaveDHT();
				cont = false;
				System.exit(0);

			}
			
			if (input.equalsIgnoreCase("words")) {
				peer.printWordSet();
			}
		}
	}
}
