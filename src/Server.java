import java.io.*;
import java.util.*;
import java.net.*;

//Main server class
public class Server {
    private ServerSocket serverSocket = null;
    public static HashMap<String,SendSocket> sendSockets = new HashMap<>();
    public static HashMap<String,RecvSocket> rcvSockets = new HashMap<>();

    //Global function which takes in a response from the server and converts into a response code depending upon the type of response
    //
    //Three digit response code corresponds to an error code according to the protocol.
    //
    //One digit response code mappings are:
    //
    //1: Send socket registered successfully
    //2: Receive socket registered successfully
    //3: Message Received successfully
    //4: Send message received, data needs to be extracted 
    //
    //Code 103: Response doesn't follow the protocol i.e. the response is either random text or has arrived from a server using different protocol
    public static int parseResponse(String response,String userName){
        //Default error code, any string which doesnt follow any of the protocol messages will be replied with ERROR 103
        int responseCode = 103;
        response = response.trim();

        //Splitting the string into header and body(content)
        String[] header = response.split("\n\n");

        if(header.length==1){
            //Means this is a non-message string, since it only has a header and no body
            //Obtaining keywords from the header
            String[] keywords = header[0].split(" ",3);
            if(keywords.length==2){
                //Acknowledgement from the recipient that the message was received
                if(keywords[0].equals("RECEIVED")){
                    //To check whether the acknowledgement came from the intended user
                    if(userName.equals(keywords[1])){
                        responseCode = 3;
                    }else{
                        responseCode = 31;
                    }
                }
            }else if(keywords.length==3){
                //Well defined errors defined by the protocol
                if(keywords[0].equals("ERROR")){
                    try{
                        //Error code followed by the meaning
                        int errorCode = Integer.parseInt(keywords[1]);
                        if(errorCode==103){
                            if(keywords[2].equals("Header incomplete")){
                                responseCode = 103;
                            }
                        }
                    }catch(Exception e){
                        System.out.println("Error code is not a number. The client will be ignored.");
                    }
                }else if(keywords[0].equals("REGISTER")){
                    //Registration request message
                    if(keywords[1].equals("TOSEND")){
                        //For client send thread
                        responseCode = 1;
                    }else if(keywords[1].equals("TORECV")){
                        //For client receive thread
                        responseCode = 2;
                    }
                }
            }
        }else if(header.length==2){
            String[] keywords = header[0].split("\n");
            if(keywords.length==2){
                String[] splitString = keywords[0].split(" ");
                if(splitString.length==2){
                    if(splitString[0].equals("SEND")){
                        //If the incoming message is a SEND request
                        String[] splitString2 = keywords[1].split(" ");
                        if(splitString2.length==2){
                            if(splitString2[0].equals("Content-length:")){
                                try{
                                    int msgLen = Integer.parseInt(splitString2[1]);
                                    String currMessage = header[1].trim();
                                    if(msgLen==currMessage.length()){
                                        //Valid if only the content length and the actual length are same
                                        responseCode = 4;
                                    }
                                }catch(Exception e){
                                    System.out.println("Message Length should be an integer.");
                                }
                            }
                        }
                    }
                }
            }
        }
        return responseCode;
    }

    //To check validity of username, [0-9a-zA-z]+ allowed
    private boolean isValid(String uname){
        for(int i=0;i<uname.length();i++){
            int idx = (int)uname.charAt(i);
            if((idx>=97 && idx<=122) || (idx>=65 && idx<=90) || (idx>=48 && idx<=57)){
                continue;
            }else{
                return false;
            }
        }
        return (uname.length()>0);
    }

    //Server socket listening for registration requests of new clients
    public void serverStart(){
        while(true){
            try{
                //New client detected
                Socket clientSocket = serverSocket.accept();
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                //Managing Registration of incoming requests
                String request = dis.readUTF();
                int responseCode = parseResponse(request, "");
                String response = "";
                if(responseCode==1){
                    //Registration of client send thread is successful
                    String newUser = (request.split("\n\n")[0]).split(" ",3)[2];
                    if(isValid(newUser)){
                        //Sending acknowledgement
                        response = "REGISTERED TOSEND "+newUser+"\n\n";

                        //The incoming send socket stored as a Thread object, since it will continuously look for messages from this client  
                        SendSocket newClient = new SendSocket(newUser,clientSocket,dis,dos);
                        sendSockets.put(newUser, newClient);

                        //The send thread is started immediately invoked to start listening for messages from the client
                        newClient.start();
                    }else{
                        //Invalid username
                        response = "ERROR 100 Malformed username\n\n";
                    }
                }else if(responseCode==2){
                    //Registration of client receive thread is successful
                    String newUser = (request.split("\n\n")[0]).split(" ",3)[2];
                    if(isValid(newUser)){
                        //Adding new user to the global hash table if username is valid
                        //Sending acknowledgement
                        response = "REGISTERED TORECV "+newUser+"\n\n";

                        //The incoming receive socket will be stored as a Thread object also
                        RecvSocket newClient = new RecvSocket(newUser,clientSocket,dis,dos);
                        rcvSockets.put(newUser, newClient);

                        //The receive thread is invoked only inside the send thread, only when a message is destined to the corresponding recipient
                    }else{
                        //Invalid username
                        response = "ERROR 100 Malformed username\n\n";
                    }
                }else{
                    //To be raised when a client sends message before registration
                    response = "ERROR 101 No user registered\n\n";
                }
                dos.writeUTF(response);
            }catch(EOFException e){
                System.out.println("Client disconnected due to incomplete registration.");
            }catch(Exception e){
                //In case of an error outside of the domain of our protocol
                System.out.println("Unresolved error in handling registration.");
            }

        }
    }

    //Constructor for server
    public Server(int port) {
        
        try{
            serverSocket = new ServerSocket(port);
        }catch(Exception e){
            System.out.println("Error in initialising server socket: "+e);
        }

    }

    public static void main(String[] args){

        //Initiating chat server
        int PORT_NO = Integer.valueOf(args[0]);
        Server msgServer = new Server(PORT_NO);
        msgServer.serverStart();

    }
}

class SendSocket extends Thread{
    public String username;
    private Socket sendSocket = null;
    private DataInputStream sockIn = null;
    private DataOutputStream sockOut = null;

    //Constructor for server side client send socket/thread
    public SendSocket(String userName, Socket clientSocket, DataInputStream dis, DataOutputStream dos){
        //Initialising username, socket and I/O streams for this client
        try{
            this.username = userName;
            this.sendSocket = clientSocket;
            this.sockIn = dis;
            this.sockOut = dos;
        }catch(Exception e){
            System.out.println("Error in initialising client sending thread:"+e);
        }

    }

    //Extract recipient of a message from a "SEND" message
    public String extractRecipient(String msg){
        return ((msg.split("\n\n")[0]).split("\n")[0]).split(" ")[1].trim();        
    }

    @Override
    public void run(){  
        
        //Waiting for the receive thread to initialise
        synchronized(this){
            try{
                this.wait(1000);
            }catch(Exception e){
                System.out.println(e);
            }
        }

        System.out.println(username+" joined");

        //Continuous parallel loop for reading messages from a registered client
        while(true){  
            try{
                //Reading message from the client
                String message = sockIn.readUTF();
                int responseCode = Server.parseResponse(message, username);
                String response = "";

                if(responseCode==4){
                    //If the message is a send request
                    String recipient = extractRecipient(message);

                    //Constructing a protocol message for forwarding it to a recipient
                    int len = (message.split("\n\n")[0]).split("\n")[0].length();
                    message = message.substring(len);
                    message = "FORWARD "+this.username+message;

                    if(recipient.equals("ALL")){
                        //Broadcast
                        responseCode = 3;

                        //Simultaneously starting all recipient sub threads for maximum parallelism while broadcasting
                        for(RecvSocket rcvskt: Server.rcvSockets.values()){
                            rcvskt.currMessage = message;
                            rcvskt.start();
                        }

                        //Simultaneously joining all sub threads to ensure sequential execution
                        for(RecvSocket rcvskt: Server.rcvSockets.values()){
                            rcvskt.join();
                            response = rcvskt.currMessage;
                            int subResponseCode = Server.parseResponse(response, rcvskt.username);
                            if(subResponseCode != 3){
                                responseCode = -1;
                            }
                            Server.rcvSockets.put(rcvskt.username, new RecvSocket(rcvskt.username,rcvskt.recvSocket,rcvskt.sockIn,rcvskt.sockOut));
                        }

                        //In case of a multiple recipients, send acknowledgement only if all recipients have received messages
                        if(responseCode == 3){
                            response = "SENT "+username+"\n\n";
                        }else{
                            response = "ERROR 102 Unable to send\n\n";
                        }
                        sockOut.writeUTF(response);
                    
                    }else{
                        //Unicast

                        if(Server.rcvSockets.containsKey(recipient)){
                            //If the recipient is a registered user

                            //Invoke recipient thread
                            RecvSocket rcvskt = Server.rcvSockets.get(recipient);
                            rcvskt.currMessage = message;
                            rcvskt.start();
                            rcvskt.join();
                            response = Server.rcvSockets.get(recipient).currMessage;

                            //Fetching the recipient response
                            responseCode = Server.parseResponse(response, recipient);

                            //In case of a single recipient, responses are sent immediately
                            if(responseCode == 3){
                                response = "SENT "+username+"\n\n";
                            }else{
                                response = "ERROR 102 Unable to send\n\n";
                            }
                            sockOut.writeUTF(response);

                            //Initialising a new thread for the recipient
                            Server.rcvSockets.put(recipient, new RecvSocket(recipient,rcvskt.recvSocket,rcvskt.sockIn,rcvskt.sockOut));
                        }else{
                            //If recipient is unregistered
                            response = "ERROR 102 Unable to send\n\n";
                            sockOut.writeUTF(response);
                        }
                    }
                }else{
                    response = "ERROR 103 Header Incomplete\n\n";
                    sockOut.writeUTF(response);
                }
            }catch(EOFException e){
                //The client is deregistered as soon as it disconnects
                System.out.println(this.username+" left.");
                Server.rcvSockets.remove(this.username);
                Server.sendSockets.remove(this.username);
                return;
            }catch(Exception e){
                System.out.println("Error in client thread "+e);
                break;
            }
        }
    }
}

class RecvSocket extends Thread{
    public String username;
    public String currMessage = ""; 
    public Socket recvSocket = null;
    public DataInputStream sockIn = null;
    public DataOutputStream sockOut = null;

    public RecvSocket(String userName, Socket recvSocket, DataInputStream dis, DataOutputStream dos){
        try{
            this.username = userName;
            this.recvSocket = recvSocket;
            this.sockIn = dis;
            this.sockOut = dos;
        }catch(Exception e){
            System.out.println("Error in initialising client receive socket"+e);
        }
    }

    @Override
    public void run(){
        try{
            //Communicating with the recipient during message forwarding
            String message = currMessage;
            sockOut.writeUTF(message);
            currMessage = sockIn.readUTF();
        }catch(EOFException e){
            //The client is deregistered as soon as it disconnects
            System.out.println(this.username+" left.");
            Server.rcvSockets.remove(this.username);
            Server.sendSockets.remove(this.username);
            return;
        }catch(Exception e){
            System.out.println("Error in client thread "+e);
        }
        return;
    }
}
