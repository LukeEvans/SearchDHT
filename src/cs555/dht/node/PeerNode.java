package cs555.dht.node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;

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
import cs555.search.common.Query;
import cs555.search.common.Search;
import cs555.search.common.SeedSet;
import cs555.search.common.WaitForObject;
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

	WordSet intermediarySet;
	
	WordSet searchWords;

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

		//searchWords = new WordSet();
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
	// Enter DHT
	//================================================================================
	public void enterDHT(String dHost, int dPort) {
		// Try reading search words from disk
		readWordResultsFromDisk();
		if (searchWords != null) {
			System.out.println("Read words from disk: " + searchWords);
		}
		
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
			Link accessLink = connect(poc);
			accessLink.sendData(lookupReq.marshall());
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

		// Try reading intermediary set from file
		readIntermediaryFromDisk();
		if (intermediarySet != null) {
			System.out.println("I am a seeder");
			System.out.println("Seeding : " + intermediarySet);
		}
		
	}

	//================================================================================
	// Seeding Methods
	//================================================================================
	public void seedDHT() {
		if (intermediarySet == null) {
			return;
		}
		
//		for (Word w : intermediarySet.words) {
//			w.hash = Tools.generateHash(w.word);
//			System.out.println("Size : " + w.searchSet.size());
//		}
		
		System.out.println("Seeding : " + intermediarySet);
		SeedSet seeds = new SeedSet(intermediarySet);
		handleSeeds(seeds);
	}
	
	public void saveWords() {
		if (searchWords == null) {
			return;
		}
		
		// Sorting
		System.out.println("Sorting...");
		for (Word w : searchWords.words) {
			Collections.sort(w.searchSet);
		}
		
		// Save
		System.out.println("Saving...");
		saveWordResultsToDisk();
	}
	
	public void handleWord(Word word, ArrayList<Peer> peers) throws IOException {
		
		WaitForObject wait = new WaitForObject();
		
		if (state.itemIsMine(word.hash)) {
			searchWords.addWord(word);
		}
		
		else {
			Peer next = state.getNexClosestPeer(word.hash);
			
			// Find the peer in the links we've already connected to
			for (Peer p : peers) {
				if (p.equals(next)) {
					p.sendData(Tools.objectToBytes(wait));
					p.sendData(Tools.objectToBytes(word));
				}
			}
		}
		
	}
	
	public void readIntermediaryFromDisk() {
		File folder = new File(Constants.base_path);

		for (File fileEntry : folder.listFiles()) {
			if (fileEntry.exists() && fileEntry.isFile()) {
				String fileString = fileEntry.getName();

				System.out.println("file String : " + fileString);

				if (fileString.endsWith(".intermediary")) {


					// Read an object
					Object obj;
					try {
						// Read from disk using FileInputStream
						FileInputStream f_in = new FileInputStream(fileEntry.getAbsolutePath());

						// Read object using ObjectInputStream
						ObjectInputStream obj_in = new ObjectInputStream (f_in);

						obj = obj_in.readObject();

						if (obj instanceof WordSet) {
							// Cast object to a State
							intermediarySet = (WordSet) obj;
							System.out.println("Read Intermediary : " + intermediarySet);

							break;
						}

						else {
							System.out.println("State could not be read from file");
						}

						obj_in.close();

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}

	public void saveIntermediaryToDisk()  {

		// Write to disk with FileOutputStream
		FileOutputStream f_out;
		try {
			f_out = new FileOutputStream(Constants.base_path + Tools.getLocalHostname() + ".intermediary");
			// Write object with ObjectOutputStream
			ObjectOutputStream obj_out = new ObjectOutputStream (f_out);

			// Write object out to disk
			obj_out.writeObject (intermediarySet);

			obj_out.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	
	public void readWordResultsFromDisk() {
		File folder = new File(Constants.base_path);

		for (File fileEntry : folder.listFiles()) {
			if (fileEntry.exists() && fileEntry.isFile()) {
				String fileString = fileEntry.getName();

				System.out.println("file String : " + fileString);

				
				if (fileString.endsWith(".words")) {

					String idString = fileString.replace(".words", "");
					idString = idString.replace(Tools.getLocalHostname()+"-", "");
					id = Integer.parseInt(idString);
					
					System.out.println("ID:  : " + idString);
					
					// Read an object
					Object obj;
					try {
						// Read from disk using FileInputStream
						FileInputStream f_in = new FileInputStream(fileEntry.getAbsolutePath());

						// Read object using ObjectInputStream
						ObjectInputStream obj_in = new ObjectInputStream (f_in);

						obj = obj_in.readObject();

						if (obj instanceof WordSet) {
							// Cast object to a State
							searchWords = (WordSet) obj;
							//System.out.println("Read Words : " + intermediarySet);

							break;
						}

						else {
							System.out.println("Words could not be read from file");
						}

						obj_in.close();

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}

	public void saveWordResultsToDisk()  {

		// Write to disk with FileOutputStream
		FileOutputStream f_out;
		try {
			f_out = new FileOutputStream(Constants.base_path + Tools.getLocalHostname() + "-" + id + ".words");
			// Write object with ObjectOutputStream
			ObjectOutputStream obj_out = new ObjectOutputStream (f_out);

			// Write object out to disk
			obj_out.writeObject (searchWords);

			obj_out.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		Link successorLink = connect(state.successor);
		successorLink.sendData(predLeaving.marshall());

		// Tell our predessor we're leaving
		SuccessorLeaving sucLeaving = new SuccessorLeaving(state.successor.hostname, state.successor.port, state.successor.id);
		Link predLink = connect(state.predecessor);
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
		
		if (state != null) {
			state.update();
		}
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
		Link lookupPeer = connect(p);
		lookupPeer.sendData(l.marshall());
	}

	public void sendPredessessorRequest(Peer p, PredessesorRequest r) {
		Link sucessorLink = connect(p);
		sucessorLink.initLink();
		sucessorLink.sendData(r.marshall());
	}

	public void handleSeeds(SeedSet set) {
		// If we got our own seed set, return
		if (set.hash == id) {
			System.out.println("Seeding complete");
			return;
		}
		
		if (set.hash == -1) {
			set.hash = id;
		}
		
		if (searchWords == null) {
			searchWords = new WordSet();
		}
		
		int i=0;
		// Go through each word, and add the ones we need
		for (Word w : set.wordSet.words) {
			
			if (w.word.equalsIgnoreCase("colorado")) {
				System.out.println("word : " + w.word + " hash: " + w.hash );
			}
			
			if (state.itemIsMine(w.hash)) {
				
				if (w.word.equalsIgnoreCase("colorado")) {
					System.out.println("Colorado is mine: " + id);
				}
				
				searchWords.addWord(w);
				//System.out.println("Search Size for word : " + w.word + " : " + w.searchSet.size());
				
				i++;
				
				
			}
		}
		
		System.out.println("Added Words from seed set : " + i);
		
		// Forward to our successor
		Link successorLink = connect(state.successor);
		
		System.out.println("Sending wait for object");
		WaitForObject wait = new WaitForObject();
		successorLink.sendData(Tools.objectToBytes(wait));
		Tools.sleep(2);
		Tools.writeObject(successorLink, set);
		System.out.println("Sent set to " + successorLink.remoteHost);
		successorLink.close();
	}
	
	//================================================================================
	// Transfer data
	//================================================================================
	public void transferData(DataItem d, Peer p) {

		Link link = connect(p);

		if (link == null){
			link = connect(state.getNextSuccessor());

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
	// Query
	//================================================================================
	public void searchDHT(String q) {
		String[] queryParts = q.split(" ");
		ArrayList<Word> results = new ArrayList<Word>();
		
		// Make query
		for (String s : queryParts) {
			System.out.println("Searching for word : " + s);
			Query query = new Query(s.trim(), Tools.generateHash(s));
			Word item = handleQuery(query, null);
			results.add(item);
			//System.out.println("one word down : " + item);
		}
		
//		// Print 
//		for (Word w : results) {
//			//System.out.println("Got result : " + w);
//		}
		
		printQueryResults(results, q);

	}
	
	public void printQueryResults(ArrayList<Word> results, String q) {
		Word first = results.get(0);
		
		for (int i=1; i<results.size(); i++) {
			ArrayList<Search> searches = intersection(first, results.get(i));
			first.searchSet = searches;
		}
		
		System.out.println("test : " + first);
		
		System.out.println("\n================================================================================");
		System.out.println("Results for query : " + q);
		
		int i=25;
		for (Search s : first.searchSet) {
			
			if (i>=25) {
				break;
			}
			
			System.out.println(i + " : " + s);
			i++;
		}
		
		System.out.println("================================================================================\n");	
	}
	
	public Word handleQuery(Query q, Link previous) {
		
		Word word = null; 
		
		// If the query belongs to me, reply with the word
		if (state.itemIsMine(q.queryHash)) {
			System.out.println("Word is mine: " + q.queryWord);
			word = getQueryWord(q.queryWord);
			
			if (word != null) {
				
				if (previous == null) {
					return word;
				}
				
				System.out.println("Sending word back");
				Tools.writeObject(previous, word);
			}
			
			else {
				System.out.println(q.queryWord + " not found. Send back blank");
				Word blank = new Word("-_-Blank-_-");
				Tools.writeObject(previous, blank);
			}
			
		}
		
		// Else, Pass it along
		else {
			Link next = connect(state.getNexClosestPeer(q.queryHash));
			System.out.println("Passing " + q.queryWord + " along to : " + next.remoteHost);
			
			next.sendData(Tools.objectToBytes(q));
			// Wait for word from next
			Object obj = Tools.readObject(next);
			
			if (obj instanceof Word) {
				word = (Word) obj;
				
				if (previous == null) {
					return word;
				}
			}
			
			Tools.writeObject(previous, obj);
			
			// Close links
			next.close();
			previous.close();
			
		}
		
		return word;
	}
	
	public Word getQueryWord(String word) {
		for  (Word w : searchWords.words) {
			if (w.word.equalsIgnoreCase(word)) {
				return w;
			}
		}
		
		return null;
	}
	
	public boolean wordHasSearch(Word word, Search s) {
		for (Search search : word.searchSet) {
			if (search.pageUrl.equalsIgnoreCase(s.pageUrl)) {
				return true;
			}
		}
		
		return false;
	}
	
	public ArrayList<Search> intersection(Word one, Word two) {
		ArrayList<Search> intersection = new ArrayList<Search>();
		
		System.out.println("ONE's search size : " + one.searchSet.size());
		System.out.println("TWO's search size : " + two.searchSet.size());
		
		for (Search search : one.searchSet) {
			if (wordHasSearch(two, search)) {
				intersection.add(search);
			}
		}
		
		return intersection;
	}
	//================================================================================
	// Receive
	//================================================================================
	// Receieve data
	public synchronized void receive(byte[] bytes, Link l){

		// Word Seeding Messages
		Object obj = Tools.bytesToObject(bytes);

		if (obj != null && obj instanceof WaitForObject) {
			System.out.println("Waiting for object");
			Object data = Tools.readObject(l);

			if (data instanceof WordSet) {
				intermediarySet = (WordSet) data;

				System.out.println("Got a wordie birdi set: " + intermediarySet);
				System.out.println("Test Word : " + intermediarySet.words.get(199));
				
//				for (Word w : intermediarySet.words) {
//					System.out.println("Incoming : " + w.searchSet.size());
//				}
				
				System.out.println("Saveing to file..."); 
				saveIntermediaryToDisk();
			}
			
			else if (data instanceof SeedSet) {
				SeedSet seeds = (SeedSet) data;
				System.out.println("Got a seed set from " + l.remoteHost);
				System.out.println("Word Set : " + seeds.wordSet);
				l.close();
				handleSeeds(seeds);
			}
			
			return;
		}
		
		else if (obj instanceof Query) {
			Query query = (Query) obj;
			System.out.println("Got query word :" + query.queryWord + ": hash: " + query.queryHash);
			handleQuery(query, l);
			
			return;
		}

		// DHT Messages
		int messageType = Tools.getMessageType(bytes);

		switch (messageType) {
		case Constants.lookup_request:

			LookupRequest lookup = new LookupRequest();
			lookup.unmarshall(bytes);

			// Info about the lookup
			int resolveID = lookup.resolveID;
			String requesterHost = lookup.hostName;
			int requesterPort = lookup.port;
			int requesterID = lookup.id;
			int entry = lookup.ftEntry;

			// If we are the target, handle it
			if (state.itemIsMine(resolveID)) {

				LookupResponse response = new LookupResponse(hostname, port, id, resolveID, entry);
				Peer requester = new Peer(requesterHost, requesterPort, requesterID);
				Link requesterLink = connect(requester);

				requesterLink.sendData(response.marshall());
			}

			// Else, pass it along
			else {

				//System.out.println("is not mine : " + resolveID);
				Peer nextPeer = state.getNexClosestPeer(resolveID);
				Link nextHop = connect(nextPeer);

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

			LookupResponse reply = new LookupResponse();
			reply.unmarshall(bytes);

			// Heard back for FingerTable entry, update state
			state.parseState(reply);

			break;

		case Constants.Predesessor_Request:

			PredessesorRequest predReq = new PredessesorRequest();
			predReq.unmarshall(bytes);

			PredessesorResponse oldPred = new PredessesorResponse(state.predecessor.hostname, state.predecessor.port, state.predecessor.id);
			l.sendData(oldPred.marshall());

			// Add this node as our predessesor
			Peer pred = new Peer(predReq.hostName, predReq.port, predReq.id);
			state.addPredecessor(pred,false);

			break;

		case Constants.Predesessor_Response:

			PredessesorResponse predResp = new PredessesorResponse();
			predResp.unmarshall(bytes);

			Peer p = new Peer(predResp.hostName, predResp.port, predResp.id);
			state.addPredecessor(p, false);

			if (state.successor.id != state.predecessor.id) {
				SuccessorRequest sucReq = new SuccessorRequest(hostname, port, id);
				Link successorLink = connect(p);
				successorLink.sendData(sucReq.marshall());
			}

			break;

		case Constants.Successor_Request:

			SuccessorRequest sReq = new SuccessorRequest();
			sReq.unmarshall(bytes);

			Peer sucessor = new Peer(sReq.hostName, sReq.port, sReq.id);
			state.addSucessor(sucessor, false);

			break;

		case Constants.Predessesor_Leaving:
			PredessesorLeaving predLeaving = new PredessesorLeaving();
			predLeaving.unmarshall(bytes);

			Peer newPred = new Peer(predLeaving.hostName, predLeaving.port, predLeaving.id);
			state.addPredecessor(newPred,true);

			break;

		case Constants.Successor_Leaving:
			SuccessorLeaving sucLeaving = new SuccessorLeaving();
			sucLeaving.unmarshall(bytes);

			Peer newSuc = new Peer(sucLeaving.hostName, sucLeaving.port, sucLeaving.id);
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
			System.out.println("Unrecognized Message : " + messageType);
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
		int refreshTime = 30;

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
			
			if (input.equalsIgnoreCase("seed")) {
				peer.seedDHT();
			}
			
			else if (input.equalsIgnoreCase("save")) {
				peer.saveWords();
			}
			
			else {
				peer.searchDHT(input);
			}
		}
	}
}