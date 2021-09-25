import java.io.*;
import java.util.*;
import java.net.*;

//Main client class
public class Client{

    //Global variables to store the username, current message, both sockets and standard input stream
    public static String username = "";
    public static String currMessage = "";
    private clientSendThread sendSock = null;
    private clientRecvThread rcvSock = null;
    public static Scanner stdIn = new Scanner(System.in);
    
    //Global function which takes in a response from the server and converts into a response code depending upon the type of response
    //
    //Three digit response code corresponds to an error code according to the protocol.
    //
    //One digit response code mappings are:
    //
    //1: Send socket registered successfully
    //2: Receive socket registered successfully
    //3: Message Sent successfully
    //4: Forwarded message received, data needs to be extracted 
    //
    //Code -1: Response doesn't follow the protocol i.e. the response is either random text or has arrived from a server using different protocol
    public static int parseResponse(String response){
        //Default Error Code to be thrown in case of non-protocol message
        int responseCode = 103;
        response = response.trim();

        //Converting response message to individual elements
        String[] header = response.split("\n\n");
        
        //Checking if the response message follows the underlying protocol and if it does, then assign proper response codes for identification.
        if(header.length==1){
            //Means the string is not a message, as it has only header with no body

            //Obtaining keywords from header
            String[] keywords = header[0].split(" ",3);
            if(keywords.length==2){
                if(keywords[0].equals("SENT")){
                    //Acknowledgement from the server that the message was delivered successfully
                    if(username.equals(keywords[1])){
                        responseCode = 3;
                    }else{
                        //In case the acknowledgement is meant for some other username
                        responseCode = 31;
                    }
                }
            }else if(keywords.length==3){
                //Well defined errors defined by the protocol
                if(keywords[0].equals("ERROR")){
                    try{
                        //Error code followed by the meaning
                        int errorCode = Integer.parseInt(keywords[1]);
                        switch(errorCode){
                            case 100: {if(keywords[2].equals("Malformed username")){responseCode=errorCode;}break;}
                            case 101: {if(keywords[2].equals("No user registered")){responseCode=errorCode;}break;}
                            case 102: {if(keywords[2].equals("Unable to send")){responseCode=errorCode;}break;}
                            case 103: {if(keywords[2].equals("Header incomplete")){responseCode=errorCode;}break;}
                        }
                    }catch(Exception e){
                        System.out.println("Unresolved error. Corrupt server.");
                        System.exit(0);
                    }
                }else if(keywords[0].equals("REGISTERED")){
                    //Acknowledgement of registration
                    if(keywords[1].equals("TOSEND")){
                        //For send thread
                        if(username.equals(keywords[2])){
                            responseCode = 1;
                        }else{
                            responseCode = 11;
                        }
                    }else if(keywords[1].equals("TORECV")){
                        //For receive thread
                        if(username.equals(keywords[2])){
                            responseCode = 2;
                        }else{
                            responseCode = 21;
                        }
                    }
                }
            }
        }else if(header.length==2){
            String[] keywords = header[0].split("\n");
            if(keywords.length==2){
                String[] splitString = keywords[0].split(" ");
                if(splitString.length==2){
                    if(splitString[0].equals("FORWARD")){
                        //If the incoming message is a FORWARDED message
                        String[] splitString2 = keywords[1].split(" ");
                        if(splitString2.length==2){
                            if(splitString2[0].equals("Content-length:")){
                                try{
                                    int msgLen = Integer.parseInt(splitString2[1]);
                                    currMessage = header[1].trim();
                                    if(msgLen==currMessage.length()){
                                        //If the message is of the correct length
                                        responseCode = 4;
                                    }else{
                                        currMessage="";
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

    //Managing the registration of sockets before sending messages. Client terminates in case of an invalid username.
    private void manageRegistration(){
        //Registration for sending thread
        int responseCode = this.sendSock.register();
        if(responseCode==1){
            System.out.println("Sending socket registered!");
        }else{
            if(responseCode==100){
                System.out.println("ERROR 100: Invalid Username.");
            }else{
                System.out.println("ERROR 103: Corrupt registration request. Exiting...");
            }
            System.exit(0);
        }
        //Registration for receiving thread
        responseCode = this.rcvSock.register();
        if(responseCode==2){
            System.out.println("Receiving socket registered!");
        }else{
            if(responseCode==100){
                System.out.println("ERROR 100: Invalid Username.");
                username = stdIn.nextLine();
            }else{
                System.out.println("ERROR 103: Corrupt registration request. Exiting...");
            }
            System.exit(0);
        }
    }

    //For starting the client process
    public void startClient(){
        //Registration of the user
        System.out.println("Registering user "+username+" to the server....");
        this.manageRegistration();

        //If user is registered succesfully, then spawn separate threads for send and receive sockets
        System.out.println("Starting client threads...");
        sendSock.start();
        rcvSock.start();
    }

    //Constructor for creating TCP connections with the server
    public Client(String userName, String serverIP, int serverPort){
        username = userName;
        this.sendSock = new clientSendThread(serverIP,serverPort);
        this.rcvSock = new clientRecvThread(serverIP,serverPort);
    }

    public static void main(String[] args){
        String userName;

        //Default Server IP and port
        String serverIP = "127.0.0.1";
        int serverPort = 1234;

        if(args.length==3){
            //User provided server IP and port
            userName = args[0];
            try{
                serverIP = args[1];
                serverPort = Integer.parseInt(args[2]);
            }catch(Exception e){
                System.out.println("Invalid arguments.");
                System.exit(1);
            }
            Client msgClient = new Client(userName, serverIP, serverPort);
            msgClient.startClient();
        }else if(args.length==1){
            userName = args[0];
            Client msgClient = new Client(userName, serverIP, serverPort);
            msgClient.startClient();
        }else{
            System.out.println("Provide proper number of arguments.");
        }

    }
}

class clientSendThread extends Thread{
    private Socket clientSendSock = null;
    private DataInputStream sockIn = null;
    private DataOutputStream sockOut = null;

    //Send Thread constructor
    public clientSendThread(String serverIP,int serverPort){
        try{
            this.clientSendSock = new Socket(serverIP,serverPort);
            this.sockIn = new DataInputStream(clientSendSock.getInputStream());
            this.sockOut = new DataOutputStream(clientSendSock.getOutputStream());
        }catch(ConnectException e){
            System.out.println("No server created");
            System.exit(0);
        }catch(Exception e){
            System.out.println("Unresolved Error");
        }
    }

    //Separate register function for sender thread
    public int register(){
        //Protocol message for client send thread registration
        String sendRequest = "REGISTER TOSEND "+Client.username+"\n\n";
        int responseCode = 103;

        try{
            sockOut.writeUTF(sendRequest);
            String sendResponse = sockIn.readUTF();
            responseCode = Client.parseResponse(sendResponse);
        }catch(Exception e){
            System.out.println("Error in send socket registration: "+e);
        }
        return responseCode;
    }

    @Override
    public void run(){
        System.out.println("Sending Thread started.");

        //Waiting for the creation of both the threads of the client
        synchronized(this){
            try{
                this.wait(1000);
            }catch(Exception e){
                System.out.println(e);
            }
        }

        //Reading messages 
        System.out.println();
        System.out.println();
        System.out.println("Welcome to the chat room, "+Client.username);
        System.out.println("You can send messages in the format: @[recipient username] message");
        System.out.println();
        System.out.println();

        String sendMessage = "";

        while(true){
            
            try{
                String inputString = Client.stdIn.nextLine();

                //Checking correct format of input message i.e. every message should start with @recipient
                if(inputString.charAt(0)=='@'){

                    //Extracting message and recipient to parse into a protocol message 
                    inputString = inputString.substring(1);
                    String[] splitString = inputString.split(" ",2);
                    String msg = splitString[1];

                    //Conversion of input into SEND protocol message
                    sendMessage = "SEND "+splitString[0]+"\nContent-length: "+msg.length()+"\n\n"+msg;
                    sockOut.writeUTF(sendMessage);

                    //Receiving acknowledgement from server regarding the status of sent message
                    String sendResponse = sockIn.readUTF();
                    int responseCode = Client.parseResponse(sendResponse);
                    if(responseCode!=3){
                        //Message not sent due to some reasons
                        if(responseCode==102){
                            //Recipient not registered with the server
                            System.out.println("ERROR 102. Recipient doesn't exist. Try again.");
                        }else if(responseCode==103){
                            //Incorrect header format for sending message
                            System.out.println("ERROR 103. This client violates protocol. Exiting...");
                            System.exit(0);
                        }else if(responseCode==101){
                            //Sending message without registering first. Exits immediately
                            System.out.println("ERROR 101. Please complete registration first.");
                            System.exit(0);
                        }
                    }else{
                        //Acknowledgement of message received
                        System.out.println("Message sent successfully.");
                    }
                }else{
                    System.out.println("Use correct format of message and try again.");
                }
                System.out.println();
            }catch(EOFException e){
                System.out.println("Server disconnected.");
                System.exit(0);
            }catch(Exception e){
                System.out.println("Error in sending messages "+e);
                break;
            }

        }

    }

}

class clientRecvThread extends Thread{
    private Socket clientRecvSock = null;
    private DataInputStream sockIn = null;
    private DataOutputStream sockOut = null;

    //Receive Thread Constructor
    public clientRecvThread(String serverIP,int serverPort){
        try{
            this.clientRecvSock = new Socket(serverIP,serverPort);
            this.sockIn = new DataInputStream(clientRecvSock.getInputStream());
            this.sockOut = new DataOutputStream(clientRecvSock.getOutputStream());
        }catch(Exception e){
            System.out.println("Error in forming receive thread: "+e);
        }
    }

    public int register(){
        //Protocol message for receive thread registration
        String rcvRequest = "REGISTER TORECV "+Client.username+"\n\n";
        int responseCode = 103;
        try{
            sockOut.writeUTF(rcvRequest);
            String rcvResponse = sockIn.readUTF();
            responseCode = Client.parseResponse(rcvResponse);
        }catch(Exception e){
            System.out.println("Error in receive thread registration: "+e);
        }
        return responseCode;
    }

    private String extractSender(String msg){
        return ((msg.split("\n\n")[0]).split("\n")[0]).split(" ")[1].trim();
    }

    @Override
    public void run(){
        System.out.println("Receiving Thread started.");
        String rcvMessage = "";
        while(true){
            try{
                //Receiving message from server
                Client.currMessage = "";
                rcvMessage = sockIn.readUTF();
                int responseCode = Client.parseResponse(rcvMessage);
                String rcvResponse;
                if(responseCode==4){
                    //Proper format of forwarded message
                    String sender = extractSender(rcvMessage);
                    System.out.println(sender+": "+Client.currMessage);
                    System.out.println();

                    //Constructing the acknowledgement message
                    rcvResponse = "RECEIVED "+Client.username+"\n\n";
                }else{
                    //Improper message format from server.
                    rcvResponse = "ERROR 103 Header Incomplete\n\n";
                }

                //Sending error or acknowledgement
                sockOut.writeUTF(rcvResponse);
            }catch(EOFException e){
                System.out.println("Server disconnected.");
                System.exit(0);
            }catch(Exception e){
                System.out.println("Error in receiving messages "+e);
                break;
            }
        }
    }
}